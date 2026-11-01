package vn.edufit.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edufit.iam.infra.security.Argon2PasswordService;

/**
 * Backend-only acceptance test for the Discovery -> AI -> Connection hand-off.
 *
 * <p>Only the external Gemini boundary is faked. IAM, HTTP security, JDBC sessions, profile,
 * discovery, connection, notification, audit, quota and logging all use production beans and a
 * real PostgreSQL database.
 */
@SpringBootTest(
    properties = {
      "edufit.ai.provider=gemini",
      "edufit.ai.timeout=200ms",
      "edufit.ai.gemini.api-key=test-only-key",
      "edufit.ai.gemini.model=test-model",
      "spring.session.jdbc.initialize-schema=never"
    })
@Testcontainers
@DirtiesContext
class AiDiscoveryConnectionE2ETest {
  private static final String PASSWORD = "Password123@";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  static final AtomicInteger providerCalls = new AtomicInteger();
  static final ExecutorService providerExecutor = Executors.newVirtualThreadPerTaskExecutor();
  static final HttpServer provider = startProvider();
  static volatile String providerMode = "success";

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add(
        "edufit.ai.gemini.base-url",
        () -> "http://127.0.0.1:" + provider.getAddress().getPort());
  }

  @Autowired WebApplicationContext context;
  @Autowired JdbcTemplate jdbc;
  @Autowired Argon2PasswordService encoder;

  MockMvc mvc;
  UUID studentUserId;
  UUID studentId;
  UUID parentUserId;
  UUID tutorUserId;
  UUID tutorId;

  @BeforeEach
  void setup() {
    mvc =
        MockMvcBuilders.webAppContextSetup(context)
            .addFilters(
                context.getBean("springSessionRepositoryFilter", Filter.class),
                context.getBean("springSecurityFilterChain", Filter.class))
            .build();
    jdbc.execute("truncate account, subject, education_level cascade");
    jdbc.execute("delete from spring_session");
    providerCalls.set(0);
    providerMode = "success";

    studentUserId = UUID.randomUUID();
    studentId = UUID.randomUUID();
    parentUserId = UUID.randomUUID();
    tutorUserId = UUID.randomUUID();
    tutorId = UUID.randomUUID();
    seedAccount(studentUserId, "student@example.test", "Student", "STUDENT");
    seedAccount(parentUserId, "parent@example.test", "Parent", "PARENT");
    seedAccount(tutorUserId, "tutor@example.test", "Tutor", "TUTOR");
    jdbc.update("insert into education_level(level_id,name,sort_order) values(2,'Grade 7',2)");
    jdbc.update("insert into education_level(level_id,name,sort_order) values(3,'Grade 8',3)");
    jdbc.update("insert into subject(subject_id,name) values(1,'Math')");
    jdbc.update(
        "insert into student_profile(student_id,user_id,profile_complete) values(?,?,false)",
        studentId,
        studentUserId);
    seedTutor(2);
  }

  @AfterAll
  static void stopProvider() {
    provider.stop(0);
    providerExecutor.shutdownNow();
  }

  @Test
  void studentCompletesDiscoveryAndConcurrentTutorAcceptanceCreatesOneActiveClass()
      throws Exception {
    ActorSession student = login("student@example.test");
    completeStudentProfile(student);
    UUID goalId = createGoal(student);
    setGoalAvailability(student, goalId);

    mvc.perform(get("/api/v1/discovery/tutors").param("subjectId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].tutorId").value(tutorId.toString()));
    mvc.perform(get("/api/v1/discovery/tutors/{id}", tutorId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.tutorId").value(tutorId.toString()));

    MvcResult match = match(student, goalId).andExpect(status().isOk()).andReturn();
    JsonNode matchJson = json(match);
    assertThat(matchJson.at("/data/0/tutorId").asText()).isEqualTo(tutorId.toString());
    assertThat(matchJson.at("/data/0/aiGenerated").asBoolean()).isTrue();
    assertThat(matchJson.at("/data/0/explanation").asText()).isEqualTo("Grounded E2E answer");
    assertThat(providerCalls).hasValue(1);

    UUID requestId = createConnectionRequest(student, goalId, tutorId);
    ActorSession tutor = login("tutor@example.test");
    mvc.perform(get("/api/v1/connections/requests").cookie(tutor.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].requestId").value(requestId.toString()))
        .andExpect(jsonPath("$.data[0].senderRole").value("STUDENT"));

    ExecutorService callers = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> statuses =
          List.of(
              callers.submit(() -> accept(tutor, requestId).getResponse().getStatus()),
              callers.submit(() -> accept(tutor, requestId).getResponse().getStatus()));
      assertThat(statuses.stream().map(this::futureStatus).toList())
          .containsExactlyInAnyOrder(200, 409);
    } finally {
      callers.shutdownNow();
    }

    assertThat(
            jdbc.queryForObject(
                "select count(*) from class where connection_request_id=? and status='ACTIVE'",
                Integer.class,
                requestId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select status from connection_request where request_id=?", String.class, requestId))
        .isEqualTo("ACCEPTED");
    assertSuccessfulMatchingAudit(goalId, studentUserId);
  }

  @Test
  void parentCanUseStudentGoalUntilStudentRevokesTheConfirmedLink() throws Exception {
    ActorSession student = login("student@example.test");
    completeStudentProfile(student);
    UUID goalId = createGoal(student);
    setGoalAvailability(student, goalId);
    ActorSession parent = login("parent@example.test");

    unsafe(
            post("/api/v1/connections/parent-student/invite")
                .content(jsonBody("targetEmail", "student@example.test")),
            parent)
        .andExpect(status().isOk());
    UUID invitationId =
        UUID.fromString(
            json(
                    mvc.perform(
                            get("/api/v1/connections/parent-student/invitations")
                                .cookie(student.session()))
                        .andExpect(status().isOk())
                        .andReturn())
                .at("/data/0/invitationId")
                .asText());
    MvcResult accepted =
        unsafe(
                post("/api/v1/connections/parent-student/respond")
                    .content(
                        "{\"invitationId\":\"" + invitationId + "\",\"accept\":true}"),
                student)
            .andExpect(status().isOk())
            .andReturn();
    UUID linkId = UUID.fromString(json(accepted).at("/data/linkId").asText());

    mvc.perform(
            get("/api/v1/discovery/students/{studentId}/goals", studentId)
                .cookie(parent.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].goalId").value(goalId.toString()));
    match(parent, goalId).andExpect(status().isOk());
    createConnectionRequest(parent, goalId, tutorId);

    unsafe(
            delete("/api/v1/connections/parent-student/links/{id}", linkId)
                .content("{\"reason\":\"Student revoked access\"}"),
            student)
        .andExpect(status().isOk());
    int callsBeforeDeniedActions = providerCalls.get();
    mvc.perform(
            get("/api/v1/discovery/students/{studentId}/goals", studentId)
                .cookie(parent.session()))
        .andExpect(status().isForbidden());
    match(parent, goalId).andExpect(status().isForbidden());
    unsafe(
            post("/api/v1/connections/requests")
                .content(connectionBody(goalId, tutorId)),
            parent)
        .andExpect(status().isForbidden());
    assertThat(providerCalls).hasValue(callsBeforeDeniedActions);
  }

  @ParameterizedTest
  @ValueSource(strings = {"http429", "blocked", "timeout"})
  void providerFailureFallsBackAndConnectionRemainsAvailable(String failure) throws Exception {
    ActorSession student = login("student@example.test");
    completeStudentProfile(student);
    UUID goalId = createGoal(student);
    setGoalAvailability(student, goalId);
    providerMode = failure;

    MvcResult result = match(student, goalId).andExpect(status().isOk()).andReturn();
    assertThat(json(result).at("/data/0/aiGenerated").asBoolean()).isFalse();
    assertThat(json(result).at("/data/0/explanation").asText()).isNotBlank();
    assertThat(providerCalls).hasValue(1);
    createConnectionRequest(student, goalId, tutorId);
    String expected = failure.equals("timeout") ? "TIMEOUT" : "ERROR";
    assertThat(jdbc.queryForList("select status from ai_request_log", String.class))
        .containsExactly(expected);
  }

  @Test
  void emptyCandidateSetSkipsProviderAndStillRecordsMatchingRun() throws Exception {
    jdbc.update("delete from tutor_subject where tutor_id=?", tutorId);
    ActorSession student = login("student@example.test");
    completeStudentProfile(student);
    UUID goalId = createGoal(student);
    setGoalAvailability(student, goalId);

    match(student, goalId).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    assertThat(providerCalls).hasValue(0);
    assertThat(
            jdbc.queryForObject(
                "select result_count from matching_run_log where goal_id=?",
                Integer.class,
                goalId))
        .isZero();
    assertThat(jdbc.queryForObject("select count(*) from ai_request_log", Integer.class)).isZero();
  }

  @Test
  void discoveryCanRankAdjacentLevelButConnectionRequiresExactSubjectAndLevel() throws Exception {
    jdbc.update(
        "update tutor_subject set education_level_id=3 where tutor_id=? and subject_id=1", tutorId);
    ActorSession student = login("student@example.test");
    completeStudentProfile(student);
    UUID goalId = createGoal(student);
    setGoalAvailability(student, goalId);

    match(student, goalId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].tutorId").value(tutorId.toString()));
    unsafe(
            post("/api/v1/connections/requests")
                .content(connectionBody(goalId, tutorId)),
            student)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_OPERATION"));
  }

  private void seedAccount(UUID id, String email, String name, String role) {
    jdbc.update(
        "insert into account(user_id,email,password_hash,full_name,role,status)"
            + " values(?,?,?,?,?,'ACTIVE')",
        id,
        email,
        encoder.hash(PASSWORD),
        name,
        role);
  }

  private void seedTutor(int educationLevelId) {
    jdbc.update(
        "insert into tutor_profile(tutor_id,user_id,display_name,headline,bio,teaching_mode,area,"
            + " price_per_session,experience_years,teaching_method,status,verified_at,rating_avg,review_count)"
            + " values(?,?,?,'Math tutor','Grounded bio','ONLINE','Hanoi',150000,5,'Practice',"
            + " 'VERIFIED',now(),4.90,20)",
        tutorId,
        tutorUserId,
        "Tutor");
    jdbc.update(
        "insert into tutor_subject(tutor_id,subject_id,education_level_id) values(?,1,?)",
        tutorId,
        educationLevelId);
    jdbc.update(
        "insert into tutor_availability_slot(tutor_id,day_of_week,start_time,end_time)"
            + " values(?,1,'18:00','20:00')",
        tutorId);
  }

  private ActorSession login(String email) throws Exception {
    Cookie bootstrap = csrf();
    MvcResult login =
        mvc.perform(
                post("/api/v1/auth/login")
                    .cookie(bootstrap)
                    .header("X-XSRF-TOKEN", bootstrap.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\""
                            + email
                            + "\",\"password\":\""
                            + PASSWORD
                            + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    Cookie session = responseCookie(login, "EDUFIT_SESSION");
    assertNotNull(session);
    return new ActorSession(session, csrf(session));
  }

  private void completeStudentProfile(ActorSession student) throws Exception {
    unsafe(
            put("/api/v1/profiles/students/me")
                .content("{\"educationLevelId\":2,\"area\":\"Hanoi\"}"),
            student)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.educationLevelId").value(2));
  }

  private UUID createGoal(ActorSession student) throws Exception {
    String body =
        "{\"subjectId\":1,\"currentLevel\":1,\"desiredLevel\":2,"
            + "\"goalType\":\"IMPROVE_GRADES\",\"deadline\":\""
            + LocalDate.now().plusMonths(2)
            + "\",\"mode\":\"ONLINE\",\"area\":null,\"budgetMin\":100000,"
            + "\"budgetMax\":200000}";
    MvcResult result =
        unsafe(post("/api/v1/profiles/students/me/goals").content(body), student)
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(json(result).at("/data/goalId").asText());
  }

  private void setGoalAvailability(ActorSession student, UUID goalId) throws Exception {
    unsafe(
            put("/api/v1/profiles/students/me/goals/{id}/availability", goalId)
                .content(
                    "{\"slots\":[{\"dayOfWeek\":1,\"startTime\":\"18:30\","
                        + "\"endTime\":\"19:30\"}]}"),
            student)
        .andExpect(status().isOk());
  }

  private org.springframework.test.web.servlet.ResultActions match(
      ActorSession actor, UUID goalId) throws Exception {
    return unsafe(
        post("/api/v1/discovery/match")
            .content("{\"goalId\":\"" + goalId + "\",\"topN\":5}"),
        actor);
  }

  private UUID createConnectionRequest(ActorSession actor, UUID goalId, UUID targetTutorId)
      throws Exception {
    MvcResult result =
        unsafe(
                post("/api/v1/connections/requests")
                    .content(connectionBody(goalId, targetTutorId)),
                actor)
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(json(result).at("/data/requestId").asText());
  }

  private String connectionBody(UUID goalId, UUID targetTutorId) {
    return "{\"goalId\":\""
        + goalId
        + "\",\"tutorId\":\""
        + targetTutorId
        + "\",\"desiredSlots\":[\"MONDAY 18:30-19:30\"],"
        + "\"message\":\"Please help with Math\"}";
  }

  private MvcResult accept(ActorSession tutor, UUID requestId) throws Exception {
    return unsafe(
            post("/api/v1/connections/requests/{id}/respond", requestId)
                .content("{\"accept\":true}"),
            tutor)
        .andReturn();
  }

  private org.springframework.test.web.servlet.ResultActions unsafe(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
      ActorSession actor) throws Exception {
    return mvc.perform(
        request
            .cookie(actor.session(), actor.csrf())
            .header("X-XSRF-TOKEN", actor.csrf().getValue())
            .contentType(MediaType.APPLICATION_JSON));
  }

  private Cookie csrf(Cookie... cookies) throws Exception {
    var request = get("/api/v1/auth/csrf");
    if (cookies.length > 0) request.cookie(cookies);
    return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
  }

  private Cookie responseCookie(MvcResult result, String name) {
    for (String header : result.getResponse().getHeaders("Set-Cookie")) {
      var parsed = java.net.HttpCookie.parse(header).getFirst();
      if (parsed.getName().equals(name)) {
        var cookie = new Cookie(name, parsed.getValue());
        cookie.setPath(parsed.getPath());
        cookie.setHttpOnly(parsed.isHttpOnly());
        return cookie;
      }
    }
    return null;
  }

  private JsonNode json(MvcResult result) {
    return JSON.readTree(result.getResponse().getContentAsByteArray());
  }

  private String jsonBody(String name, String value) {
    return "{\"" + name + "\":\"" + value + "\"}";
  }

  private int futureStatus(Future<Integer> result) {
    try {
      return result.get();
    } catch (Exception ex) {
      throw new AssertionError(ex);
    }
  }

  private void assertSuccessfulMatchingAudit(UUID goalId, UUID actorId) {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from matching_run_log where goal_id=? and user_id=?"
                    + " and student_id=? and result_count=1",
                Integer.class,
                goalId,
                actorId,
                studentId))
        .isEqualTo(1);
    assertThat(jdbc.queryForList("select status from ai_request_log", String.class))
        .containsExactly("SUCCESS");
    assertThat(jdbc.queryForList("select status from ai_match_reservation", String.class))
        .containsExactly("SUCCESS");
  }

  private static HttpServer startProvider() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setExecutor(providerExecutor);
      server.createContext(
          "/v1beta/models/test-model:generateContent",
          exchange -> {
            providerCalls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            try {
              if (providerMode.equals("timeout")) Thread.sleep(1000);
              String body =
                  providerMode.equals("blocked")
                      ? "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"
                      : "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"1|Grounded E2E answer\"}]}}]}";
              byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
              exchange.getResponseHeaders().set("Content-Type", "application/json");
              exchange.sendResponseHeaders(providerMode.equals("http429") ? 429 : 200, bytes.length);
              exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            } catch (IOException ignored) {
              // A timeout closes the client connection before the fake writes its response.
            } finally {
              exchange.close();
            }
          });
      server.start();
      return server;
    } catch (IOException ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  private record ActorSession(Cookie session, Cookie csrf) {}
}

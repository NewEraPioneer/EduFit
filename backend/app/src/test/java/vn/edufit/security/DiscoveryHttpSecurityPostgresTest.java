package vn.edufit.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import vn.edufit.iam.infra.security.Argon2PasswordService;

/**
 * Real application, JDBC session filter, security filters and IAM; no mocked identities/facades.
 */
@SpringBootTest(
    properties = {"edufit.ai.provider=stub", "spring.session.jdbc.initialize-schema=never"})
@Testcontainers
@DirtiesContext
class DiscoveryHttpSecurityPostgresTest {
  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired WebApplicationContext context;
  @Autowired JdbcTemplate jdbc;
  @Autowired Argon2PasswordService encoder;
  MockMvc mvc;
  UUID studentId, goalId;

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
    UUID accountId = UUID.randomUUID();
    studentId = UUID.randomUUID();
    goalId = UUID.randomUUID();
    jdbc.update(
        "insert into account(user_id,email,password_hash,full_name,role,status) values(?,?,?,?,"
            + " 'STUDENT','ACTIVE')",
        accountId,
        "student@example.test",
        encoder.hash("Password123@"),
        "Student");
    jdbc.update("insert into education_level(level_id,name,sort_order) values(1,'Grade',1)");
    jdbc.update("insert into subject(subject_id,name) values(1,'Math')");
    jdbc.update(
        "insert into student_profile(student_id,user_id,education_level_id) values(?,?,1)",
        studentId,
        accountId);
    jdbc.update(
        "insert into"
            + " learning_goal(goal_id,student_id,subject_id,current_level,desired_level,goal_type,deadline,mode,budget_max)"
            + " values(?,?,1,1,2,'IMPROVE_GRADES',current_date+30,'ONLINE',200000)",
        goalId,
        studentId);
    jdbc.update(
        "insert into goal_availability_slot(goal_id,day_of_week,start_time,end_time)"
            + " values(?,1,'09:00','10:00')",
        goalId);
  }

  @Test
  void csrfBootstrapAndPublicReadsNeedNoSession() throws Exception {
    var result =
        mvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"))
            .andExpect(jsonPath("$.data.parameterName").value("_csrf"))
            .andReturn();
    Cookie token = result.getResponse().getCookie("XSRF-TOKEN");
    assertNotNull(token);
    assertEquals("/", token.getPath());
    assertFalse(token.isHttpOnly());
    assertNull(result.getResponse().getCookie("EDUFIT_SESSION"));
    assertTrue(result.getResponse().getContentAsString().contains(token.getValue()));
    mvc.perform(get("/api/v1/discovery/tutors")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/discovery/tutors/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @ValueSource(strings = {"http://localhost:3000", "http://127.0.0.1:3000"})
  void corsPreflightBeforeAuthentication(String origin) throws Exception {
    mvc.perform(
            options("/api/v1/discovery/match")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", origin))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
        .andExpect(header().exists("Access-Control-Allow-Headers"));
  }

  @Test
  void alienOriginRejected() throws Exception {
    mvc.perform(
            options("/api/v1/discovery/match")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }

  @Test
  void sessionAndCsrfAreBothRequiredAndLogoutRevokesStoredSession() throws Exception {
    Cookie preLogin = csrf();
    mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(loginBody()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    MvcResult login =
        mvc.perform(
                post("/api/v1/auth/login")
                    .cookie(preLogin)
                    .header("X-XSRF-TOKEN", preLogin.getValue())
                    .contentType("application/json")
                    .content(loginBody()))
            .andExpect(status().isOk())
            .andReturn();
    Cookie session = responseCookie(login, "EDUFIT_SESSION");
    assertNotNull(session);
    assertTrue(session.isHttpOnly());
    assertEquals("/", session.getPath());
    assertEquals(0, login.getResponse().getCookie("XSRF-TOKEN").getMaxAge());
    Cookie token = csrf(session);
    assertNotEquals(preLogin.getValue(), token.getValue());
    mvc.perform(get("/api/v1/auth/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("student@example.test"));
    mvc.perform(
            post("/api/v1/discovery/match")
                .cookie(session)
                .contentType("application/json")
                .content(matchBody()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mvc.perform(
            post("/api/v1/discovery/match")
                .cookie(session, token)
                .header("X-XSRF-TOKEN", "wrong")
                .contentType("application/json")
                .content(matchBody()))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/discovery/match")
                .cookie(session, token)
                .header("X-XSRF-TOKEN", token.getValue())
                .contentType("application/json")
                .content(matchBody()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
    mvc.perform(
            post("/api/v1/auth/logout")
                .cookie(session, token)
                .header("X-XSRF-TOKEN", token.getValue()))
        .andExpect(status().isOk())
        .andExpect(result -> assertEquals(0, responseCookie(result, "EDUFIT_SESSION").getMaxAge()))
        .andExpect(cookie().maxAge("XSRF-TOKEN", 0));
    mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/discovery/match")
                .cookie(session, token)
                .header("X-XSRF-TOKEN", token.getValue())
                .contentType("application/json")
                .content(matchBody()))
        .andExpect(status().isUnauthorized());
    assertNotEquals(token.getValue(), csrf().getValue());
  }

  @Test
  void anonymousUnsafeRequestHasDocumentedFilterOrdering() throws Exception {
    mvc.perform(
            post("/api/v1/discovery/match").contentType("application/json").content(matchBody()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    Cookie token = csrf();
    mvc.perform(
            post("/api/v1/discovery/match")
                .cookie(token)
                .header("X-XSRF-TOKEN", token.getValue())
                .contentType("application/json")
                .content(matchBody()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void reloginRotatesExistingSessionAndDoesNotTrustOldSessionCookie() throws Exception {
    Cookie token = csrf();
    MvcResult firstLogin =
        mvc.perform(
                post("/api/v1/auth/login")
                    .cookie(token)
                    .header("X-XSRF-TOKEN", token.getValue())
                    .contentType("application/json")
                    .content(loginBody()))
            .andExpect(status().isOk())
            .andReturn();
    Cookie original = responseCookie(firstLogin, "EDUFIT_SESSION");
    token = csrf(original);
    MvcResult secondLogin =
        mvc.perform(
                post("/api/v1/auth/login")
                    .cookie(original, token)
                    .header("X-XSRF-TOKEN", token.getValue())
                    .contentType("application/json")
                    .content(loginBody()))
            .andExpect(status().isOk())
            .andReturn();
    Cookie rotated = responseCookie(secondLogin, "EDUFIT_SESSION");
    assertNotNull(rotated);
    assertNotEquals(original.getValue(), rotated.getValue());
    mvc.perform(get("/api/v1/auth/me").cookie(original)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").cookie(rotated)).andExpect(status().isOk());
    assertEquals(1, jdbc.queryForObject("select count(*) from spring_session", Integer.class));
    assertEquals(
        3600,
        jdbc.queryForObject("select max_inactive_interval from spring_session", Integer.class));
  }

  Cookie csrf(Cookie... cookies) throws Exception {
    var request = get("/api/v1/auth/csrf");
    if (cookies.length > 0) request.cookie(cookies);
    return mvc.perform(request)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("XSRF-TOKEN");
  }

  Cookie responseCookie(MvcResult result, String name) {
    // Spring Session writes Set-Cookie headers directly, not ServletResponse.addCookie.
    for (String header : result.getResponse().getHeaders("Set-Cookie")) {
      var parsed = java.net.HttpCookie.parse(header).getFirst();
      if (parsed.getName().equals(name)) {
        var cookie = new Cookie(name, parsed.getValue());
        cookie.setPath(parsed.getPath());
        cookie.setHttpOnly(parsed.isHttpOnly());
        cookie.setMaxAge((int) parsed.getMaxAge());
        return cookie;
      }
    }
    fail("Missing cookie " + name + "; headers=" + result.getResponse().getHeaderNames());
    return null;
  }

  String loginBody() {
    return "{\"email\":\"student@example.test\",\"password\":\"Password123@\"}";
  }

  String matchBody() {
    return "{\"goalId\":\"" + goalId + "\"}";
  }
}

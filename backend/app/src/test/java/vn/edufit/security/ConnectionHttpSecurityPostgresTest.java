package vn.edufit.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import vn.edufit.iam.infra.security.Argon2PasswordService;

/**
 * End-to-end security integration test for Connection module routes,
 * verifying real Spring Security filter chain, session authentication, SPA CSRF,
 * role authorization, malformed inputs, error mapping, and privacy whitelists.
 */
@SpringBootTest(
    properties = {"edufit.ai.provider=stub", "spring.session.jdbc.initialize-schema=never"})
@Testcontainers
@DirtiesContext
class ConnectionHttpSecurityPostgresTest {
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
  UUID studentUser, studentId, parentUser, tutorUser, tutorId, adminUser;
  String studentEmail = "student_sec@test.invalid";
  String parentEmail = "parent_sec@test.invalid";
  String tutorEmail = "tutor_sec@test.invalid";
  String adminEmail = "admin_sec@test.invalid";
  String password = "Password123@";

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context)
        .addFilters(
            context.getBean("springSessionRepositoryFilter", Filter.class),
            context.getBean("springSecurityFilterChain", Filter.class))
        .build();

    jdbc.execute("truncate account, subject, education_level cascade");
    jdbc.execute("delete from spring_session");

    studentUser = UUID.randomUUID();
    studentId = UUID.randomUUID();
    parentUser = UUID.randomUUID();
    tutorUser = UUID.randomUUID();
    tutorId = UUID.randomUUID();
    adminUser = UUID.randomUUID();

    String hash = encoder.hash(password);

    // Seed Accounts
    jdbc.update("insert into account(user_id, email, password_hash, full_name, role, status) values (?, ?, ?, 'Student User', 'STUDENT', 'ACTIVE')", studentUser, studentEmail, hash);
    jdbc.update("insert into account(user_id, email, password_hash, full_name, role, status) values (?, ?, ?, 'Parent User', 'PARENT', 'ACTIVE')", parentUser, parentEmail, hash);
    jdbc.update("insert into account(user_id, email, password_hash, full_name, role, status) values (?, ?, ?, 'Tutor User', 'TUTOR', 'ACTIVE')", tutorUser, tutorEmail, hash);
    jdbc.update("insert into account(user_id, email, password_hash, full_name, role, status) values (?, ?, ?, 'Admin User', 'ADMIN', 'ACTIVE')", adminUser, adminEmail, hash);

    // Seed Profiles
    jdbc.update("insert into education_level(level_id, name, sort_order) values(1, 'Grade 10', 1)");
    jdbc.update("insert into subject(subject_id, name) values(1, 'Math')");
    jdbc.update("insert into student_profile(student_id, user_id, education_level_id) values (?, ?, 1)", studentId, studentUser);
    jdbc.update("insert into tutor_profile(tutor_id, user_id, display_name, teaching_mode, status) values (?, ?, 'Tutor Pro', 'ONLINE', 'VERIFIED')", tutorId, tutorUser);
  }

  // --- Helpers ---

  private Cookie csrf(Cookie... cookies) throws Exception {
    var req = get("/api/v1/auth/csrf");
    if (cookies.length > 0) req.cookie(cookies);
    return mvc.perform(req)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("XSRF-TOKEN");
  }

  private Cookie login(String email, String pwd) throws Exception {
    Cookie preCsrf = csrf();
    String body = "{\"email\":\"" + email + "\",\"password\":\"" + pwd + "\"}";
    MvcResult result = mvc.perform(
        post("/api/v1/auth/login")
            .cookie(preCsrf)
            .header("X-XSRF-TOKEN", preCsrf.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andReturn();

    return responseCookie(result, "EDUFIT_SESSION");
  }

  private Cookie responseCookie(MvcResult result, String name) {
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

  // --- Tests ---

  @Test
  void unauthenticatedAccessToConnectionRoutesIsRejectedWith401() throws Exception {
    // Legacy endpoints
    mvc.perform(get("/api/v1/connections/parent-student/invitations"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    mvc.perform(get("/api/v1/connections/parent-student/links"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    mvc.perform(get("/api/v1/connections/requests"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    // Paged endpoints
    mvc.perform(get("/api/v1/connections/parent-student/invitations/page"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    mvc.perform(get("/api/v1/connections/parent-student/links/page"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    mvc.perform(get("/api/v1/connections/requests/page"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    // Admin endpoint
    mvc.perform(get("/api/v1/connections/admin/parent-student/links").param("email", "any@test.invalid"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void spaCsrfProtectionGuardsConnectionWriteEndpoints() throws Exception {
    Cookie session = login(parentEmail, password);
    Cookie csrfToken = csrf(session);

    String inviteBody = "{\"targetEmail\":\"" + studentEmail + "\"}";

    // 1. Missing CSRF token -> 403 Forbidden
    mvc.perform(post("/api/v1/connections/parent-student/invite")
            .cookie(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(inviteBody))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    // 2. Invalid CSRF token -> 403 Forbidden
    mvc.perform(post("/api/v1/connections/parent-student/invite")
            .cookie(session, csrfToken)
            .header("X-XSRF-TOKEN", "invalid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(inviteBody))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    // 3. Valid session + Valid CSRF header -> 200 OK
    mvc.perform(post("/api/v1/connections/parent-student/invite")
            .cookie(session, csrfToken)
            .header("X-XSRF-TOKEN", csrfToken.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content(inviteBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void adminEndpointRejectsNonAdminRolesWith403() throws Exception {
    // Student session
    Cookie studentSession = login(studentEmail, password);
    mvc.perform(get("/api/v1/connections/admin/parent-student/links")
            .param("email", studentEmail)
            .cookie(studentSession))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    // Parent session
    Cookie parentSession = login(parentEmail, password);
    mvc.perform(get("/api/v1/connections/admin/parent-student/links")
            .param("email", studentEmail)
            .cookie(parentSession))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    // Tutor session
    Cookie tutorSession = login(tutorEmail, password);
    mvc.perform(get("/api/v1/connections/admin/parent-student/links")
            .param("email", studentEmail)
            .cookie(tutorSession))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    // Admin session -> 200 OK
    Cookie adminSession = login(adminEmail, password);
    mvc.perform(get("/api/v1/connections/admin/parent-student/links")
            .param("email", studentEmail)
            .cookie(adminSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items").isArray());
  }

  @Test
  void malformedInputsReturn400BadRequest() throws Exception {
    Cookie session = login(tutorEmail, password);
    Cookie csrfToken = csrf(session);

    // 1. Malformed UUID in path variable
    mvc.perform(post("/api/v1/connections/requests/not-a-uuid/cancel")
            .cookie(session, csrfToken)
            .header("X-XSRF-TOKEN", csrfToken.getValue()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    // 2. Malformed JSON body
    mvc.perform(post("/api/v1/connections/requests/" + UUID.randomUUID() + "/respond")
            .cookie(session, csrfToken)
            .header("X-XSRF-TOKEN", csrfToken.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"accept\": true, invalid_json}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    // 3. Missing required boolean accept
    mvc.perform(post("/api/v1/connections/requests/" + UUID.randomUUID() + "/respond")
            .cookie(session, csrfToken)
            .header("X-XSRF-TOKEN", csrfToken.getValue())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\": \"no boolean accept provided\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void tutorInboxEnforcesBR37PrivacyWhitelistWithoutLeakage() throws Exception {
    // Seed an active connection request for this tutor
    UUID reqId = UUID.randomUUID();
    UUID needId = UUID.randomUUID();
    Instant now = Instant.now();

    jdbc.update("insert into learning_need(need_id, student_id, created_by, education_level_id, subject_id, mode, budget_max_per_session, status) " +
        "values (?, ?, ?, 1, 1, 'ONLINE', 200000, 'ACTIVE')", needId, studentId, studentUser);

    jdbc.update("insert into connection_request(request_id, student_id, tutor_id, need_id, sent_by_user_id, desired_slots, message, status, expires_at) " +
        "values (?, ?, ?, ?, ?, cast(? as jsonb), 'Please help with Math', 'PENDING', ?)",
        reqId, studentId, tutorId, needId, studentUser, "[\"Tue 19:00\"]", java.sql.Timestamp.from(now.plusSeconds(86400)));

    Cookie tutorSession = login(tutorEmail, password);

    // Call Tutor Inbox
    MvcResult result = mvc.perform(get("/api/v1/connections/requests").cookie(tutorSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].requestId").value(reqId.toString()))
        .andExpect(jsonPath("$.data[0].need.subjectName").value("Math"))
        .andExpect(jsonPath("$.data[0].need.educationLevelName").value("Grade 10"))
        .andExpect(jsonPath("$.data[0].senderRole").value("STUDENT"))
        .andExpect(jsonPath("$.data[0].desiredSlots[0]").value("Tue 19:00"))
        .andReturn();

    String content = result.getResponse().getContentAsString();

    // Verify strict BR-37 whitelist: NO email, password, contact, unrequested goals
    assertFalse(content.contains(studentEmail), "Must not leak student email");
    assertFalse(content.contains(parentEmail), "Must not leak parent email");
    assertFalse(content.contains("password_hash"), "Must not leak password hash");
    assertFalse(content.contains("budget_max"), "Must not leak budget_max");
  }
}

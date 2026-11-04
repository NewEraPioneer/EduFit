package vn.edufit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.edufit.iam.application.OtpPolicy;
import vn.edufit.iam.application.port.AccountEmailSender;

/**
 * Kiểm thử tích hợp luồng xác thực email khi đăng ký và quên mật khẩu bằng OTP qua HTTP thật:
 * Spring Security (CSRF, permitAll, phiên JDBC) → IAM → PostgreSQL 17 (Flyway V1.33).
 * Chỉ thay adapter gửi email để đọc mã OTP đã gửi.
 */
@SpringBootTest(properties = {"edufit.ai.provider=stub", "spring.session.jdbc.initialize-schema=never"})
@Testcontainers
@DirtiesContext
class AuthOtpFlowPostgresTest {

  private static final String PASSWORD = "Password123@";
  private static final String NEW_PASSWORD = "BrandNew456@";
  private static final String FULL_NAME = "Học Sinh OTP";
  private static final String SESSION_COOKIE = "EDUFIT_SESSION";
  private static final String CSRF_COOKIE = "XSRF-TOKEN";
  private static final String CSRF_HEADER = "X-XSRF-TOKEN";

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
  @Autowired OtpPolicy otpPolicy;
  @MockitoBean AccountEmailSender emailSender;

  MockMvc mvc;
  String email;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context)
        .addFilters(
            context.getBean("springSessionRepositoryFilter", Filter.class),
            context.getBean("springSecurityFilterChain", Filter.class))
        .build();
    email = "otp-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    clearInvocations(emailSender);
  }

  // ---------------------------------------------------------------- Đăng ký

  @Test
  @DisplayName("IT-OTP-01: Chưa xác thực email thì đăng nhập báo như tài khoản không tồn tại; xác thực xong vào được ngay")
  void registration_requiresEmailVerificationBeforeAccountExists() throws Exception {
    postJson("/api/v1/auth/register", registerBody("STUDENT"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.email").value(email))
        .andExpect(jsonPath("$.data.resendAvailableAt").exists());

    assertThat(accountCount()).isZero();
    postJson("/api/v1/auth/login", loginBody(PASSWORD))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

    MvcResult verified = postJson("/api/v1/auth/register/verify", verifyBody(lastRegistrationCode()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.email").value(email))
        .andExpect(jsonPath("$.data.role").value("STUDENT"))
        .andReturn();

    assertThat(accountCount()).isEqualTo(1);
    Cookie session = cookie(verified, SESSION_COOKIE);
    mvc.perform(get("/api/v1/auth/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value(email));
    postJson("/api/v1/auth/login", loginBody(PASSWORD)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("IT-OTP-02: Nhập sai quá số lần thì mã bị khóa kể cả mã đúng; lần sai được lưu bền vào CSDL")
  void registration_wrongCodeAttemptsArePersistedAndCapped() throws Exception {
    postJson("/api/v1/auth/register", registerBody("TUTOR")).andExpect(status().isAccepted());
    String code = lastRegistrationCode();
    String wrong = code.equals("000000") ? "111111" : "000000";

    for (int i = 0; i < otpPolicy.maxFailedAttempts(); i++) {
      postJson("/api/v1/auth/register/verify", verifyBody(wrong))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
    assertThat(jdbc.queryForObject(
        "select failed_attempts from email_otp_challenge where email = ? and consumed_at is null",
        Integer.class, email)).isEqualTo(otpPolicy.maxFailedAttempts());

    postJson("/api/v1/auth/register/verify", verifyBody(code))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    assertThat(accountCount()).isZero();
  }

  @Test
  @DisplayName("IT-OTP-03: Gửi lại mã trong thời gian chờ bị chặn 429; mã định dạng sai bị validation chặn")
  void registration_resendCooldownAndValidation() throws Exception {
    postJson("/api/v1/auth/register", registerBody("PARENT")).andExpect(status().isAccepted());

    postJson("/api/v1/auth/register/resend", "{\"email\":\"" + email + "\"}")
        .andExpect(status().isTooManyRequests());
    postJson("/api/v1/auth/register/verify", verifyBody("12ab56"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @DisplayName("IT-OTP-04: CSDL chỉ cho phép một thử thách chưa dùng cho mỗi (mục đích, email) và chặn ADMIN")
  void database_enforcesSingleActiveChallengeAndRoleWhitelist() throws Exception {
    postJson("/api/v1/auth/register", registerBody("STUDENT")).andExpect(status().isAccepted());
    String sha = "a".repeat(64);

    assertThatThrownBy(() -> jdbc.update("""
        insert into email_otp_challenge(challenge_id, purpose, email, code_hash, send_window_started_at,
          last_sent_at, expires_at, password_hash, full_name, role)
        values (gen_random_uuid(), 'REGISTRATION', ?, ?, now(), now(), now(), 'h', 'n', 'STUDENT')
        """, email, sha)).isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("""
        insert into email_otp_challenge(challenge_id, purpose, email, code_hash, send_window_started_at,
          last_sent_at, expires_at, password_hash, full_name, role)
        values (gen_random_uuid(), 'REGISTRATION', 'other@example.test', ?, now(), now(), now(), 'h', 'n', 'ADMIN')
        """, sha)).isInstanceOf(DataIntegrityViolationException.class);
  }

  // ---------------------------------------------------------- Quên mật khẩu

  @Test
  @DisplayName("IT-OTP-05: Quên mật khẩu: OTP → token → mật khẩu mới, đăng nhập luôn và thu hồi phiên cũ")
  void passwordReset_fullFlowRevokesOldSessions() throws Exception {
    Cookie oldSession = registerAndVerify();
    mvc.perform(get("/api/v1/auth/me").cookie(oldSession)).andExpect(status().isOk());

    postJson("/api/v1/auth/forgot-password", "{\"email\":\"" + email + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value(email));

    MvcResult grant = postJson("/api/v1/auth/forgot-password/verify", verifyBody(lastResetCode()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resetToken").exists())
        .andReturn();
    String resetToken = JsonPath.read(grant.getResponse().getContentAsString(), "$.data.resetToken");

    MvcResult reset = postJson("/api/v1/auth/reset-password",
        "{\"token\":\"" + resetToken + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value(email))
        .andReturn();

    mvc.perform(get("/api/v1/auth/me").cookie(oldSession)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").cookie(cookie(reset, SESSION_COOKIE))).andExpect(status().isOk());
    postJson("/api/v1/auth/login", loginBody(PASSWORD)).andExpect(status().isUnauthorized());
    postJson("/api/v1/auth/login", loginBody(NEW_PASSWORD)).andExpect(status().isOk());

    postJson("/api/v1/auth/reset-password",
        "{\"token\":\"" + resetToken + "\",\"newPassword\":\"Another789@\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TOKEN_ALREADY_USED"));
  }

  @Test
  @DisplayName("IT-OTP-06: Email không tồn tại nhận phản hồi giống hệt email có thật và không có thư nào được gửi")
  void passwordReset_unknownEmailIsIndistinguishable() throws Exception {
    String unknown = "nobody-" + UUID.randomUUID() + "@example.test";

    postJson("/api/v1/auth/forgot-password", "{\"email\":\"" + unknown + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.email").value(unknown))
        .andExpect(jsonPath("$.data.codeExpiresAt").exists())
        .andExpect(jsonPath("$.data.resendAvailableAt").exists());
    verify(emailSender, never()).sendPasswordResetCode(any(), any(), any(), any());

    postJson("/api/v1/auth/forgot-password/verify",
        "{\"email\":\"" + unknown + "\",\"code\":\"123456\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  // ---------------------------------------------------------------- Helpers

  private Cookie registerAndVerify() throws Exception {
    postJson("/api/v1/auth/register", registerBody("STUDENT")).andExpect(status().isAccepted());
    MvcResult verified = postJson("/api/v1/auth/register/verify", verifyBody(lastRegistrationCode()))
        .andExpect(status().isCreated())
        .andReturn();
    return cookie(verified, SESSION_COOKIE);
  }

  private String lastRegistrationCode() {
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(emailSender, atLeastOnce()).sendRegistrationCode(eq(email), eq(FULL_NAME), code.capture(), any());
    return code.getValue();
  }

  private String lastResetCode() {
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(emailSender, atLeastOnce()).sendPasswordResetCode(eq(email), eq(FULL_NAME), code.capture(), any());
    return code.getValue();
  }

  private ResultActions postJson(String path, String body) throws Exception {
    Cookie csrf = cookie(mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn(), CSRF_COOKIE);
    return mvc.perform(post(path)
        .cookie(csrf)
        .header(CSRF_HEADER, csrf.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private String registerBody(String role) {
    return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"fullName\":\""
        + FULL_NAME + "\",\"role\":\"" + role + "\"}";
  }

  private String verifyBody(String code) {
    return "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}";
  }

  private String loginBody(String password) {
    return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
  }

  private int accountCount() {
    return jdbc.queryForObject("select count(*) from account where email = ?", Integer.class, email);
  }

  private static Cookie cookie(MvcResult result, String name) {
    List<String> seen = new ArrayList<>();
    for (String header : result.getResponse().getHeaders("Set-Cookie")) {
      HttpCookie parsed = HttpCookie.parse(header).getFirst();
      seen.add(parsed.getName());
      if (parsed.getName().equals(name)) {
        Cookie cookie = new Cookie(name, parsed.getValue());
        cookie.setPath(parsed.getPath());
        return cookie;
      }
    }
    fail("Thiếu cookie " + name + "; có: " + seen);
    return null;
  }
}

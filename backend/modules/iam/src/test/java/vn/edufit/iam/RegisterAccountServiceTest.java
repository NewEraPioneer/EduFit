package vn.edufit.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.application.OtpChallengeView;
import vn.edufit.iam.application.OtpPolicy;
import vn.edufit.iam.application.PasswordPolicy;
import vn.edufit.iam.application.RegisterAccountService;
import vn.edufit.iam.application.port.AccountEmailSender;
import vn.edufit.iam.application.port.EmailDeliveryException;
import vn.edufit.iam.infra.persistence.Account;
import vn.edufit.iam.infra.persistence.AccountRepository;
import vn.edufit.iam.infra.persistence.EmailOtpChallenge;
import vn.edufit.iam.infra.persistence.EmailOtpChallengeRepository;
import vn.edufit.iam.infra.persistence.OtpPurpose;
import vn.edufit.iam.infra.security.Argon2PasswordService;
import vn.edufit.iam.infra.security.TokenCryptoService;
import vn.edufit.shared.exception.BaseBusinessException;
import vn.edufit.shared.exception.ErrorCode;

@DisplayName("Kiểm thử Đơn vị Đăng ký có xác thực email OTP (RegisterAccountService - UC1.1)")
class RegisterAccountServiceTest {

  private static final OtpPolicy POLICY = new OtpPolicy(
      Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 5, Duration.ofHours(1));
  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");
  private static final String EMAIL = "New.Student@EduFit.Test";
  private static final String NORMALIZED_EMAIL = "new.student@edufit.test";
  private static final String PASSWORD = "Password123@";
  private static final String PASSWORD_HASH = "$argon2id$hashed";
  private static final String FULL_NAME = "Nguyễn Văn A";

  private AccountRepository accountRepository;
  private EmailOtpChallengeRepository challengeRepository;
  private AccountEmailSender emailSender;
  private MutableClock clock;
  private RegisterAccountService service;
  private final AtomicReference<EmailOtpChallenge> stored = new AtomicReference<>();

  @BeforeEach
  void setUp() {
    accountRepository = mock(AccountRepository.class);
    challengeRepository = mock(EmailOtpChallengeRepository.class);
    emailSender = mock(AccountEmailSender.class);
    Argon2PasswordService passwordService = mock(Argon2PasswordService.class);
    clock = new MutableClock(NOW);

    when(passwordService.hash(PASSWORD)).thenReturn(PASSWORD_HASH);
    when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(challengeRepository.findActiveForUpdate(eq(OtpPurpose.REGISTRATION), anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(stored.get())
            .filter(c -> c.getEmail().equals(invocation.getArgument(1)) && c.getConsumedAt() == null));
    when(challengeRepository.saveAndFlush(any(EmailOtpChallenge.class))).thenAnswer(invocation -> {
      stored.set(invocation.getArgument(0));
      return invocation.getArgument(0);
    });
    when(challengeRepository.save(any(EmailOtpChallenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service = new RegisterAccountService(
        accountRepository, challengeRepository, passwordService, new PasswordPolicy(),
        new TokenCryptoService(), emailSender, POLICY, clock);
  }

  private String lastSentCode() {
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(emailSender, atLeastOnce())
        .sendRegistrationCode(eq(NORMALIZED_EMAIL), eq(FULL_NAME), code.capture(), eq(POLICY.codeTtl()));
    return code.getValue();
  }

  private static ErrorCode errorCodeOf(Throwable error) {
    return ((BaseBusinessException) error).getErrorCode();
  }

  @Test
  @DisplayName("IAM-REG-001: Đăng ký chỉ gửi mã OTP, CHƯA tạo tài khoản (email chưa xác thực = chưa đăng ký)")
  void startRegistration_sendsCodeWithoutCreatingAccount() {
    OtpChallengeView view = service.startRegistration(EMAIL, PASSWORD, FULL_NAME, AccountRole.STUDENT);

    assertThat(view.email()).isEqualTo(NORMALIZED_EMAIL);
    assertThat(view.codeExpiresAt()).isEqualTo(NOW.plus(POLICY.codeTtl()));
    assertThat(view.resendAvailableAt()).isEqualTo(NOW.plus(POLICY.resendCooldown()));
    assertThat(lastSentCode()).matches("^[0-9]{6}$");
    assertThat(stored.get().getPasswordHash()).isEqualTo(PASSWORD_HASH);
    verify(accountRepository, never()).save(any(Account.class));
  }

  @Test
  @DisplayName("IAM-REG-002: Nhập đúng mã thì tạo tài khoản ACTIVE với mật khẩu đã băm từ bước đăng ký")
  void completeRegistration_withCorrectCode_createsActiveAccount() {
    service.startRegistration(EMAIL, PASSWORD, FULL_NAME, AccountRole.TUTOR);
    clock.advance(Duration.ofSeconds(30));

    Account account = service.completeRegistration(EMAIL, lastSentCode());

    assertThat(account.getEmail()).isEqualTo(NORMALIZED_EMAIL);
    assertThat(account.getPasswordHash()).isEqualTo(PASSWORD_HASH);
    assertThat(account.getRole()).isEqualTo(AccountRole.TUTOR);
    assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(stored.get().getConsumedAt()).isNotNull();
  }

  @Test
  @DisplayName("IAM-REG-003: Nhập sai mã bị từ chối, số lần sai được lưu lại và không tạo tài khoản")
  void completeRegistration_withWrongCode_isRejectedAndCounted() {
    service.startRegistration(EMAIL, PASSWORD, FULL_NAME, AccountRole.STUDENT);
    String wrongCode = lastSentCode().equals("000000") ? "111111" : "000000";

    assertThatThrownBy(() -> service.completeRegistration(EMAIL, wrongCode))
        .satisfies(error -> assertThat(errorCodeOf(error)).isEqualTo(ErrorCode.INVALID_TOKEN))
        .hasMessageContaining("còn 4 lần");
    assertThat(stored.get().getFailedAttempts()).isEqualTo(1);
    verify(challengeRepository).save(stored.get());
    verify(accountRepository, never()).save(any(Account.class));
  }

  @Test
  @DisplayName("IAM-REG-004: Từ chối đăng ký khi email đã thuộc một tài khoản (BR-01)")
  void startRegistration_existingAccount_conflicts() {
    when(accountRepository.existsByEmail(NORMALIZED_EMAIL)).thenReturn(true);

    assertThatThrownBy(() -> service.startRegistration(EMAIL, PASSWORD, FULL_NAME, AccountRole.STUDENT))
        .satisfies(error -> assertThat(errorCodeOf(error)).isEqualTo(ErrorCode.USER_ALREADY_EXISTS));
    verify(emailSender, never()).sendRegistrationCode(any(), any(), any(), any());
  }

  @Test
  @DisplayName("IAM-REG-005: Chặn tự đăng ký vai trò ADMIN (BR-03) và mật khẩu yếu (BR-02)")
  void startRegistration_adminOrWeakPassword_isRejected() {
    assertThatThrownBy(() -> service.startRegistration(EMAIL, PASSWORD, FULL_NAME, AccountRole.ADMIN))
        .satisfies(error -> assertThat(errorCodeOf(error)).isEqualTo(ErrorCode.FORBIDDEN));
    assertThatThrownBy(() -> service.startRegistration(EMAIL, "short", FULL_NAME, AccountRole.STUDENT))
        .isInstanceOf(BaseBusinessException.class);
    verify(emailSender, never()).sendRegistrationCode(any(), any(), any(), any());
  }

  @Test
  @DisplayName("IAM-REG-006: Đăng ký lại trước khi xác thực: trong thời gian chờ bị chặn, sau đó thay thông tin và gửi mã mới")
  void reRegistration_respectsCooldownThenRotatesCode() {
    service.startRegistration(EMAIL, PASSWORD, FULL_NAME, AccountRole.STUDENT);
    String firstCode = lastSentCode();

    assertThatThrownBy(() -> service.startRegistration(EMAIL, PASSWORD, FULL_NAME, AccountRole.PARENT))
        .satisfies(error -> assertThat(errorCodeOf(error)).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED));

    clock.advance(POLICY.resendCooldown());
    service.startRegistration(EMAIL, PASSWORD, FULL_NAME, AccountRole.PARENT);
    String secondCode = lastSentCode();

    assertThat(stored.get().getRole()).isEqualTo(AccountRole.PARENT);
    if (!firstCode.equals(secondCode)) {
      assertThatThrownBy(() -> service.completeRegistration(EMAIL, firstCode))
          .satisfies(error -> assertThat(errorCodeOf(error)).isEqualTo(ErrorCode.INVALID_TOKEN));
    }
    assertThat(service.completeRegistration(EMAIL, secondCode).getRole()).isEqualTo(AccountRole.PARENT);
  }

  @Test
  @DisplayName("IAM-REG-007: Gửi email lỗi thì báo EMAIL_DELIVERY_FAILED để người dùng thử lại")
  void startRegistration_mailFailure_surfacesDeliveryError() {
    doThrow(new EmailDeliveryException("smtp down", null))
        .when(emailSender).sendRegistrationCode(any(), any(), any(), any());

    assertThatThrownBy(() -> service.startRegistration(EMAIL, PASSWORD, FULL_NAME, AccountRole.STUDENT))
        .satisfies(error -> assertThat(errorCodeOf(error)).isEqualTo(ErrorCode.EMAIL_DELIVERY_FAILED));
  }

  @Test
  @DisplayName("IAM-REG-008: Xác thực/gửi lại khi không có đăng ký đang chờ thì yêu cầu đăng ký lại")
  void verifyOrResend_withoutPendingRegistration_isRejected() {
    assertThatThrownBy(() -> service.completeRegistration(EMAIL, "123456"))
        .satisfies(error -> assertThat(errorCodeOf(error)).isEqualTo(ErrorCode.INVALID_TOKEN));
    assertThatThrownBy(() -> service.resendCode(EMAIL))
        .satisfies(error -> assertThat(errorCodeOf(error)).isEqualTo(ErrorCode.INVALID_TOKEN));
  }

  @Test
  @DisplayName("IAM-REG-009: Email bị đăng ký (ví dụ qua Google) trong lúc chờ mã thì không tạo trùng tài khoản")
  void completeRegistration_whenEmailTakenMeanwhile_conflicts() {
    service.startRegistration(EMAIL, PASSWORD, FULL_NAME, AccountRole.STUDENT);
    when(accountRepository.existsByEmail(NORMALIZED_EMAIL)).thenReturn(true);

    assertThatThrownBy(() -> service.completeRegistration(EMAIL, lastSentCode()))
        .satisfies(error -> assertThat(errorCodeOf(error)).isEqualTo(ErrorCode.USER_ALREADY_EXISTS));
    verify(accountRepository, never()).save(any(Account.class));
  }
}

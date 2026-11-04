package vn.edufit.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.application.ChangePasswordService;
import vn.edufit.iam.application.OtpChallengeView;
import vn.edufit.iam.application.OtpPolicy;
import vn.edufit.iam.application.PasswordPolicy;
import vn.edufit.iam.application.PasswordResetGrant;
import vn.edufit.iam.application.RequestPasswordResetService;
import vn.edufit.iam.application.ResetPasswordService;
import vn.edufit.iam.application.port.AccountEmailSender;
import vn.edufit.iam.application.port.SessionRevoker;
import vn.edufit.iam.infra.persistence.Account;
import vn.edufit.iam.infra.persistence.AccountRepository;
import vn.edufit.iam.infra.persistence.AccountToken;
import vn.edufit.iam.infra.persistence.AccountTokenRepository;
import vn.edufit.iam.infra.persistence.EmailOtpChallenge;
import vn.edufit.iam.infra.persistence.EmailOtpChallengeRepository;
import vn.edufit.iam.infra.persistence.OtpPurpose;
import vn.edufit.iam.infra.persistence.TokenType;
import vn.edufit.iam.infra.security.Argon2PasswordService;
import vn.edufit.iam.infra.security.TokenCryptoService;
import vn.edufit.shared.exception.BaseBusinessException;
import vn.edufit.shared.exception.ErrorCode;

@DisplayName("Kiểm thử Đơn vị Quên mật khẩu bằng OTP & Đổi mật khẩu (UC1.3, UC1.4)")
class PasswordResetAndChangeTest {

  private static final OtpPolicy POLICY = new OtpPolicy(
      Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 5, Duration.ofHours(1));
  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");
  private static final String EMAIL = "student@edufit.test";
  private static final String UNKNOWN_EMAIL = "unknown@edufit.test";
  private static final String FULL_NAME = "Học Sinh A";
  private static final String OLD_HASH = "$argon2id$old_hash";
  private static final String NEW_HASH = "$argon2id$new_hash";
  private static final String NEW_PASSWORD = "NewPassword123@";

  private AccountRepository accountRepository;
  private AccountTokenRepository accountTokenRepository;
  private EmailOtpChallengeRepository challengeRepository;
  private Argon2PasswordService passwordService;
  private AccountEmailSender emailSender;
  private SessionRevoker sessionRevoker;
  private MutableClock clock;
  private final TokenCryptoService crypto = new TokenCryptoService();
  private final AtomicReference<EmailOtpChallenge> storedChallenge = new AtomicReference<>();
  private final AtomicReference<AccountToken> storedToken = new AtomicReference<>();

  private RequestPasswordResetService requestResetService;
  private ResetPasswordService resetPasswordService;
  private ChangePasswordService changePasswordService;
  private Account account;

  @BeforeEach
  void setUp() {
    accountRepository = mock(AccountRepository.class);
    accountTokenRepository = mock(AccountTokenRepository.class);
    challengeRepository = mock(EmailOtpChallengeRepository.class);
    passwordService = mock(Argon2PasswordService.class);
    emailSender = mock(AccountEmailSender.class);
    sessionRevoker = mock(SessionRevoker.class);
    clock = new MutableClock(NOW);

    account = Account.of(UUID.randomUUID(), EMAIL, OLD_HASH, FULL_NAME, AccountRole.STUDENT, AccountStatus.ACTIVE);
    when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
    when(accountRepository.findByEmail(UNKNOWN_EMAIL)).thenReturn(Optional.empty());
    when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(passwordService.hash(anyString())).thenReturn(NEW_HASH);

    when(challengeRepository.findActiveForUpdate(eq(OtpPurpose.PASSWORD_RESET), anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(storedChallenge.get())
            .filter(c -> c.getEmail().equals(invocation.getArgument(1)) && c.getConsumedAt() == null));
    when(challengeRepository.save(any(EmailOtpChallenge.class))).thenAnswer(invocation -> {
      storedChallenge.set(invocation.getArgument(0));
      return invocation.getArgument(0);
    });
    when(accountTokenRepository.save(any(AccountToken.class))).thenAnswer(invocation -> {
      storedToken.set(invocation.getArgument(0));
      return invocation.getArgument(0);
    });
    when(accountTokenRepository.findByTokenHashAndTokenType(anyString(), eq(TokenType.RESET_PASSWORD)))
        .thenAnswer(invocation -> Optional.ofNullable(storedToken.get())
            .filter(t -> t.getTokenHash().equals(invocation.getArgument(0))));

    PasswordPolicy passwordPolicy = new PasswordPolicy();
    requestResetService = new RequestPasswordResetService(
        accountRepository, accountTokenRepository, challengeRepository, crypto, emailSender, POLICY, clock);
    resetPasswordService = new ResetPasswordService(
        accountRepository, accountTokenRepository, passwordService, passwordPolicy, crypto, sessionRevoker, clock);
    changePasswordService = new ChangePasswordService(accountRepository, passwordService, passwordPolicy);
  }

  private String lastSentCode() {
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(emailSender, org.mockito.Mockito.atLeastOnce())
        .sendPasswordResetCode(eq(EMAIL), eq(FULL_NAME), code.capture(), eq(POLICY.codeTtl()));
    return code.getValue();
  }

  private static ErrorCode errorCodeOf(Throwable error) {
    return ((BaseBusinessException) error).getErrorCode();
  }

  @Test
  @DisplayName("IAM-PWD-001: Yêu cầu mã với email không tồn tại trả phản hồi trung tính, không gửi thư (NFR-06)")
  void requestCode_unknownEmail_isNeutral() {
    OtpChallengeView view = requestResetService.requestCode(UNKNOWN_EMAIL);

    assertThat(view.email()).isEqualTo(UNKNOWN_EMAIL);
    assertThat(view.codeExpiresAt()).isEqualTo(NOW.plus(POLICY.codeTtl()));
    assertThat(view.resendAvailableAt()).isEqualTo(NOW.plus(POLICY.resendCooldown()));
    verify(emailSender, never()).sendPasswordResetCode(any(), any(), any(), any());
  }

  @Test
  @DisplayName("IAM-PWD-002: Tài khoản bị vô hiệu hóa không nhận mã nhưng phản hồi vẫn trung tính")
  void requestCode_deactivatedAccount_isNeutral() {
    account.updateStatus(AccountStatus.DEACTIVATED);

    requestResetService.requestCode(EMAIL);

    verify(emailSender, never()).sendPasswordResetCode(any(), any(), any(), any());
  }

  @Test
  @DisplayName("IAM-PWD-003: Yêu cầu lại trong thời gian chờ không gửi thêm thư và không báo lỗi lộ email")
  void requestCode_withinCooldown_isSilentlyThrottled() {
    requestResetService.requestCode(EMAIL);
    clock.advance(Duration.ofSeconds(10));

    requestResetService.requestCode(EMAIL);

    verify(emailSender, times(1)).sendPasswordResetCode(any(), any(), any(), any());
  }

  @Test
  @DisplayName("IAM-PWD-004: Nhập đúng mã OTP được cấp token đặt lại dùng một lần (BR-05)")
  void verifyCode_correct_issuesResetToken() {
    requestResetService.requestCode(EMAIL);

    PasswordResetGrant grant = requestResetService.verifyCode(EMAIL, lastSentCode());

    assertThat(grant.resetToken()).hasSize(64);
    assertThat(grant.expiresAt()).isEqualTo(NOW.plus(TokenType.RESET_PASSWORD.getDefaultTtl()));
    assertThat(storedToken.get().getTokenHash()).isEqualTo(crypto.hashToken(grant.resetToken()));
    verify(accountTokenRepository).invalidatePreviousTokens(account, TokenType.RESET_PASSWORD, NOW);
  }

  @Test
  @DisplayName("IAM-PWD-005: Nhập sai mã hoặc email không có yêu cầu đều nhận cùng loại lỗi INVALID_TOKEN")
  void verifyCode_wrongOrUnknown_isRejected() {
    requestResetService.requestCode(EMAIL);
    String wrongCode = lastSentCode().equals("000000") ? "111111" : "000000";

    assertThatThrownBy(() -> requestResetService.verifyCode(EMAIL, wrongCode))
        .satisfies(error -> assertThat(errorCodeOf(error)).isEqualTo(ErrorCode.INVALID_TOKEN));
    assertThatThrownBy(() -> requestResetService.verifyCode(UNKNOWN_EMAIL, "123456"))
        .satisfies(error -> assertThat(errorCodeOf(error)).isEqualTo(ErrorCode.INVALID_TOKEN));
    assertThat(storedChallenge.get().getFailedAttempts()).isEqualTo(1);
    verify(accountTokenRepository, never()).save(any());
  }

  @Test
  @DisplayName("IAM-PWD-006: Đặt mật khẩu mới: tiêu thụ token, mở khóa tài khoản và thu hồi mọi phiên cũ")
  void resetPassword_success_consumesTokenUnlocksAndRevokesSessions() {
    for (int i = 0; i < Account.MAX_FAILED_LOGIN_ATTEMPTS; i++) {
      account.recordLoginFailure(NOW);
    }
    assertThat(account.isLocked(NOW)).isTrue();
    requestResetService.requestCode(EMAIL);
    PasswordResetGrant grant = requestResetService.verifyCode(EMAIL, lastSentCode());

    Account updated = resetPasswordService.resetPassword(grant.resetToken(), NEW_PASSWORD);

    assertThat(updated.getPasswordHash()).isEqualTo(NEW_HASH);
    assertThat(updated.isLocked(NOW)).isFalse();
    assertThat(storedToken.get().isUsed()).isTrue();
    verify(sessionRevoker).revokeAllSessions(EMAIL);

    assertThatThrownBy(() -> resetPasswordService.resetPassword(grant.resetToken(), NEW_PASSWORD))
        .satisfies(error -> assertThat(errorCodeOf(error)).isEqualTo(ErrorCode.TOKEN_ALREADY_USED));
  }

  @Test
  @DisplayName("IAM-PWD-007: Token đặt lại quá 30 phút bị từ chối")
  void resetPassword_expiredToken_isRejected() {
    requestResetService.requestCode(EMAIL);
    PasswordResetGrant grant = requestResetService.verifyCode(EMAIL, lastSentCode());
    clock.advance(TokenType.RESET_PASSWORD.getDefaultTtl());

    assertThatThrownBy(() -> resetPasswordService.resetPassword(grant.resetToken(), NEW_PASSWORD))
        .satisfies(error -> assertThat(errorCodeOf(error)).isEqualTo(ErrorCode.TOKEN_EXPIRED));
    verify(sessionRevoker, never()).revokeAllSessions(any());
  }

  @Test
  @DisplayName("IAM-PWD-008: Mật khẩu mới trùng mật khẩu cũ bị từ chối")
  void resetPassword_reusedPassword_isRejected() {
    requestResetService.requestCode(EMAIL);
    PasswordResetGrant grant = requestResetService.verifyCode(EMAIL, lastSentCode());
    when(passwordService.verify(OLD_HASH, NEW_PASSWORD)).thenReturn(true);

    assertThatThrownBy(() -> resetPasswordService.resetPassword(grant.resetToken(), NEW_PASSWORD))
        .satisfies(error -> assertThat(errorCodeOf(error)).isEqualTo(ErrorCode.PASSWORD_REUSE_FORBIDDEN));
  }

  @Test
  @DisplayName("IAM-PWD-009: Đổi mật khẩu từ chối khi mật khẩu hiện tại không đúng")
  void changePassword_wrongCurrentPassword_isRejected() {
    when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
    when(passwordService.verify(OLD_HASH, "WrongCurrentPass")).thenReturn(false);

    assertThatThrownBy(() -> changePasswordService.changePassword(account.getId(), "WrongCurrentPass", NEW_PASSWORD))
        .satisfies(error -> assertThat(errorCodeOf(error)).isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD));
  }

  @Test
  @DisplayName("IAM-PWD-010: Đổi mật khẩu từ chối khi mật khẩu mới trùng mật khẩu hiện tại")
  void changePassword_samePassword_isRejected() {
    when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
    when(passwordService.verify(OLD_HASH, "SamePassword123@")).thenReturn(true);

    assertThatThrownBy(() -> changePasswordService.changePassword(account.getId(), "SamePassword123@", "SamePassword123@"))
        .satisfies(error -> assertThat(errorCodeOf(error)).isEqualTo(ErrorCode.PASSWORD_REUSE_FORBIDDEN));
  }
}

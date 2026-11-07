package vn.edufit.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.application.OtpPolicy;
import vn.edufit.iam.infra.persistence.EmailOtpChallenge;
import vn.edufit.iam.infra.persistence.OtpPurpose;
import vn.edufit.iam.infra.security.TokenCryptoService;
import vn.edufit.shared.exception.BaseBusinessException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

@DisplayName("Kiểm thử Đơn vị Thử thách OTP qua email (EmailOtpChallenge)")
class EmailOtpChallengeTest {

  private static final OtpPolicy POLICY = new OtpPolicy(
      Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 3, Duration.ofHours(1));
  private static final Instant T0 = Instant.parse("2026-11-04T08:00:00Z");
  private static final String EMAIL = "  New.User@EduFit.Test ";
  private static final String CODE = "123456";
  private static final String WRONG_CODE = "654321";

  private final TokenCryptoService crypto = new TokenCryptoService();
  private EmailOtpChallenge challenge;

  @BeforeEach
  void setUp() {
    challenge = EmailOtpChallenge.forRegistration(EMAIL, "$argon2id$hash", " Học Sinh ", AccountRole.STUDENT);
    challenge.issueCode(hash(CODE), T0, POLICY);
  }

  private String hash(String code) {
    return crypto.hashOtpCode(challenge.getId(), code);
  }

  @Test
  @DisplayName("OTP-001: Phát hành mã chuẩn hóa email, đặt hạn theo chính sách và thời điểm được gửi lại")
  void issueCode_setsExpiryAndResendWindow() {
    assertThat(challenge.getPurpose()).isEqualTo(OtpPurpose.REGISTRATION);
    assertThat(challenge.getEmail()).isEqualTo("new.user@edufit.test");
    assertThat(challenge.getFullName()).isEqualTo("Học Sinh");
    assertThat(challenge.getExpiresAt()).isEqualTo(T0.plus(POLICY.codeTtl()));
    assertThat(challenge.resendAvailableAt(POLICY)).isEqualTo(T0.plus(POLICY.resendCooldown()));
    assertThat(challenge.getCodeHash()).isNotEqualTo(CODE).hasSize(64);
  }

  @Test
  @DisplayName("OTP-002: Nhập đúng mã thì tiêu thụ thử thách, không dùng lại được")
  void verify_correctCode_consumesOnce() {
    assertThat(challenge.verify(hash(CODE), T0.plusSeconds(30), POLICY)).isTrue();
    assertThat(challenge.getConsumedAt()).isEqualTo(T0.plusSeconds(30));

    assertThatThrownBy(() -> challenge.verify(hash(CODE), T0.plusSeconds(31), POLICY))
        .isInstanceOf(InvalidOperationException.class)
        .extracting(e -> ((BaseBusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TOKEN_ALREADY_USED);
  }

  @Test
  @DisplayName("OTP-003: Nhập sai tăng bộ đếm; vượt số lần cho phép thì khóa mã kể cả khi nhập đúng")
  void verify_wrongCodeTooManyTimes_locksChallenge() {
    for (int i = 0; i < POLICY.maxFailedAttempts(); i++) {
      assertThat(challenge.verify(hash(WRONG_CODE), T0.plusSeconds(10), POLICY)).isFalse();
    }
    assertThat(challenge.remainingAttempts(POLICY)).isZero();

    assertThatThrownBy(() -> challenge.verify(hash(CODE), T0.plusSeconds(11), POLICY))
        .extracting(e -> ((BaseBusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
  }

  @Test
  @DisplayName("OTP-004: Mã quá hạn bị từ chối")
  void verify_expiredCode_isRejected() {
    assertThatThrownBy(() -> challenge.verify(hash(CODE), T0.plus(POLICY.codeTtl()), POLICY))
        .extracting(e -> ((BaseBusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TOKEN_EXPIRED);
  }

  @Test
  @DisplayName("OTP-005: Gửi lại trước khi hết thời gian chờ bị chặn")
  void issueCode_beforeCooldown_isRateLimited() {
    assertThat(challenge.canIssueCode(T0.plusSeconds(59), POLICY)).isFalse();
    assertThatThrownBy(() -> challenge.issueCode(hash(CODE), T0.plusSeconds(59), POLICY))
        .extracting(e -> ((BaseBusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
  }

  @Test
  @DisplayName("OTP-006: Gửi lại sau thời gian chờ thay mã mới, mã cũ không còn đúng và bộ đếm sai được đặt lại")
  void issueCode_afterCooldown_rotatesCode() {
    challenge.verify(hash(WRONG_CODE), T0.plusSeconds(5), POLICY);
    Instant resendAt = T0.plus(POLICY.resendCooldown());

    challenge.issueCode(hash("000042"), resendAt, POLICY);

    assertThat(challenge.getFailedAttempts()).isZero();
    assertThat(challenge.getSendCount()).isEqualTo(2);
    assertThat(challenge.verify(hash(CODE), resendAt.plusSeconds(1), POLICY)).isFalse();
    assertThat(challenge.verify(hash("000042"), resendAt.plusSeconds(2), POLICY)).isTrue();
  }

  @Test
  @DisplayName("OTP-007: Vượt số lần gửi trong cửa sổ thì bị chặn; sang cửa sổ mới thì được gửi tiếp")
  void issueCode_exceedingWindowCap_isBlockedUntilWindowEnds() {
    Instant now = T0;
    for (int sent = 1; sent < POLICY.maxSendsPerWindow(); sent++) {
      now = now.plus(POLICY.resendCooldown());
      challenge.issueCode(hash(CODE), now, POLICY);
    }
    Instant blockedAt = now.plus(POLICY.resendCooldown());
    assertThat(challenge.canIssueCode(blockedAt, POLICY)).isFalse();

    Instant nextWindow = T0.plus(POLICY.sendWindow());
    challenge.issueCode(hash(CODE), nextWindow, POLICY);
    assertThat(challenge.getSendCount()).isEqualTo(1);
    assertThat(challenge.getSendWindowStartedAt()).isEqualTo(nextWindow);
  }

  @Test
  @DisplayName("OTP-008: Không cho phép tự đăng ký vai trò ADMIN qua thử thách OTP")
  void forRegistration_admin_isForbidden() {
    assertThatThrownBy(() -> EmailOtpChallenge.forRegistration(EMAIL, "$argon2id$hash", "Admin", AccountRole.ADMIN))
        .extracting(e -> ((BaseBusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FORBIDDEN);
  }

  @Test
  @DisplayName("OTP-009: Mã OTP luôn gồm 6 chữ số và băm khác nhau giữa hai thử thách")
  void otpCode_formatAndSaltedHash() {
    for (int i = 0; i < 200; i++) {
      assertThat(crypto.generateOtpCode()).matches("^[0-9]{6}$");
    }
    EmailOtpChallenge other = EmailOtpChallenge.forPasswordReset(EMAIL);
    assertThat(crypto.hashOtpCode(other.getId(), CODE)).isNotEqualTo(hash(CODE));
  }

  @Test
  @DisplayName("OTP-010: Cấu hình chính sách OTP sai bị từ chối ngay khi khởi động")
  void otpPolicy_invalidConfiguration_failsFast() {
    assertThatThrownBy(() -> new OtpPolicy(Duration.ofMinutes(1), 5, Duration.ofMinutes(2), 3, Duration.ofHours(1)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new OtpPolicy(Duration.ofMinutes(10), 0, Duration.ofSeconds(60), 3, Duration.ofHours(1)))
        .isInstanceOf(IllegalStateException.class);
  }
}

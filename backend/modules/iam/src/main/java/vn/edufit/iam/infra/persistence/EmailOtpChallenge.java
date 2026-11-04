package vn.edufit.iam.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.application.OtpPolicy;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Thử thách mã OTP 6 số gửi qua email (bảng {@code email_otp_challenge}).
 *
 * <p>Entity tự bảo vệ các bất biến bảo mật:
 * <ul>
 *   <li>Chỉ lưu {@code code_hash}; mã thô không bao giờ được lưu.</li>
 *   <li>{@link #issueCode}: áp dụng thời gian chờ gửi lại và giới hạn số lần gửi trong cửa sổ thời gian.</li>
 *   <li>{@link #verify}: giới hạn số lần nhập sai, kiểm tra hạn và tiêu thụ mã đúng một lần.</li>
 * </ul>
 */
@Entity
@Table(name = "email_otp_challenge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailOtpChallenge implements Persistable<UUID> {

  @Id
  @Column(name = "challenge_id")
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", nullable = false, length = 30)
  private OtpPurpose purpose;

  @Column(name = "email", nullable = false, length = 255)
  private String email;

  @Column(name = "code_hash", nullable = false, length = 64)
  private String codeHash;

  @Column(name = "failed_attempts", nullable = false)
  private int failedAttempts;

  @Column(name = "send_count", nullable = false)
  private int sendCount;

  @Column(name = "send_window_started_at", nullable = false)
  private Instant sendWindowStartedAt;

  @Column(name = "last_sent_at", nullable = false)
  private Instant lastSentAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(name = "full_name", length = 150)
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", length = 20)
  private AccountRole role;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Transient
  private boolean newEntity = true;

  /**
   * Tạo thử thách đăng ký chờ xác thực email. Phải gọi {@link #issueCode} trước khi lưu.
   */
  public static EmailOtpChallenge forRegistration(
      String email, String passwordHash, String fullName, AccountRole role) {
    EmailOtpChallenge challenge = newChallenge(OtpPurpose.REGISTRATION, email);
    challenge.updateRegistrationDetails(passwordHash, fullName, role);
    return challenge;
  }

  /**
   * Tạo thử thách quên mật khẩu. Phải gọi {@link #issueCode} trước khi lưu.
   */
  public static EmailOtpChallenge forPasswordReset(String email) {
    return newChallenge(OtpPurpose.PASSWORD_RESET, email);
  }

  private static EmailOtpChallenge newChallenge(OtpPurpose purpose, String email) {
    Objects.requireNonNull(email, "Email không được để trống");
    EmailOtpChallenge challenge = new EmailOtpChallenge();
    challenge.id = UUID.randomUUID();
    challenge.purpose = purpose;
    challenge.email = Account.normalizeEmail(email);
    return challenge;
  }

  /**
   * Cập nhật thông tin đăng ký khi người dùng đăng ký lại cùng email trước khi xác thực.
   */
  public void updateRegistrationDetails(String passwordHash, String fullName, AccountRole role) {
    if (purpose != OtpPurpose.REGISTRATION) {
      throw new IllegalStateException("Chỉ thử thách đăng ký mới mang thông tin tài khoản");
    }
    Objects.requireNonNull(passwordHash, "Password hash không được để trống");
    Objects.requireNonNull(fullName, "Họ tên không được để trống");
    Objects.requireNonNull(role, "Vai trò không được để trống");
    if (role == AccountRole.ADMIN) {
      throw new InvalidOperationException(
          ErrorCode.FORBIDDEN,
          "Không được phép tự đăng ký tài khoản với quyền Quản trị viên (ADMIN)"
      );
    }
    this.passwordHash = passwordHash;
    this.fullName = fullName.trim();
    this.role = role;
  }

  /**
   * Kiểm tra có được phép gửi mã mới tại thời điểm {@code now} hay không (không thay đổi trạng thái).
   */
  public boolean canIssueCode(Instant now, OtpPolicy policy) {
    return issueBlockReason(now, policy) == null;
  }

  /**
   * Phát hành mã mới: thay mã cũ, đặt lại số lần nhập sai và gia hạn hiệu lực.
   *
   * @param newCodeHash mã băm của mã mới, tính bằng {@code SHA-256(id + ":" + code)}
   * @throws InvalidOperationException {@link ErrorCode#RATE_LIMIT_EXCEEDED} nếu chưa hết thời gian chờ
   *                                   hoặc đã vượt số lần gửi trong cửa sổ thời gian
   */
  public void issueCode(String newCodeHash, Instant now, OtpPolicy policy) {
    Objects.requireNonNull(newCodeHash, "Mã băm không được để trống");
    String blockReason = issueBlockReason(now, policy);
    if (blockReason != null) {
      throw new InvalidOperationException(ErrorCode.RATE_LIMIT_EXCEEDED, blockReason);
    }
    if (sendWindowStartedAt == null || !now.isBefore(sendWindowStartedAt.plus(policy.sendWindow()))) {
      sendWindowStartedAt = now;
      sendCount = 0;
    }
    codeHash = newCodeHash.toLowerCase();
    sendCount++;
    lastSentAt = now;
    expiresAt = now.plus(policy.codeTtl());
    failedAttempts = 0;
  }

  private String issueBlockReason(Instant now, OtpPolicy policy) {
    if (consumedAt != null) {
      return "Yêu cầu xác thực này đã hoàn tất";
    }
    if (lastSentAt == null) {
      return null;
    }
    Instant resendAt = resendAvailableAt(policy);
    if (now.isBefore(resendAt)) {
      long seconds = Math.max(1, Duration.between(now, resendAt).toSeconds());
      return "Vui lòng chờ " + seconds + " giây trước khi yêu cầu mã mới";
    }
    Instant windowEnd = sendWindowStartedAt.plus(policy.sendWindow());
    if (now.isBefore(windowEnd) && sendCount >= policy.maxSendsPerWindow()) {
      long minutes = Math.max(1, Duration.between(now, windowEnd).toMinutes());
      return "Bạn đã yêu cầu quá nhiều mã xác thực. Vui lòng thử lại sau " + minutes + " phút";
    }
    return null;
  }

  /**
   * So khớp mã người dùng nhập.
   *
   * @param candidateHash mã băm của mã người dùng nhập, tính cùng công thức với {@link #issueCode}
   * @return {@code true} và tiêu thụ thử thách nếu đúng; {@code false} và tăng số lần sai nếu sai
   * @throws InvalidOperationException khi thử thách đã dùng, đã hết hạn hoặc đã nhập sai quá số lần
   */
  public boolean verify(String candidateHash, Instant now, OtpPolicy policy) {
    Objects.requireNonNull(candidateHash, "Mã băm không được để trống");
    if (consumedAt != null) {
      throw new InvalidOperationException(ErrorCode.TOKEN_ALREADY_USED, "Mã xác thực này đã được sử dụng");
    }
    if (failedAttempts >= policy.maxFailedAttempts()) {
      throw new InvalidOperationException(
          ErrorCode.RATE_LIMIT_EXCEEDED,
          "Bạn đã nhập sai quá " + policy.maxFailedAttempts() + " lần. Vui lòng yêu cầu mã mới"
      );
    }
    if (!now.isBefore(expiresAt)) {
      throw new InvalidOperationException(
          ErrorCode.TOKEN_EXPIRED, "Mã xác thực đã hết hạn. Vui lòng yêu cầu mã mới");
    }
    boolean matches = MessageDigest.isEqual(
        codeHash.getBytes(StandardCharsets.US_ASCII),
        candidateHash.toLowerCase().getBytes(StandardCharsets.US_ASCII)
    );
    if (!matches) {
      failedAttempts++;
      return false;
    }
    consumedAt = now;
    return true;
  }

  /** Số lần nhập sai còn lại cho mã hiện tại. */
  public int remainingAttempts(OtpPolicy policy) {
    return Math.max(0, policy.maxFailedAttempts() - failedAttempts);
  }

  /** Thời điểm sớm nhất được yêu cầu gửi lại mã. */
  public Instant resendAvailableAt(OtpPolicy policy) {
    return lastSentAt.plus(policy.resendCooldown());
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  void markPersisted() {
    this.newEntity = false;
  }

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}

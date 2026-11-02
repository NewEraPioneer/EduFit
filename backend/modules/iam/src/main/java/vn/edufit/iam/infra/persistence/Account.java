package vn.edufit.iam.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * JPA Entity đại diện cho bảng {@code accounts} trong cơ sở dữ liệu.
 *
 * <p>Entity tự bảo vệ tính toàn vẹn của trạng thái thông qua các phương thức hành vi:
 * <ul>
 *   <li>{@link #recordLoginFailure(Instant)}: Đếm lỗi đăng nhập và tự động khóa 15 phút khi sai liên tiếp 5 lần (BR-06).</li>
 *   <li>{@link #recordSuccessfulLogin(Instant)}: Xóa bộ đếm lỗi và ghi nhận thời điểm đăng nhập thành công.</li>
 * </ul>
 */
@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

  public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
  public static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "user_id")
  private UUID id;

  @Column(name = "email", nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(name = "avatar_url", length = 500)
  private String avatarUrl;

  @Column(name = "full_name", nullable = false, length = 150)
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private AccountRole role;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private AccountStatus status;

  @Column(name = "failed_attempts", nullable = false)
  private int failedLoginAttempts;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /**
   * Khởi tạo tài khoản mới mặc định ở trạng thái hoạt động {@link AccountStatus#ACTIVE}.
   *
   * @param email email của tài khoản (sẽ được chuẩn hóa trim và lowercase)
   * @param passwordHash chuỗi băm mật khẩu Argon2
   * @param fullName họ và tên
   * @param role vai trò (STUDENT, PARENT, TUTOR)
   */
  public static Account create(String email, String passwordHash, String fullName, AccountRole role) {
    Objects.requireNonNull(email, "Email không được để trống");
    Objects.requireNonNull(passwordHash, "Password hash không được để trống");
    Objects.requireNonNull(fullName, "Họ tên không được để trống");
    Objects.requireNonNull(role, "Vai trò không được để trống");

    if (role == AccountRole.ADMIN) {
      throw new InvalidOperationException(
          ErrorCode.FORBIDDEN,
          "Không được phép tự đăng ký tài khoản với quyền Quản trị viên (ADMIN)"
      );
    }

    Account account = new Account();
    account.email = normalizeEmail(email);
    account.passwordHash = passwordHash;
    account.fullName = fullName.trim();
    account.role = role;
    account.status = AccountStatus.ACTIVE;
    account.failedLoginAttempts = 0;
    return account;
  }

  /**
   * Khởi tạo tài khoản mới từ OAuth2 (không có mật khẩu ban đầu).
   */
  public static Account createOAuthAccount(String email, String fullName, AccountRole role, String avatarUrl) {
    Objects.requireNonNull(email, "Email không được để trống");
    Objects.requireNonNull(fullName, "Họ tên không được để trống");
    Objects.requireNonNull(role, "Vai trò không được để trống");

    if (role == AccountRole.ADMIN) {
      throw new InvalidOperationException(
          ErrorCode.FORBIDDEN,
          "Không được phép tự đăng ký tài khoản với quyền Quản trị viên (ADMIN)"
      );
    }

    Account account = new Account();
    account.email = normalizeEmail(email);
    account.passwordHash = null;
    account.fullName = fullName.trim();
    account.role = role;
    account.status = AccountStatus.ACTIVE;
    account.avatarUrl = avatarUrl;
    account.failedLoginAttempts = 0;
    return account;
  }

  /**
   * Cập nhật ảnh đại diện tài khoản.
   */
  public void updateAvatar(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  /**
   * Cập nhật trạng thái hoạt động của tài khoản (ACTIVE / DEACTIVATED).
   */
  public void updateStatus(AccountStatus status) {
    this.status = Objects.requireNonNull(status, "Trạng thái không được để trống");
  }

  /**
   * Khởi tạo tài khoản mới ở trạng thái ACTIVE (UC1.1).
   */
  public static Account createActive(String email, String passwordHash, String fullName, AccountRole role) {
    return create(email, passwordHash, fullName, role);
  }

  /**
   * Phương thức tạo dành cho các trường hợp kiểm thử hoặc khởi tạo dữ liệu mẫu với ID có sẵn.
   */
  public static Account of(
      UUID id,
      String email,
      String passwordHash,
      String fullName,
      AccountRole role,
      AccountStatus status
  ) {
    return of(id, email, passwordHash, fullName, role, status, null);
  }

  public static Account of(
      UUID id,
      String email,
      String passwordHash,
      String fullName,
      AccountRole role,
      AccountStatus status,
      String avatarUrl
  ) {
    Account account = new Account();
    account.id = id;
    account.email = normalizeEmail(email);
    account.passwordHash = passwordHash;
    account.fullName = fullName != null ? fullName.trim() : "";
    account.role = role;
    account.status = status;
    account.avatarUrl = avatarUrl;
    account.failedLoginAttempts = 0;
    return account;
  }

  /**
   * Kiểm tra xem tài khoản có đang trong thời gian bị tạm khóa do đăng nhập sai hay không (BR-06).
   */
  public boolean isLocked(Instant now) {
    if (lockedUntil == null) {
      return false;
    }
    return now.isBefore(lockedUntil);
  }

  /**
   * Kiểm tra xem tài khoản có đủ điều kiện để thực hiện đăng nhập hay không.
   */
  public boolean canAuthenticate(Instant now) {
    return status == AccountStatus.ACTIVE && !isLocked(now);
  }

  /**
   * Ghi nhận một lần đăng nhập sai (BR-06).
   * Khi sai liên tiếp 5 lần ➔ khóa tài khoản 15 phút.
   */
  public void recordLoginFailure(Instant now) {
    // Nếu trước đó đã hết hạn khóa, reset lại bộ đếm để bắt đầu chu kỳ đếm mới
    if (lockedUntil != null && !now.isBefore(lockedUntil)) {
      this.failedLoginAttempts = 0;
      this.lockedUntil = null;
    }

    this.failedLoginAttempts++;
    if (this.failedLoginAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
      this.lockedUntil = now.plus(LOCKOUT_DURATION);
    }
  }

  /**
   * Ghi nhận đăng nhập thành công. Xóa sạch bộ đếm lỗi và thời gian khóa (BR-06).
   */
  public void recordSuccessfulLogin(Instant now) {
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
    this.lastLoginAt = now;
  }

  /**
   * Cập nhật mật khẩu mới (chuỗi băm Argon2).
   */
  public void changePassword(String newPasswordHash) {
    Objects.requireNonNull(newPasswordHash, "Mật khẩu mới không được để trống");
    this.passwordHash = newPasswordHash;
  }

  /**
   * Chuẩn hóa email theo BR-01: cắt khoảng trắng và đưa về chữ thường.
   */
  public static String normalizeEmail(String email) {
    if (email == null) {
      return "";
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    if (this.createdAt == null) {
      this.createdAt = now;
    }
    if (this.updatedAt == null) {
      this.updatedAt = now;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}

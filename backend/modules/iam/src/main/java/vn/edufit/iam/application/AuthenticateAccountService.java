package vn.edufit.iam.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.infra.persistence.Account;
import vn.edufit.iam.infra.persistence.AccountRepository;
import vn.edufit.iam.infra.security.Argon2PasswordService;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Ca sử dụng Xác thực đăng nhập người dùng (UC1.2).
 *
 * <p>Quy tắc bảo mật:
 * <ul>
 *   <li><b>BR-06:</b> Bộ đếm lỗi đăng nhập sai liên tiếp: sai 5 lần khóa 15 phút. Đăng nhập đúng xóa bộ đếm.</li>
 *   <li><b>NFR-01:</b> So khớp mật khẩu qua giải thuật Argon2id an toàn.</li>
 *   <li><b>NFR-06:</b> Chống dò quét: Sai email hay sai mật khẩu đều trả thông báo chung {@code INVALID_CREDENTIALS}.</li>
 * </ul>
 */
@Service
public class AuthenticateAccountService {

  // Mật khẩu giả để thực hiện dummy hash verification tránh tấn công Timing Attack khi email không tồn tại
  private static final String DUMMY_HASH =
      "$argon2id$v=19$m=65536,t=3,p=1$c29tZXNhbHQxMjM0NTY3OA$9Hk7iK5kQ5ZkZ1j8e4f1a2b3c4d5e6f7a8b9c0d1e2f";

  private final AccountRepository accountRepository;
  private final Argon2PasswordService passwordService;
  private final Clock clock;

  public AuthenticateAccountService(
      AccountRepository accountRepository,
      Argon2PasswordService passwordService,
      Clock clock
  ) {
    this.accountRepository = accountRepository;
    this.passwordService = passwordService;
    this.clock = clock;
  }

  /**
   * Xác thực thông tin đăng nhập của người dùng.
   *
   * @param email    email đăng nhập
   * @param password mật khẩu thô
   * @return {@link Account} đã xác thực thành công
   */
  @Transactional
  public Account authenticate(String email, String password) {
    Objects.requireNonNull(email, "Email không được để trống");
    Objects.requireNonNull(password, "Mật khẩu không được để trống");

    Instant now = clock.instant();
    String normalizedEmail = Account.normalizeEmail(email);

    Account account = accountRepository.findByEmail(normalizedEmail).orElse(null);

    // Nếu email không tồn tại: Chạy verify giả lập để cân bằng thời gian phản hồi (chống Timing Attack - NFR-06)
    if (account == null) {
      try {
        passwordService.verify(DUMMY_HASH, password);
      } catch (Exception ignored) {
        // bỏ qua exception của dummy verify
      }
      throw new InvalidOperationException(
          ErrorCode.INVALID_CREDENTIALS,
          "Email hoặc mật khẩu không chính xác"
      );
    }

    // 1. Kiểm tra tài khoản có đang bị khóa 15 phút không (BR-06)
    if (account.isLocked(now)) {
      throw new InvalidOperationException(
          ErrorCode.ACCOUNT_LOCKED,
          "Tài khoản của bạn tạm thời bị khóa do nhập sai mật khẩu quá 5 lần liên tiếp. Vui lòng thử lại sau 15 phút."
      );
    }

    // 2. Kiểm tra trạng thái tài khoản (BR-04)
    if (account.getStatus() != AccountStatus.ACTIVE) {
      throw new InvalidOperationException(
          ErrorCode.USER_NOT_ACTIVE,
          "Tài khoản chưa được kích hoạt qua email hoặc đã bị vô hiệu hóa"
      );
    }

    // 3. So khớp mật khẩu bằng Argon2id
    boolean passwordMatches = account.getPasswordHash() != null
        && passwordService.verify(account.getPasswordHash(), password);

    if (!passwordMatches) {
      account.recordLoginFailure(now);
      accountRepository.save(account);

      if (account.isLocked(now)) {
        throw new InvalidOperationException(
            ErrorCode.ACCOUNT_LOCKED,
            "Tài khoản của bạn đã bị khóa 15 phút do nhập sai mật khẩu 5 lần liên tiếp"
        );
      }

      throw new InvalidOperationException(
          ErrorCode.INVALID_CREDENTIALS,
          "Email hoặc mật khẩu không chính xác"
      );
    }

    // 4. Đăng nhập thành công: xóa bộ đếm lỗi và cập nhật lastLoginAt (BR-06)
    account.recordSuccessfulLogin(now);
    return accountRepository.save(account);
  }
}

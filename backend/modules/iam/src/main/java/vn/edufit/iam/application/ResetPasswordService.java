package vn.edufit.iam.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.application.port.SessionRevoker;
import vn.edufit.iam.infra.persistence.Account;
import vn.edufit.iam.infra.persistence.AccountRepository;
import vn.edufit.iam.infra.persistence.AccountToken;
import vn.edufit.iam.infra.persistence.AccountTokenRepository;
import vn.edufit.iam.infra.persistence.TokenType;
import vn.edufit.iam.infra.security.Argon2PasswordService;
import vn.edufit.iam.infra.security.TokenCryptoService;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Ca sử dụng Đặt mật khẩu mới bằng token cấp sau khi xác thực mã OTP (UC1.3).
 *
 * <p>Quy tắc:
 * <ul>
 *   <li><b>BR-02:</b> Mật khẩu mới phải vượt qua {@link PasswordPolicy}.</li>
 *   <li><b>BR-05:</b> Tiêu thụ token đặt lại (dùng một lần, hạn 30 phút).</li>
 *   <li><b>BR-06:</b> Đặt lại thành công xóa bộ đếm đăng nhập sai và mở khóa tài khoản.</li>
 *   <li><b>NFR-01:</b> Băm mật khẩu mới bằng Argon2id trước khi lưu.</li>
 *   <li>Thu hồi mọi phiên đang đăng nhập bằng mật khẩu cũ; tầng web tạo phiên mới cho người vừa đặt lại.</li>
 * </ul>
 */
@Service
public class ResetPasswordService {

  private final AccountRepository accountRepository;
  private final AccountTokenRepository accountTokenRepository;
  private final Argon2PasswordService passwordService;
  private final PasswordPolicy passwordPolicy;
  private final TokenCryptoService tokenCryptoService;
  private final SessionRevoker sessionRevoker;
  private final Clock clock;

  public ResetPasswordService(
      AccountRepository accountRepository,
      AccountTokenRepository accountTokenRepository,
      Argon2PasswordService passwordService,
      PasswordPolicy passwordPolicy,
      TokenCryptoService tokenCryptoService,
      SessionRevoker sessionRevoker,
      Clock clock
  ) {
    this.accountRepository = accountRepository;
    this.accountTokenRepository = accountTokenRepository;
    this.passwordService = passwordService;
    this.passwordPolicy = passwordPolicy;
    this.tokenCryptoService = tokenCryptoService;
    this.sessionRevoker = sessionRevoker;
    this.clock = clock;
  }

  /**
   * Đặt mật khẩu mới thông qua token đặt lại.
   *
   * @param rawToken    token thô nhận được sau khi xác thực mã OTP
   * @param newPassword mật khẩu mới
   * @return tài khoản đã cập nhật, sẵn sàng để tầng web tạo phiên đăng nhập mới
   */
  @Transactional
  public Account resetPassword(String rawToken, String newPassword) {
    Objects.requireNonNull(rawToken, "Mã token không được để trống");
    Objects.requireNonNull(newPassword, "Mật khẩu mới không được để trống");

    passwordPolicy.validate(newPassword);

    Instant now = clock.instant();
    AccountToken token = accountTokenRepository
        .findByTokenHashAndTokenType(tokenCryptoService.hashToken(rawToken), TokenType.RESET_PASSWORD)
        .orElseThrow(() -> new InvalidOperationException(
            ErrorCode.INVALID_TOKEN,
            "Phiên đặt lại mật khẩu không hợp lệ. Vui lòng yêu cầu mã xác thực mới."
        ));

    if (token.isUsed()) {
      throw new InvalidOperationException(
          ErrorCode.TOKEN_ALREADY_USED,
          "Phiên đặt lại mật khẩu đã được sử dụng. Vui lòng yêu cầu mã xác thực mới."
      );
    }
    if (token.isExpired(now)) {
      throw new InvalidOperationException(
          ErrorCode.TOKEN_EXPIRED,
          "Phiên đặt lại mật khẩu đã hết hạn (quá 30 phút). Vui lòng yêu cầu mã xác thực mới."
      );
    }

    Account account = token.getAccount();
    if (account.getStatus() != AccountStatus.ACTIVE) {
      throw new InvalidOperationException(ErrorCode.USER_NOT_ACTIVE, "Tài khoản đã bị vô hiệu hóa");
    }
    if (account.getPasswordHash() != null && passwordService.verify(account.getPasswordHash(), newPassword)) {
      throw new InvalidOperationException(
          ErrorCode.PASSWORD_REUSE_FORBIDDEN,
          "Mật khẩu mới không được trùng với mật khẩu gần nhất"
      );
    }

    token.consume(now);
    account.changePassword(passwordService.hash(newPassword));
    account.recordSuccessfulLogin(now);
    Account saved = accountRepository.save(account);
    sessionRevoker.revokeAllSessions(saved.getEmail());
    return saved;
  }
}

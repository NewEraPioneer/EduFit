package vn.edufit.iam.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.application.port.AccountEmailSender;
import vn.edufit.iam.application.port.EmailDeliveryException;
import vn.edufit.iam.infra.persistence.Account;
import vn.edufit.iam.infra.persistence.AccountRepository;
import vn.edufit.iam.infra.persistence.AccountToken;
import vn.edufit.iam.infra.persistence.AccountTokenRepository;
import vn.edufit.iam.infra.persistence.EmailOtpChallenge;
import vn.edufit.iam.infra.persistence.EmailOtpChallengeRepository;
import vn.edufit.iam.infra.persistence.OtpPurpose;
import vn.edufit.iam.infra.persistence.TokenType;
import vn.edufit.iam.infra.security.TokenCryptoService;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Ca sử dụng Quên mật khẩu bằng mã OTP qua email (UC1.3).
 *
 * <p>Luồng: nhập email → nhận mã OTP → nhập đúng mã → nhận token đặt lại mật khẩu dùng một lần
 * (xem {@link ResetPasswordService}).
 *
 * <p>Quy tắc bảo mật:
 * <ul>
 *   <li><b>NFR-06 (chống dò email):</b> Bước yêu cầu mã luôn trả cùng một kiểu phản hồi dù email có tồn tại,
 *       bị khóa, đang trong thời gian chờ hay gửi mail lỗi. Bước nhập mã trả cùng một lỗi cho mã sai và
 *       email không tồn tại.</li>
 *   <li><b>BR-05:</b> Mã đúng được đổi lấy token đặt lại mật khẩu 256-bit, hạn 30 phút, dùng một lần.</li>
 * </ul>
 */
@Service
public class RequestPasswordResetService {

  private static final Logger log = LoggerFactory.getLogger(RequestPasswordResetService.class);
  private static final String INVALID_CODE_MESSAGE = "Mã xác thực không đúng hoặc đã hết hạn.";

  private final AccountRepository accountRepository;
  private final AccountTokenRepository accountTokenRepository;
  private final EmailOtpChallengeRepository challengeRepository;
  private final TokenCryptoService tokenCryptoService;
  private final AccountEmailSender emailSender;
  private final OtpPolicy otpPolicy;
  private final Clock clock;

  public RequestPasswordResetService(
      AccountRepository accountRepository,
      AccountTokenRepository accountTokenRepository,
      EmailOtpChallengeRepository challengeRepository,
      TokenCryptoService tokenCryptoService,
      AccountEmailSender emailSender,
      OtpPolicy otpPolicy,
      Clock clock
  ) {
    this.accountRepository = accountRepository;
    this.accountTokenRepository = accountTokenRepository;
    this.challengeRepository = challengeRepository;
    this.tokenCryptoService = tokenCryptoService;
    this.emailSender = emailSender;
    this.otpPolicy = otpPolicy;
    this.clock = clock;
  }

  /**
   * Yêu cầu (hoặc yêu cầu lại) mã OTP đặt lại mật khẩu. Luôn trả phản hồi trung tính.
   */
  @Transactional
  public OtpChallengeView requestCode(String email) {
    Objects.requireNonNull(email, "Email không được để trống");
    String normalizedEmail = Account.normalizeEmail(email);
    Instant now = clock.instant();
    OtpChallengeView neutral = new OtpChallengeView(
        normalizedEmail, now.plus(otpPolicy.codeTtl()), now.plus(otpPolicy.resendCooldown()));

    Optional<Account> account = accountRepository.findByEmail(normalizedEmail)
        .filter(found -> found.getStatus() == AccountStatus.ACTIVE);
    if (account.isEmpty()) {
      return neutral;
    }

    EmailOtpChallenge challenge = challengeRepository
        .findActiveForUpdate(OtpPurpose.PASSWORD_RESET, normalizedEmail)
        .orElseGet(() -> EmailOtpChallenge.forPasswordReset(normalizedEmail));
    if (!challenge.canIssueCode(now, otpPolicy)) {
      // Không lộ việc email tồn tại qua lỗi 429; người dùng thật đã có đếm ngược trên giao diện.
      return neutral;
    }

    String code = tokenCryptoService.generateOtpCode();
    challenge.issueCode(tokenCryptoService.hashOtpCode(challenge.getId(), code), now, otpPolicy);
    challengeRepository.save(challenge);

    try {
      emailSender.sendPasswordResetCode(normalizedEmail, account.get().getFullName(), code, otpPolicy.codeTtl());
    } catch (EmailDeliveryException e) {
      log.error("[IAM EMAIL] Không gửi được mã đặt lại mật khẩu: {}", e.getMessage());
    }
    return new OtpChallengeView(normalizedEmail, challenge.getExpiresAt(), challenge.resendAvailableAt(otpPolicy));
  }

  /**
   * Kiểm tra mã OTP; đúng thì cấp token đặt lại mật khẩu dùng một lần.
   * Lần nhập sai vẫn được ghi nhận (không rollback) để giới hạn số lần thử có hiệu lực.
   */
  @Transactional(noRollbackFor = InvalidOperationException.class)
  public PasswordResetGrant verifyCode(String email, String code) {
    Objects.requireNonNull(email, "Email không được để trống");
    Objects.requireNonNull(code, "Mã xác thực không được để trống");

    String normalizedEmail = Account.normalizeEmail(email);
    Instant now = clock.instant();
    EmailOtpChallenge challenge = challengeRepository
        .findActiveForUpdate(OtpPurpose.PASSWORD_RESET, normalizedEmail)
        .orElseThrow(() -> new InvalidOperationException(ErrorCode.INVALID_TOKEN, INVALID_CODE_MESSAGE));

    boolean matches = challenge.verify(tokenCryptoService.hashOtpCode(challenge.getId(), code), now, otpPolicy);
    challengeRepository.save(challenge);
    if (!matches) {
      int remaining = challenge.remainingAttempts(otpPolicy);
      throw new InvalidOperationException(
          ErrorCode.INVALID_TOKEN,
          remaining > 0
              ? "Mã xác thực không đúng. Bạn còn " + remaining + " lần thử."
              : "Mã xác thực không đúng. Bạn đã hết lượt thử, vui lòng yêu cầu mã mới."
      );
    }

    Account account = accountRepository.findByEmail(normalizedEmail)
        .filter(found -> found.getStatus() == AccountStatus.ACTIVE)
        .orElseThrow(() -> new InvalidOperationException(ErrorCode.INVALID_TOKEN, INVALID_CODE_MESSAGE));

    accountTokenRepository.invalidatePreviousTokens(account, TokenType.RESET_PASSWORD, now);
    String rawToken = tokenCryptoService.generateRawToken();
    Instant expiresAt = now.plus(TokenType.RESET_PASSWORD.getDefaultTtl());
    accountTokenRepository.save(AccountToken.create(
        account, TokenType.RESET_PASSWORD, tokenCryptoService.hashToken(rawToken), expiresAt));
    return new PasswordResetGrant(rawToken, expiresAt);
  }
}

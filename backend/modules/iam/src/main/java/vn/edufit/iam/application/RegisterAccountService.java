package vn.edufit.iam.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.application.port.AccountEmailSender;
import vn.edufit.iam.application.port.EmailDeliveryException;
import vn.edufit.iam.infra.persistence.Account;
import vn.edufit.iam.infra.persistence.AccountRepository;
import vn.edufit.iam.infra.persistence.EmailOtpChallenge;
import vn.edufit.iam.infra.persistence.EmailOtpChallengeRepository;
import vn.edufit.iam.infra.persistence.OtpPurpose;
import vn.edufit.iam.infra.security.Argon2PasswordService;
import vn.edufit.iam.infra.security.TokenCryptoService;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Ca sử dụng Đăng ký tài khoản có xác thực email bằng mã OTP (UC1.1).
 *
 * <p>Quy tắc nghiệp vụ:
 * <ul>
 *   <li><b>BR-01:</b> Email được chuẩn hóa (trim, lowercase) và không trùng với tài khoản đã có.</li>
 *   <li><b>BR-02:</b> Mật khẩu phải vượt qua {@link PasswordPolicy}.</li>
 *   <li><b>BR-03:</b> Chỉ cho phép vai trò STUDENT, PARENT, TUTOR. Cấm ADMIN.</li>
 *   <li><b>BR-04:</b> Tài khoản chỉ được tạo (ACTIVE) sau khi nhập đúng mã OTP gửi tới email.
 *       Email chưa xác thực coi như chưa đăng ký: không đăng nhập được và có thể đăng ký lại.</li>
 *   <li><b>NFR-01:</b> Mật khẩu được băm bằng Argon2id ngay từ bước đăng ký, không lưu mật khẩu thô.</li>
 * </ul>
 */
@Service
public class RegisterAccountService {

  private final AccountRepository accountRepository;
  private final EmailOtpChallengeRepository challengeRepository;
  private final Argon2PasswordService passwordService;
  private final PasswordPolicy passwordPolicy;
  private final TokenCryptoService tokenCryptoService;
  private final AccountEmailSender emailSender;
  private final OtpPolicy otpPolicy;
  private final Clock clock;

  public RegisterAccountService(
      AccountRepository accountRepository,
      EmailOtpChallengeRepository challengeRepository,
      Argon2PasswordService passwordService,
      PasswordPolicy passwordPolicy,
      TokenCryptoService tokenCryptoService,
      AccountEmailSender emailSender,
      OtpPolicy otpPolicy,
      Clock clock
  ) {
    this.accountRepository = accountRepository;
    this.challengeRepository = challengeRepository;
    this.passwordService = passwordService;
    this.passwordPolicy = passwordPolicy;
    this.tokenCryptoService = tokenCryptoService;
    this.emailSender = emailSender;
    this.otpPolicy = otpPolicy;
    this.clock = clock;
  }

  /**
   * Bước 1: ghi nhận đăng ký chờ xác thực và gửi mã OTP tới email. Chưa tạo tài khoản.
   * Đăng ký lại cùng email trước khi xác thực sẽ thay thông tin cũ và gửi mã mới (tuân theo thời gian chờ).
   */
  @Transactional
  public OtpChallengeView startRegistration(String email, String password, String fullName, AccountRole role) {
    Objects.requireNonNull(email, "Email không được để trống");
    Objects.requireNonNull(password, "Mật khẩu không được để trống");
    Objects.requireNonNull(fullName, "Họ tên không được để trống");
    Objects.requireNonNull(role, "Vai trò không được để trống");

    passwordPolicy.validate(password);
    String normalizedEmail = Account.normalizeEmail(email);
    ensureEmailNotRegistered(normalizedEmail);

    String passwordHash = passwordService.hash(password);
    EmailOtpChallenge challenge = challengeRepository
        .findActiveForUpdate(OtpPurpose.REGISTRATION, normalizedEmail)
        .map(existing -> {
          existing.updateRegistrationDetails(passwordHash, fullName, role);
          return existing;
        })
        .orElseGet(() -> EmailOtpChallenge.forRegistration(normalizedEmail, passwordHash, fullName, role));

    return issueAndSend(challenge);
  }

  /**
   * Gửi lại mã OTP cho đăng ký đang chờ xác thực.
   */
  @Transactional
  public OtpChallengeView resendCode(String email) {
    Objects.requireNonNull(email, "Email không được để trống");
    EmailOtpChallenge challenge = requirePendingRegistration(Account.normalizeEmail(email));
    return issueAndSend(challenge);
  }

  /**
   * Bước 2: kiểm tra mã OTP; đúng thì tạo tài khoản ACTIVE từ thông tin đã đăng ký.
   * Lần nhập sai vẫn được ghi nhận (không rollback) để giới hạn số lần thử có hiệu lực.
   */
  @Transactional(noRollbackFor = InvalidOperationException.class)
  public Account completeRegistration(String email, String code) {
    Objects.requireNonNull(email, "Email không được để trống");
    Objects.requireNonNull(code, "Mã xác thực không được để trống");

    String normalizedEmail = Account.normalizeEmail(email);
    EmailOtpChallenge challenge = requirePendingRegistration(normalizedEmail);
    Instant now = clock.instant();

    boolean matches = challenge.verify(tokenCryptoService.hashOtpCode(challenge.getId(), code), now, otpPolicy);
    challengeRepository.save(challenge);
    if (!matches) {
      throw wrongCode(challenge);
    }

    ensureEmailNotRegistered(normalizedEmail);
    Account account = Account.create(
        normalizedEmail, challenge.getPasswordHash(), challenge.getFullName(), challenge.getRole());
    account.recordSuccessfulLogin(now);
    return accountRepository.save(account);
  }

  private OtpChallengeView issueAndSend(EmailOtpChallenge challenge) {
    Instant now = clock.instant();
    String code = tokenCryptoService.generateOtpCode();
    challenge.issueCode(tokenCryptoService.hashOtpCode(challenge.getId(), code), now, otpPolicy);
    challengeRepository.saveAndFlush(challenge);

    try {
      emailSender.sendRegistrationCode(challenge.getEmail(), challenge.getFullName(), code, otpPolicy.codeTtl());
    } catch (EmailDeliveryException e) {
      // Rollback toàn bộ để người dùng thử lại ngay, không bị tính thời gian chờ cho một mã không tới tay.
      throw new InvalidOperationException(
          ErrorCode.EMAIL_DELIVERY_FAILED,
          "Không gửi được email xác thực lúc này. Vui lòng thử lại sau ít phút."
      );
    }
    return new OtpChallengeView(
        challenge.getEmail(), challenge.getExpiresAt(), challenge.resendAvailableAt(otpPolicy));
  }

  private EmailOtpChallenge requirePendingRegistration(String normalizedEmail) {
    return challengeRepository.findActiveForUpdate(OtpPurpose.REGISTRATION, normalizedEmail)
        .orElseThrow(() -> new InvalidOperationException(
            ErrorCode.INVALID_TOKEN,
            "Không có yêu cầu đăng ký nào đang chờ xác thực cho email này. Vui lòng đăng ký lại."
        ));
  }

  private void ensureEmailNotRegistered(String normalizedEmail) {
    if (accountRepository.existsByEmail(normalizedEmail)) {
      throw new ResourceConflictException(
          ErrorCode.USER_ALREADY_EXISTS,
          "Email đã được đăng ký bởi một tài khoản khác trong hệ thống"
      );
    }
  }

  private InvalidOperationException wrongCode(EmailOtpChallenge challenge) {
    int remaining = challenge.remainingAttempts(otpPolicy);
    String message = remaining > 0
        ? "Mã xác thực không đúng. Bạn còn " + remaining + " lần thử."
        : "Mã xác thực không đúng. Bạn đã hết lượt thử, vui lòng yêu cầu mã mới.";
    return new InvalidOperationException(ErrorCode.INVALID_TOKEN, message);
  }
}

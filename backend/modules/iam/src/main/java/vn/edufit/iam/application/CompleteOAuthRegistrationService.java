package vn.edufit.iam.application;

import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.infra.persistence.Account;
import vn.edufit.iam.infra.persistence.AccountRepository;
import vn.edufit.iam.infra.persistence.UserSocialAccount;
import vn.edufit.iam.infra.persistence.UserSocialAccountRepository;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Ca sử dụng Hoàn tất đăng ký tài khoản qua OAuth2 sau khi người dùng chọn vai trò.
 */
@Service
public class CompleteOAuthRegistrationService {

  private final AccountRepository accountRepository;
  private final UserSocialAccountRepository userSocialAccountRepository;
  private final OAuthOnboardingTokenService tokenService;
  private final Clock clock;

  public CompleteOAuthRegistrationService(
      AccountRepository accountRepository,
      UserSocialAccountRepository userSocialAccountRepository,
      OAuthOnboardingTokenService tokenService,
      Clock clock
  ) {
    this.accountRepository = accountRepository;
    this.userSocialAccountRepository = userSocialAccountRepository;
    this.tokenService = tokenService;
    this.clock = clock;
  }

  /**
   * Hoàn tất tạo tài khoản với vai trò đã chọn.
   *
   * @param onboardingToken mã xác thực tạm thời từ bước đăng nhập
   * @param role vai trò người dùng lựa chọn (STUDENT, PARENT, TUTOR)
   * @return {@link Account} mới tạo thành công
   */
  @Transactional
  public Account completeRegistration(String onboardingToken, AccountRole role) {
    Objects.requireNonNull(onboardingToken, "onboardingToken không được để trống");
    Objects.requireNonNull(role, "Vai trò không được để trống");

    if (role == AccountRole.ADMIN) {
      throw new InvalidOperationException(
          ErrorCode.FORBIDDEN,
          "Không được phép tự đăng ký tài khoản với quyền Quản trị viên (ADMIN)"
      );
    }

    OAuthUserInfo userInfo = tokenService.consume(onboardingToken)
        .orElseThrow(() -> new InvalidOperationException(
            ErrorCode.INVALID_CREDENTIALS,
            "Phiên chọn vai trò đã hết hạn hoặc không hợp lệ. Vui lòng thử đăng nhập lại."
        ));

    String normalizedEmail = Account.normalizeEmail(userInfo.email());

    // Kiểm tra trùng lặp email phòng trường hợp tài khoản vừa được tạo
    if (accountRepository.existsByEmail(normalizedEmail)) {
      throw new ResourceConflictException(
          ErrorCode.USER_ALREADY_EXISTS,
          "Email " + normalizedEmail + " đã được đăng ký trong hệ thống"
      );
    }

    // 1. Tạo tài khoản EduFit mới
    Account account = Account.createOAuthAccount(
        normalizedEmail,
        userInfo.fullName(),
        role,
        userInfo.avatarUrl()
    );
    account.recordSuccessfulLogin(clock.instant());
    Account savedAccount = accountRepository.save(account);

    // 2. Lưu liên kết tài khoản mạng xã hội
    UserSocialAccount socialAccount = UserSocialAccount.of(
        savedAccount.getId(),
        userInfo.provider(),
        userInfo.providerUserId(),
        normalizedEmail,
        userInfo.avatarUrl()
    );
    userSocialAccountRepository.save(socialAccount);

    return savedAccount;
  }
}

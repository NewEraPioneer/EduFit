package vn.edufit.iam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.application.port.GoogleTokenVerifier;
import vn.edufit.iam.infra.persistence.Account;
import vn.edufit.iam.infra.persistence.AccountRepository;
import vn.edufit.iam.infra.persistence.UserSocialAccount;
import vn.edufit.iam.infra.persistence.UserSocialAccountRepository;
import vn.edufit.iam.web.dto.AuthUserResponse;
import vn.edufit.iam.web.dto.OAuthLoginRequest;
import vn.edufit.iam.web.dto.OAuthLoginResult;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Xử lý xác thực Google OAuth2 và điều phối luồng đăng nhập / chọn vai trò lần đầu (UC1.1, UC1.2).
 */
@Service
public class GoogleOAuthService {

  private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

  private final AccountRepository accountRepository;
  private final UserSocialAccountRepository userSocialAccountRepository;
  private final OAuthOnboardingTokenService tokenService;
  private final GoogleTokenVerifier tokenVerifier;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public GoogleOAuthService(
      AccountRepository accountRepository,
      UserSocialAccountRepository userSocialAccountRepository,
      OAuthOnboardingTokenService tokenService,
      GoogleTokenVerifier tokenVerifier,
      ObjectMapper objectMapper,
      Clock clock
  ) {
    this.accountRepository = accountRepository;
    this.userSocialAccountRepository = userSocialAccountRepository;
    this.tokenService = tokenService;
    this.tokenVerifier = tokenVerifier;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /**
   * Xác thực yêu cầu đăng nhập từ Google.
   *
   * @param request payload từ frontend (chứa idToken hoặc thông tin mô phỏng)
   * @return OAuthProcessOutcome chứa kết quả xác thực và tài khoản (nếu đã có)
   */
  @Transactional
  public OAuthProcessOutcome authenticate(OAuthLoginRequest request) {
    Objects.requireNonNull(request, "OAuthLoginRequest không được để trống");

    OAuthUserInfo userInfo = extractUserInfo(request);
    String provider = userInfo.provider();
    String providerUserId = userInfo.providerUserId();
    String normalizedEmail = Account.normalizeEmail(userInfo.email());

    if (normalizedEmail.isBlank()) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Không thể trích xuất địa chỉ email từ tài khoản Google"
      );
    }

    // 1. Kiểm tra xem định danh mạng xã hội này đã liên kết với tài khoản EduFit nào chưa
    Optional<UserSocialAccount> existingSocial = userSocialAccountRepository
        .findByProviderAndProviderUserId(provider, providerUserId);

    if (existingSocial.isPresent()) {
      Account account = accountRepository.findById(existingSocial.get().getUserId())
          .orElseThrow(() -> new InvalidOperationException(
              ErrorCode.RESOURCE_NOT_FOUND,
              "Tài khoản người dùng liên kết không còn tồn tại trên hệ thống"
          ));

      validateAccountActive(account);
      account.recordSuccessfulLogin(clock.instant());
      accountRepository.save(account);

      return OAuthProcessOutcome.authenticated(account, OAuthLoginResult.authenticated(AuthUserResponse.from(account)));
    }

    // 2. Nếu chưa liên kết provider_user_id, kiểm tra xem email đã tồn tại trong hệ thống chưa
    Optional<Account> existingAccountByEmail = accountRepository.findByEmail(normalizedEmail);
    if (existingAccountByEmail.isPresent()) {
      Account account = existingAccountByEmail.get();
      validateAccountActive(account);

      // Tự động liên kết mạng xã hội với tài khoản hiện có
      UserSocialAccount newSocial = UserSocialAccount.of(
          account.getId(),
          provider,
          providerUserId,
          normalizedEmail,
          userInfo.avatarUrl()
      );
      userSocialAccountRepository.save(newSocial);

      if (account.getAvatarUrl() == null && userInfo.avatarUrl() != null) {
        account.updateAvatar(userInfo.avatarUrl());
      }
      account.recordSuccessfulLogin(clock.instant());
      accountRepository.save(account);

      return OAuthProcessOutcome.authenticated(account, OAuthLoginResult.authenticated(AuthUserResponse.from(account)));
    }

    // 3. Người dùng hoàn toàn mới -> Cần chọn vai trò (Học sinh / Phụ huynh / Gia sư)
    String onboardingToken = tokenService.store(userInfo);
    OAuthLoginResult result = OAuthLoginResult.requiresRole(
        onboardingToken,
        normalizedEmail,
        userInfo.fullName(),
        userInfo.avatarUrl()
    );

    return OAuthProcessOutcome.requiresRole(result);
  }

  private void validateAccountActive(Account account) {
    if (account.getStatus() != AccountStatus.ACTIVE) {
      throw new InvalidOperationException(
          ErrorCode.USER_NOT_ACTIVE,
          "Tài khoản của bạn đã bị vô hiệu hóa hoặc khóa bởi Quản trị viên"
      );
    }
  }

  private OAuthUserInfo extractUserInfo(OAuthLoginRequest request) {
    // Danh tính chỉ đến từ Google ID Token đã xác thực chữ ký số qua GoogleTokenVerifier
    if (request.idToken() == null || request.idToken().isBlank()) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Yêu cầu đăng nhập Google cần cung cấp idToken hợp lệ"
      );
    }
    return tokenVerifier.verify(request.idToken().trim());
  }
}


package vn.edufit.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.application.OAuthProcessOutcome;
import vn.edufit.iam.application.port.GoogleTokenVerifier;
import vn.edufit.iam.infra.persistence.Account;
import vn.edufit.iam.infra.persistence.AccountRepository;
import vn.edufit.iam.infra.persistence.UserSocialAccount;
import vn.edufit.iam.infra.persistence.UserSocialAccountRepository;
import vn.edufit.iam.web.dto.OAuthLoginRequest;
import vn.edufit.shared.exception.InvalidOperationException;

class GoogleOAuthServiceTest {

  private AccountRepository accountRepository;
  private UserSocialAccountRepository userSocialAccountRepository;
  private OAuthOnboardingTokenService tokenService;
  private GoogleTokenVerifier tokenVerifier;
  private GoogleOAuthService googleOAuthService;
  private CompleteOAuthRegistrationService completeOAuthRegistrationService;
  private Clock fixedClock;

  @BeforeEach
  void setUp() {
    accountRepository = mock(AccountRepository.class);
    userSocialAccountRepository = mock(UserSocialAccountRepository.class);
    tokenService = new OAuthOnboardingTokenService();
    tokenVerifier = mock(GoogleTokenVerifier.class);
    fixedClock = Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneId.of("UTC"));

    googleOAuthService = new GoogleOAuthService(
        accountRepository,
        userSocialAccountRepository,
        tokenService,
        tokenVerifier,
        new ObjectMapper(),
        fixedClock
    );

    completeOAuthRegistrationService = new CompleteOAuthRegistrationService(
        accountRepository,
        userSocialAccountRepository,
        tokenService,
        fixedClock
    );
  }

  @Test
  @DisplayName("Người dùng mới đăng nhập Google lần đầu phải yêu cầu chọn vai trò (needsRoleSelection = true)")
  void newGoogleUser_shouldRequireRoleSelection() {
    OAuthLoginRequest request = new OAuthLoginRequest("new-user.id.token", "GOOGLE");
    when(tokenVerifier.verify("new-user.id.token")).thenReturn(new OAuthUserInfo(
        "GOOGLE",
        "google-sub-12345",
        "newuser@gmail.com",
        "Nguyễn Văn Mới",
        "https://avatar.google.com/newuser.png"
    ));

    when(userSocialAccountRepository.findByProviderAndProviderUserId("GOOGLE", "google-sub-12345"))
        .thenReturn(Optional.empty());
    when(accountRepository.findByEmail("newuser@gmail.com"))
        .thenReturn(Optional.empty());

    OAuthProcessOutcome outcome = googleOAuthService.authenticate(request);

    assertThat(outcome.isAuthenticated()).isFalse();
    assertThat(outcome.result().needsRoleSelection()).isTrue();
    assertThat(outcome.result().onboardingToken()).isNotBlank();
    assertThat(outcome.result().email()).isEqualTo("newuser@gmail.com");
    assertThat(outcome.result().fullName()).isEqualTo("Nguyễn Văn Mới");
  }

  @Test
  @DisplayName("Hoàn tất đăng ký thành công với vai trò Học sinh (STUDENT)")
  void completeRegistration_withValidTokenAndStudentRole_shouldCreateAccount() {
    OAuthUserInfo info = new OAuthUserInfo(
        "GOOGLE",
        "google-sub-777",
        "student.test@gmail.com",
        "Em Học Sinh",
        "https://avatar.com/stu.png"
    );
    String token = tokenService.store(info);

    when(accountRepository.existsByEmail("student.test@gmail.com")).thenReturn(false);
    when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
      Account acc = inv.getArgument(0);
      return Account.of(UUID.randomUUID(), acc.getEmail(), acc.getPasswordHash(), acc.getFullName(), acc.getRole(), acc.getStatus(), acc.getAvatarUrl());
    });
    when(userSocialAccountRepository.save(any(UserSocialAccount.class))).thenAnswer(inv -> inv.getArgument(0));

    Account created = completeOAuthRegistrationService.completeRegistration(token, AccountRole.STUDENT);

    assertThat(created).isNotNull();
    assertThat(created.getEmail()).isEqualTo("student.test@gmail.com");
    assertThat(created.getRole()).isEqualTo(AccountRole.STUDENT);
    assertThat(created.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(created.getPasswordHash()).isNull();
    assertThat(created.getAvatarUrl()).isEqualTo("https://avatar.com/stu.png");

    verify(userSocialAccountRepository).save(any(UserSocialAccount.class));
  }

  @Test
  @DisplayName("Không được phép tự đăng ký tài khoản OAuth với vai trò Quản trị viên (ADMIN)")
  void completeRegistration_withAdminRole_shouldThrowForbidden() {
    OAuthUserInfo info = new OAuthUserInfo(
        "GOOGLE",
        "google-sub-admin",
        "admin.hack@gmail.com",
        "Kẻ Mạo Danh",
        null
    );
    String token = tokenService.store(info);

    assertThatThrownBy(() -> completeOAuthRegistrationService.completeRegistration(token, AccountRole.ADMIN))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("ADMIN");
  }

  @Test
  @DisplayName("Người dùng Google đã tồn tại tài khoản thì đăng nhập thành công ngay")
  void existingGoogleUser_shouldAuthenticateDirectly() {
    UUID accountId = UUID.randomUUID();
    Account account = Account.createOAuthAccount("existing@gmail.com", "Đã Tồn Tại", AccountRole.TUTOR, null);

    UserSocialAccount social = UserSocialAccount.of(accountId, "GOOGLE", "sub-existing", "existing@gmail.com", null);

    when(userSocialAccountRepository.findByProviderAndProviderUserId("GOOGLE", "sub-existing"))
        .thenReturn(Optional.of(social));
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

    OAuthLoginRequest request = new OAuthLoginRequest("existing-user.id.token", "GOOGLE");
    when(tokenVerifier.verify("existing-user.id.token")).thenReturn(new OAuthUserInfo(
        "GOOGLE", "sub-existing", "existing@gmail.com", "Đã Tồn Tại", null));

    OAuthProcessOutcome outcome = googleOAuthService.authenticate(request);

    assertThat(outcome.isAuthenticated()).isTrue();
    assertThat(outcome.result().needsRoleSelection()).isFalse();
    assertThat(outcome.result().user().email()).isEqualTo("existing@gmail.com");
    assertThat(outcome.result().user().role()).isEqualTo(AccountRole.TUTOR);
  }

  @Test
  @DisplayName("Khi gửi idToken hợp lệ, hệ thống phải ủy quyền xác thực cho GoogleTokenVerifier")
  void authenticateWithValidIdToken_shouldVerifyViaVerifier() {
    String rawIdToken = "valid.google.id.token";
    OAuthUserInfo verifiedUser = new OAuthUserInfo(
        "GOOGLE",
        "sub-verified-999",
        "verified.user@gmail.com",
        "Người Dùng Đã Xác Thực",
        "https://avatar.com/verified.png"
    );

    when(tokenVerifier.verify(rawIdToken)).thenReturn(verifiedUser);
    when(userSocialAccountRepository.findByProviderAndProviderUserId("GOOGLE", "sub-verified-999"))
        .thenReturn(Optional.empty());
    when(accountRepository.findByEmail("verified.user@gmail.com"))
        .thenReturn(Optional.empty());

    OAuthLoginRequest request = new OAuthLoginRequest(rawIdToken, "GOOGLE");

    OAuthProcessOutcome outcome = googleOAuthService.authenticate(request);

    verify(tokenVerifier).verify(rawIdToken);
    assertThat(outcome.isAuthenticated()).isFalse();
    assertThat(outcome.result().needsRoleSelection()).isTrue();
    assertThat(outcome.result().email()).isEqualTo("verified.user@gmail.com");
  }

  @Test
  @DisplayName("Thiếu idToken thì phải từ chối, kể cả khi client tự khai email của tài khoản có sẵn")
  void authenticateWithoutIdToken_shouldRejectSelfAssertedEmail() throws Exception {
    // Client cố gửi kèm email/sub tự khai; record chỉ nhận idToken + provider nên các field này bị bỏ qua
    OAuthLoginRequest request = new ObjectMapper().readValue(
        "{\"provider\":\"GOOGLE\",\"email\":\"admin.demo@edufit.vn\",\"providerUserId\":\"forged-sub\"}",
        OAuthLoginRequest.class);

    assertThatThrownBy(() -> googleOAuthService.authenticate(request))
        .isInstanceOf(InvalidOperationException.class)
        .satisfies(ex -> assertThat(((InvalidOperationException) ex).getErrorCode())
            .isEqualTo(vn.edufit.shared.exception.ErrorCode.VALIDATION_FAILED));

    verifyNoInteractions(tokenVerifier, accountRepository, userSocialAccountRepository);
  }

  @Test
  @DisplayName("Khi idToken không hợp lệ hoặc hết hạn, hệ thống ném ngoại lệ và từ chối đăng nhập")
  void authenticateWithInvalidIdToken_shouldThrowUnauthorized() {
    String invalidIdToken = "invalid.token";
    when(tokenVerifier.verify(invalidIdToken)).thenThrow(
        new InvalidOperationException(vn.edufit.shared.exception.ErrorCode.UNAUTHORIZED, "Google ID Token không hợp lệ hoặc đã hết hạn")
    );

    OAuthLoginRequest request = new OAuthLoginRequest(invalidIdToken, "GOOGLE");

    assertThatThrownBy(() -> googleOAuthService.authenticate(request))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("Google ID Token không hợp lệ hoặc đã hết hạn");

    verify(tokenVerifier).verify(invalidIdToken);
  }
}

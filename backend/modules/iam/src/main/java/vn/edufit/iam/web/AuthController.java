package vn.edufit.iam.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.iam.application.AuthenticateAccountService;
import vn.edufit.iam.application.ChangePasswordService;
import vn.edufit.iam.application.CompleteOAuthRegistrationService;
import vn.edufit.iam.application.GoogleOAuthService;
import vn.edufit.iam.application.OAuthProcessOutcome;
import vn.edufit.iam.application.RegisterAccountService;
import vn.edufit.iam.application.RequestPasswordResetService;
import vn.edufit.iam.application.ResetPasswordService;
import vn.edufit.iam.infra.persistence.Account;
import vn.edufit.iam.infra.persistence.AccountRepository;
import vn.edufit.iam.infra.security.EduFitUserDetails;
import vn.edufit.iam.web.dto.AuthUserResponse;
import vn.edufit.iam.web.dto.ChangePasswordRequest;
import vn.edufit.iam.web.dto.CompleteOAuthRegistrationRequest;
import vn.edufit.iam.web.dto.ForgotPasswordRequest;
import vn.edufit.iam.web.dto.LoginRequest;
import vn.edufit.iam.web.dto.OAuthLoginRequest;
import vn.edufit.iam.web.dto.OAuthLoginResult;
import vn.edufit.iam.web.dto.RegisterRequest;
import vn.edufit.iam.web.dto.ResetPasswordRequest;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.response.ApiResponse;

import java.util.concurrent.TimeUnit;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import vn.edufit.iam.application.UpdateAvatarService;
import vn.edufit.iam.infra.storage.AvatarStorageService;
import vn.edufit.iam.web.dto.UpdateAvatarUrlRequest;

/**
 * REST API Controller phụ trách toàn bộ luồng Xác thực, Quản lý phiên và Ảnh đại diện (UC1.1 đến UC1.4, Nhiệm vụ 1.3).
 *
 * <p>Tuân thủ nghiêm ngặt hợp đồng tại {@code docs/authentication-contract.md}:
 * <ul>
 *   <li>Đăng ký kích hoạt tài khoản trực tiếp (Status: ACTIVE).</li>
 *   <li>Quản lý phiên stateful với cookie {@code EDUFIT_SESSION} (ADR-001 Decision 5).</li>
 *   <li>Cấu trúc phản hồi bọc chuẩn {@link ApiResponse}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final RegisterAccountService registerAccountService;
  private final AuthenticateAccountService authenticateAccountService;
  private final RequestPasswordResetService requestPasswordResetService;
  private final ResetPasswordService resetPasswordService;
  private final ChangePasswordService changePasswordService;
  private final GoogleOAuthService googleOAuthService;
  private final CompleteOAuthRegistrationService completeOAuthRegistrationService;
  private final UpdateAvatarService updateAvatarService;
  private final AvatarStorageService avatarStorageService;
  private final AccountRepository accountRepository;
  private final ObjectProvider<CurrentUser> currentUserProvider;
  private final CsrfTokenRepository csrfTokenRepository;

  public AuthController(
      RegisterAccountService registerAccountService,
      AuthenticateAccountService authenticateAccountService,
      RequestPasswordResetService requestPasswordResetService,
      ResetPasswordService resetPasswordService,
      ChangePasswordService changePasswordService,
      GoogleOAuthService googleOAuthService,
      CompleteOAuthRegistrationService completeOAuthRegistrationService,
      UpdateAvatarService updateAvatarService,
      AvatarStorageService avatarStorageService,
      AccountRepository accountRepository,
      ObjectProvider<CurrentUser> currentUserProvider,
      CsrfTokenRepository csrfTokenRepository
  ) {
    this.registerAccountService = registerAccountService;
    this.authenticateAccountService = authenticateAccountService;
    this.requestPasswordResetService = requestPasswordResetService;
    this.resetPasswordService = resetPasswordService;
    this.changePasswordService = changePasswordService;
    this.googleOAuthService = googleOAuthService;
    this.completeOAuthRegistrationService = completeOAuthRegistrationService;
    this.updateAvatarService = updateAvatarService;
    this.avatarStorageService = avatarStorageService;
    this.accountRepository = accountRepository;
    this.currentUserProvider = currentUserProvider;
    this.csrfTokenRepository = csrfTokenRepository;
  }

  /**
   * Đăng ký tài khoản người dùng mới (UC1.1 - Kích hoạt ngay).
   */
  @PostMapping("/register")
  public ResponseEntity<ApiResponse<AuthUserResponse>> register(
      @Valid @RequestBody RegisterRequest request
  ) {
    Account account = registerAccountService.register(
        request.email(),
        request.password(),
        request.fullName(),
        request.role()
    );
    AuthUserResponse responseData = AuthUserResponse.from(account);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(
            responseData,
            "Đăng ký thành công. Bạn có thể đăng nhập ngay bây giờ."
        ));
  }

  /**
   * Đăng nhập người dùng và khởi tạo phiên làm việc (UC1.2).
   */
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthUserResponse>> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse
  ) {
    Account account = authenticateAccountService.authenticate(request.email(), request.password());
    establishSecuritySession(account, httpRequest, httpResponse);

    return ResponseEntity.ok(ApiResponse.success(
        AuthUserResponse.from(account),
        "Đăng nhập thành công"
    ));
  }

  /**
   * Xác thực đăng nhập qua Google OAuth2 (UC1.2 & Nhiệm vụ 1.1).
   *
   * - Nếu tài khoản đã tồn tại: tạo phiên và trả về thông tin người dùng.
   * - Nếu tài khoản mới: trả về onboardingToken yêu cầu người dùng chọn vai trò.
   */
  @PostMapping("/oauth2/google")
  public ResponseEntity<ApiResponse<OAuthLoginResult>> loginWithGoogle(
      @RequestBody OAuthLoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse
  ) {
    OAuthProcessOutcome outcome = googleOAuthService.authenticate(request);

    if (outcome.isAuthenticated()) {
      establishSecuritySession(outcome.account(), httpRequest, httpResponse);
      return ResponseEntity.ok(ApiResponse.success(
          outcome.result(),
          "Đăng nhập tài khoản Google thành công"
      ));
    }

    return ResponseEntity.ok(ApiResponse.success(
        outcome.result(),
        "Xác thực Google thành công. Vui lòng chọn vai trò để hoàn tất đăng ký."
    ));
  }

  /**
   * Hoàn tất đăng ký tài khoản OAuth2 sau khi người dùng chọn vai trò (Học sinh / Phụ huynh / Gia sư).
   */
  @PostMapping("/oauth2/complete-registration")
  public ResponseEntity<ApiResponse<AuthUserResponse>> completeOAuthRegistration(
      @Valid @RequestBody CompleteOAuthRegistrationRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse
  ) {
    Account account = completeOAuthRegistrationService.completeRegistration(
        request.onboardingToken(),
        request.role()
    );

    establishSecuritySession(account, httpRequest, httpResponse);

    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
        AuthUserResponse.from(account),
        "Đăng ký tài khoản thành công. Chào mừng bạn gia nhập EduFit!"
    ));
  }

  /**
   * Thiết lập phiên làm việc chuẩn Spring Security Session Context và Cookie EDUFIT_SESSION.
   */
  private void establishSecuritySession(
      Account account,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse
  ) {
    EduFitUserDetails userDetails = EduFitUserDetails.from(account);
    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
        userDetails,
        null,
        userDetails.getAuthorities()
    );

    new ChangeSessionIdAuthenticationStrategy()
        .onAuthentication(authentication, httpRequest, httpResponse);
    csrfTokenRepository.saveToken(null, httpRequest, httpResponse);

    SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
    securityContext.setAuthentication(authentication);
    SecurityContextHolder.setContext(securityContext);

    HttpSession session = httpRequest.getSession(true);
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
    session.setAttribute("ACCOUNT_ID", account.getId());
    session.setAttribute("ROLE", account.getRole().name());
  }

  /**
   * Đăng xuất và hủy phiên làm việc hiện tại.
   */
  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(
      HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    HttpSession session = httpRequest.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
    csrfTokenRepository.saveToken(null, httpRequest, httpResponse);
    return ResponseEntity.ok(ApiResponse.ok("Đăng xuất thành công"));
  }

  /**
   * Yêu cầu đặt lại mật khẩu khi quên (UC1.3).
   */
  @PostMapping("/forgot-password")
  public ResponseEntity<ApiResponse<Void>> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request
  ) {
    requestPasswordResetService.requestReset(request.email());
    return ResponseEntity.ok(ApiResponse.ok(
        "Nếu email tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi."
    ));
  }

  /**
   * Đặt lại mật khẩu mới qua mã token (UC1.3).
   */
  @PostMapping("/reset-password")
  public ResponseEntity<ApiResponse<Void>> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request
  ) {
    resetPasswordService.resetPassword(request.token(), request.newPassword());
    return ResponseEntity.ok(ApiResponse.ok(
        "Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại."
    ));
  }

  /**
   * Đổi mật khẩu cho tài khoản đang đăng nhập (UC1.4).
   */
  @PostMapping("/change-password")
  public ResponseEntity<ApiResponse<Void>> changePassword(
      @Valid @RequestBody ChangePasswordRequest request
  ) {
    CurrentUser currentUser = currentUserProvider.getIfAvailable();
    if (currentUser == null || currentUser.getUserId() == null) {
      throw new InvalidOperationException(
          ErrorCode.UNAUTHORIZED,
          "Yêu cầu cần được xác thực trước khi thực hiện đổi mật khẩu"
      );
    }

    changePasswordService.changePassword(
        currentUser.getUserId(),
        request.currentPassword(),
        request.newPassword()
    );
    return ResponseEntity.ok(ApiResponse.ok("Đổi mật khẩu thành công"));
  }

  /**
   * Lấy thông tin tài khoản người dùng hiện tại đang đăng nhập.
   */
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<AuthUserResponse>> getCurrentUser() {
    CurrentUser currentUser = currentUserProvider.getIfAvailable();
    if (currentUser == null || currentUser.getUserId() == null) {
      throw new InvalidOperationException(
          ErrorCode.UNAUTHORIZED,
          "Phiên làm việc chưa được xác thực hoặc đã hết hạn"
      );
    }

    Account account = accountRepository.findById(currentUser.getUserId())
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy thông tin tài khoản người dùng"));

    return ResponseEntity.ok(ApiResponse.success(
        AuthUserResponse.from(account),
        "Lấy thông tin tài khoản thành công"
    ));
  }

  /**
   * Tải lên và cập nhật tệp ảnh đại diện cho người dùng đang đăng nhập (Nhiệm vụ 1.3).
   */
  @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<AuthUserResponse>> uploadAvatar(
      @RequestParam("file") MultipartFile file
  ) {
    CurrentUser currentUser = requireCurrentUser();
    AuthUserResponse response = updateAvatarService.uploadAvatar(currentUser.getUserId(), file);
    return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật ảnh đại diện thành công"));
  }

  /**
   * Trả về dữ liệu tệp ảnh đại diện công khai (hỗ trợ cache trình duyệt 7 ngày).
   */
  @GetMapping("/avatar/{fileName:.+}")
  public ResponseEntity<Resource> getAvatar(@PathVariable("fileName") String fileName) {
    Resource resource = avatarStorageService.loadAvatarResource(fileName);
    String mimeType = avatarStorageService.getMimeType(fileName);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(mimeType))
        .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
        .body(resource);
  }

  /**
   * Cập nhật ảnh đại diện qua URL trực tiếp (preset mẫu hoặc URL bên ngoài).
   */
  @PutMapping("/avatar")
  public ResponseEntity<ApiResponse<AuthUserResponse>> updateAvatarUrl(
      @Valid @RequestBody UpdateAvatarUrlRequest request
  ) {
    CurrentUser currentUser = requireCurrentUser();
    AuthUserResponse response = updateAvatarService.updateAvatarUrl(currentUser.getUserId(), request.avatarUrl());
    return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật ảnh đại diện thành công"));
  }

  /**
   * Xóa ảnh đại diện hiện tại và khôi phục về trạng thái mặc định.
   */
  @DeleteMapping("/avatar")
  public ResponseEntity<ApiResponse<AuthUserResponse>> removeAvatar() {
    CurrentUser currentUser = requireCurrentUser();
    AuthUserResponse response = updateAvatarService.removeAvatar(currentUser.getUserId());
    return ResponseEntity.ok(ApiResponse.success(response, "Xóa ảnh đại diện thành công"));
  }

  private CurrentUser requireCurrentUser() {
    CurrentUser currentUser = currentUserProvider.getIfAvailable();
    if (currentUser == null || currentUser.getUserId() == null) {
      throw new InvalidOperationException(
          ErrorCode.UNAUTHORIZED,
          "Yêu cầu cần được xác thực trước khi thực hiện hành động này"
      );
    }
    return currentUser;
  }
}

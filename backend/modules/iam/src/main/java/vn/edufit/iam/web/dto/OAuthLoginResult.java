package vn.edufit.iam.web.dto;

/**
 * Kết quả trả về sau khi xác thực danh tính mạng xã hội.
 * 
 * - Nếu người dùng đã có tài khoản: needsRoleSelection = false, trả về AuthUserResponse.
 * - Nếu người dùng mới đăng nhập lần đầu: needsRoleSelection = true, trả về onboardingToken để chuyển tiếp chọn vai trò.
 */
public record OAuthLoginResult(
    boolean needsRoleSelection,
    String onboardingToken,
    AuthUserResponse user,
    String email,
    String fullName,
    String avatarUrl
) {

  public static OAuthLoginResult authenticated(AuthUserResponse user) {
    return new OAuthLoginResult(false, null, user, user.email(), user.fullName(), user.avatarUrl());
  }

  public static OAuthLoginResult requiresRole(String onboardingToken, String email, String fullName, String avatarUrl) {
    return new OAuthLoginResult(true, onboardingToken, null, email, fullName, avatarUrl);
  }
}

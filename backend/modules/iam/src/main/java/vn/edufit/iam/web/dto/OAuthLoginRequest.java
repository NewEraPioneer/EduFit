package vn.edufit.iam.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload yêu cầu xác thực bằng OAuth2 bên thứ 3 (Google, Zalo,...).
 *
 * <p>Danh tính người dùng chỉ được lấy từ {@code idToken} đã xác minh chữ ký; server không nhận
 * email/tên do client tự khai.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OAuthLoginRequest(
    String idToken,
    String provider
) {
  public String getEffectiveProvider() {
    return (provider != null && !provider.isBlank()) ? provider.toUpperCase() : "GOOGLE";
  }
}

package vn.edufit.iam.application;

/**
 * Thông tin định danh trích xuất từ nhà cung cấp OAuth (Google, Zalo,...).
 */
public record OAuthUserInfo(
    String provider,
    String providerUserId,
    String email,
    String fullName,
    String avatarUrl
) {}

package vn.edufit.iam.application;

import java.time.Instant;

/**
 * Quyền đặt lại mật khẩu cấp sau khi xác thực đúng mã OTP.
 *
 * @param resetToken token thô 256-bit dùng một lần (chỉ trả cho client, CSDL lưu bản băm)
 * @param expiresAt  thời điểm token hết hạn
 */
public record PasswordResetGrant(String resetToken, Instant expiresAt) {}

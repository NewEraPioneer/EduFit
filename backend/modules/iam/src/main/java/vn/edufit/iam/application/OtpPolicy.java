package vn.edufit.iam.application;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chính sách mã OTP qua email dùng chung cho xác thực đăng ký và quên mật khẩu.
 *
 * <p>Giá trị được nạp từ {@code edufit.auth.otp.*} và được kiểm tra ngay khi khởi động
 * (cấu hình sai làm ứng dụng không khởi động được thay vì âm thầm nới lỏng bảo mật).
 *
 * @param codeTtl            thời gian hiệu lực của một mã
 * @param maxFailedAttempts  số lần nhập sai tối đa cho một mã, vượt quá phải gửi mã mới
 * @param resendCooldown     khoảng chờ tối thiểu giữa hai lần gửi mã
 * @param maxSendsPerWindow  số lần gửi mã tối đa trong một cửa sổ {@code sendWindow}
 * @param sendWindow         độ dài cửa sổ đếm số lần gửi mã
 */
@ConfigurationProperties(prefix = "edufit.auth.otp")
public record OtpPolicy(
    Duration codeTtl,
    int maxFailedAttempts,
    Duration resendCooldown,
    int maxSendsPerWindow,
    Duration sendWindow
) {

  public OtpPolicy {
    requirePositive(codeTtl, "edufit.auth.otp.code-ttl");
    requirePositive(resendCooldown, "edufit.auth.otp.resend-cooldown");
    requirePositive(sendWindow, "edufit.auth.otp.send-window");
    if (maxFailedAttempts < 1) {
      throw new IllegalStateException("edufit.auth.otp.max-failed-attempts phải >= 1");
    }
    if (maxSendsPerWindow < 1) {
      throw new IllegalStateException("edufit.auth.otp.max-sends-per-window phải >= 1");
    }
    if (resendCooldown.compareTo(codeTtl) >= 0) {
      throw new IllegalStateException("edufit.auth.otp.resend-cooldown phải ngắn hơn code-ttl");
    }
  }

  private static void requirePositive(Duration value, String key) {
    Objects.requireNonNull(value, key + " là bắt buộc");
    if (value.isNegative() || value.isZero()) {
      throw new IllegalStateException(key + " phải lớn hơn 0");
    }
  }
}

package vn.edufit.iam.application.port;

import java.time.Duration;

/**
 * Cổng gửi thư điện tử của phân hệ IAM (Ports & Adapters).
 *
 * <p>Adapter được chọn theo cấu hình {@code edufit.mail.delivery}: {@code smtp} (Mailpit ở dev/demo,
 * SMTP thật ở prod) hoặc {@code log} (chỉ cho môi trường không phải prod, khi không có SMTP).
 *
 * <p>Mọi phương thức ném {@link EmailDeliveryException} khi không gửi được; tầng ứng dụng quyết định
 * báo lỗi cho người dùng (đăng ký) hay giữ phản hồi trung tính (quên mật khẩu).
 * Mã OTP chỉ xuất hiện trong nội dung thư, adapter SMTP không bao giờ ghi mã ra log.
 */
public interface AccountEmailSender {

  /**
   * Gửi mã xác thực email khi đăng ký tài khoản.
   */
  void sendRegistrationCode(String recipientEmail, String fullName, String code, Duration validity);

  /**
   * Gửi mã xác thực khi người dùng yêu cầu đặt lại mật khẩu.
   */
  void sendPasswordResetCode(String recipientEmail, String fullName, String code, Duration validity);
}

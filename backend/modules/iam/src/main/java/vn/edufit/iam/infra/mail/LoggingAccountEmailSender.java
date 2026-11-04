package vn.edufit.iam.infra.mail;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.edufit.iam.application.port.AccountEmailSender;

/**
 * Adapter "gửi" email bằng cách ghi mã OTP ra log của backend.
 *
 * <p>Chỉ dùng cho môi trường không phải production không có SMTP (ví dụ UAT trên Render chặn cổng SMTP).
 * {@link AccountEmailSenderConfig} từ chối khởi động nếu chế độ này được bật cùng profile {@code prod}.
 */
public class LoggingAccountEmailSender implements AccountEmailSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingAccountEmailSender.class);

  @Override
  public void sendRegistrationCode(String recipientEmail, String fullName, String code, Duration validity) {
    log.warn("[IAM EMAIL:LOG] Mã xác thực ĐĂNG KÝ cho {}: {} (hiệu lực {} phút)",
        recipientEmail, code, validity.toMinutes());
  }

  @Override
  public void sendPasswordResetCode(String recipientEmail, String fullName, String code, Duration validity) {
    log.warn("[IAM EMAIL:LOG] Mã ĐẶT LẠI MẬT KHẨU cho {}: {} (hiệu lực {} phút)",
        recipientEmail, code, validity.toMinutes());
  }
}

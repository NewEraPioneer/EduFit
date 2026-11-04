package vn.edufit.iam.infra.mail;

import java.time.Duration;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import vn.edufit.iam.application.port.AccountEmailSender;
import vn.edufit.iam.application.port.EmailDeliveryException;

/**
 * Adapter gửi email IAM qua SMTP (Mailpit ở dev/demo, SMTP thật ở prod).
 *
 * <p>Mã OTP chỉ nằm trong nội dung thư gửi tới chủ email, không bao giờ được ghi log.
 * Lỗi SMTP được bọc thành {@link EmailDeliveryException} để tầng ứng dụng quyết định cách phản hồi.
 */
public class SmtpAccountEmailSender implements AccountEmailSender {

  private final JavaMailSender mailSender;
  private final String fromAddress;

  public SmtpAccountEmailSender(JavaMailSender mailSender, String fromAddress) {
    this.mailSender = mailSender;
    this.fromAddress = fromAddress;
  }

  @Override
  public void sendRegistrationCode(String recipientEmail, String fullName, String code, Duration validity) {
    send(recipientEmail, OtpEmailTemplates.registration(fullName, code, validity));
  }

  @Override
  public void sendPasswordResetCode(String recipientEmail, String fullName, String code, Duration validity) {
    send(recipientEmail, OtpEmailTemplates.passwordReset(fullName, code, validity));
  }

  private void send(String recipientEmail, OtpEmailTemplates.Content content) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromAddress);
    message.setTo(recipientEmail);
    message.setSubject(content.subject());
    message.setText(content.body());
    try {
      mailSender.send(message);
    } catch (MailException e) {
      throw new EmailDeliveryException("Không gửi được email qua SMTP: " + e.getMessage(), e);
    }
  }
}

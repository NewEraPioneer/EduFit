package vn.edufit.iam.infra.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import vn.edufit.iam.application.port.AccountEmailSender;

/**
 * Adapter gửi email của IAM qua SMTP (Mailpit ở môi trường local, SMTP thật khi triển khai).
 *
 * <p>Token thô chỉ xuất hiện trong nội dung thư gửi tới chủ tài khoản, không bao giờ được ghi log.
 * Lỗi SMTP được ghi nhận nhưng không ném ra ngoài để API quên mật khẩu luôn phản hồi giống nhau
 * dù email có tồn tại hay không (NFR-06).
 */
@Component
public class SmtpAccountEmailSender implements AccountEmailSender {

  private static final Logger log = LoggerFactory.getLogger(SmtpAccountEmailSender.class);

  private final JavaMailSender mailSender;
  private final String fromAddress;
  private final String frontendBaseUrl;

  public SmtpAccountEmailSender(
      JavaMailSender mailSender,
      @Value("${edufit.mail.from}") String fromAddress,
      @Value("${edufit.frontend.base-url}") String frontendBaseUrl
  ) {
    this.mailSender = mailSender;
    this.fromAddress = fromAddress;
    this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
  }

  @Override
  public void sendPasswordResetEmail(String recipientEmail, String rawToken, String fullName) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromAddress);
    message.setTo(recipientEmail);
    message.setSubject("[EduFit] Đặt lại mật khẩu");
    message.setText("""
        Xin chào %s,

        Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản EduFit của bạn.
        Mở liên kết sau trong vòng 30 phút để đặt mật khẩu mới:

        %s/reset-password?token=%s

        Nếu bạn không yêu cầu, hãy bỏ qua email này.
        """.formatted(fullName, frontendBaseUrl, rawToken));

    try {
      mailSender.send(message);
    } catch (MailException e) {
      log.error("[IAM EMAIL] Không gửi được email đặt lại mật khẩu: {}", e.getMessage());
    }
  }
}

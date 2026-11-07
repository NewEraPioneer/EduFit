package vn.edufit.iam.infra.mail;

import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSender;
import vn.edufit.iam.application.port.AccountEmailSender;

/**
 * Chọn adapter gửi email IAM theo {@code edufit.mail.delivery} và chặn cấu hình nguy hiểm khi khởi động.
 *
 * <ul>
 *   <li>{@code smtp} (mặc định): gửi qua {@link JavaMailSender}. Dev/demo trỏ tới Mailpit, prod trỏ tới SMTP thật.</li>
 *   <li>{@code log}: ghi mã ra log, chỉ cho môi trường không phải prod. Bật cùng profile {@code prod}
 *       thì ứng dụng không khởi động.</li>
 *   <li>Giá trị khác: ứng dụng không khởi động.</li>
 * </ul>
 */
@Configuration
public class AccountEmailSenderConfig {

  static final String SMTP = "smtp";
  static final String LOG = "log";

  @Bean
  AccountEmailSender accountEmailSender(
      @Value("${edufit.mail.delivery:smtp}") String delivery,
      @Value("${edufit.mail.from}") String fromAddress,
      ObjectProvider<JavaMailSender> mailSender,
      Environment environment
  ) {
    String mode = delivery == null ? "" : delivery.trim().toLowerCase(Locale.ROOT);
    return switch (mode) {
      case SMTP -> new SmtpAccountEmailSender(requireMailSender(mailSender), requireFrom(fromAddress));
      case LOG -> {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
          throw new IllegalStateException(
              "edufit.mail.delivery=log bị cấm ở profile prod: mã OTP sẽ lộ ra log. Hãy cấu hình SMTP (MAIL_*).");
        }
        yield new LoggingAccountEmailSender();
      }
      default -> throw new IllegalStateException(
          "edufit.mail.delivery chỉ nhận 'smtp' hoặc 'log', nhận được: '" + delivery + "'");
    };
  }

  private static JavaMailSender requireMailSender(ObjectProvider<JavaMailSender> mailSender) {
    JavaMailSender sender = mailSender.getIfAvailable();
    if (sender == null) {
      throw new IllegalStateException(
          "edufit.mail.delivery=smtp nhưng chưa cấu hình spring.mail.host (MAIL_HOST)");
    }
    return sender;
  }

  private static String requireFrom(String fromAddress) {
    if (fromAddress == null || fromAddress.isBlank()) {
      throw new IllegalStateException("edufit.mail.from (MAIL_FROM) là bắt buộc khi gửi qua SMTP");
    }
    return fromAddress.trim();
  }
}

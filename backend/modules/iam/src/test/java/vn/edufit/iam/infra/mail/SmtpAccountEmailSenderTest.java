package vn.edufit.iam.infra.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpAccountEmailSenderTest {

  private static final String FROM = "no-reply@edufit.test";
  private static final String FRONTEND = "https://app.edufit.test/";
  private static final String RECIPIENT = "student@edufit.test";
  private static final String RAW_TOKEN = "a1b2c3d4e5f6";

  private JavaMailSender mailSender;
  private SmtpAccountEmailSender sender;

  @BeforeEach
  void setUp() {
    mailSender = mock(JavaMailSender.class);
    sender = new SmtpAccountEmailSender(mailSender, FROM, FRONTEND);
  }

  @Test
  @DisplayName("Gửi email đặt lại mật khẩu qua SMTP tới đúng người nhận, kèm liên kết chứa token")
  void sendPasswordResetEmail_shouldSendLinkToRecipient() {
    sender.sendPasswordResetEmail(RECIPIENT, RAW_TOKEN, "Học Sinh");

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    SimpleMailMessage message = captor.getValue();
    assertThat(message.getTo()).containsExactly(RECIPIENT);
    assertThat(message.getFrom()).isEqualTo(FROM);
    assertThat(message.getText())
        .contains("https://app.edufit.test/reset-password?token=" + RAW_TOKEN)
        .contains("Học Sinh");
  }

  @Test
  @DisplayName("Lỗi SMTP không được làm lộ việc email có tồn tại (không ném ngoại lệ ra API)")
  void sendPasswordResetEmail_whenSmtpFails_shouldNotPropagate() {
    doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

    assertThatCode(() -> sender.sendPasswordResetEmail(RECIPIENT, RAW_TOKEN, "Học Sinh"))
        .doesNotThrowAnyException();
  }
}

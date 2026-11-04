package vn.edufit.iam.infra.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import vn.edufit.iam.application.port.EmailDeliveryException;

@DisplayName("Kiểm thử adapter gửi email OTP và chốt chặn cấu hình theo môi trường")
class SmtpAccountEmailSenderTest {

  private static final String FROM = "no-reply@edufit.test";
  private static final String RECIPIENT = "student@edufit.test";
  private static final String FULL_NAME = "Học Sinh";
  private static final String CODE = "042137";
  private static final Duration VALIDITY = Duration.ofMinutes(10);

  private JavaMailSender mailSender;
  private SmtpAccountEmailSender sender;

  @BeforeEach
  void setUp() {
    mailSender = mock(JavaMailSender.class);
    sender = new SmtpAccountEmailSender(mailSender, FROM);
  }

  private SimpleMailMessage sentMessage() {
    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("MAIL-001: Thư xác thực đăng ký gửi đúng người nhận, chứa mã và thời hạn")
  void sendRegistrationCode_containsCode() {
    sender.sendRegistrationCode(RECIPIENT, FULL_NAME, CODE, VALIDITY);

    SimpleMailMessage message = sentMessage();
    assertThat(message.getFrom()).isEqualTo(FROM);
    assertThat(message.getTo()).containsExactly(RECIPIENT);
    assertThat(message.getSubject()).contains(CODE);
    assertThat(message.getText()).contains(CODE, FULL_NAME, "10 phút");
  }

  @Test
  @DisplayName("MAIL-002: Thư quên mật khẩu chứa mã, không còn liên kết chứa token")
  void sendPasswordResetCode_containsCodeWithoutLink() {
    sender.sendPasswordResetCode(RECIPIENT, FULL_NAME, CODE, VALIDITY);

    SimpleMailMessage message = sentMessage();
    assertThat(message.getText()).contains(CODE).doesNotContain("token=", "http");
  }

  @Test
  @DisplayName("MAIL-003: Lỗi SMTP được bọc thành EmailDeliveryException để tầng ứng dụng quyết định")
  void smtpFailure_isWrapped() {
    doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

    assertThatThrownBy(() -> sender.sendRegistrationCode(RECIPIENT, FULL_NAME, CODE, VALIDITY))
        .isInstanceOf(EmailDeliveryException.class);
  }

  @Test
  @DisplayName("MAIL-004: Chế độ smtp chọn adapter SMTP; chế độ log chỉ được bật ngoài profile prod")
  void deliveryMode_selectsAdapterAndGuardsProd() {
    AccountEmailSenderConfig config = new AccountEmailSenderConfig();
    ObjectProvider<JavaMailSender> provider = providerOf(mailSender);

    assertThat(config.accountEmailSender("smtp", FROM, provider, new MockEnvironment()))
        .isInstanceOf(SmtpAccountEmailSender.class);
    assertThat(config.accountEmailSender("log", FROM, provider, envWithProfile("uat")))
        .isInstanceOf(LoggingAccountEmailSender.class);
    assertThatThrownBy(() -> config.accountEmailSender("log", FROM, provider, envWithProfile("prod")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("prod");
  }

  @Test
  @DisplayName("MAIL-005: Giá trị chế độ lạ, thiếu SMTP hoặc thiếu địa chỉ gửi làm ứng dụng không khởi động")
  void invalidConfiguration_failsFast() {
    AccountEmailSenderConfig config = new AccountEmailSenderConfig();

    assertThatThrownBy(() -> config.accountEmailSender("sms", FROM, providerOf(mailSender), new MockEnvironment()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> config.accountEmailSender("smtp", FROM, providerOf(null), new MockEnvironment()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> config.accountEmailSender("smtp", " ", providerOf(mailSender), new MockEnvironment()))
        .isInstanceOf(IllegalStateException.class);
  }

  private static MockEnvironment envWithProfile(String profile) {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(profile);
    return environment;
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<JavaMailSender> providerOf(JavaMailSender sender) {
    ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(sender);
    return provider;
  }
}

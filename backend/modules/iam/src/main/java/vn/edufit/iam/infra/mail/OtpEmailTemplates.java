package vn.edufit.iam.infra.mail;

import java.time.Duration;

/**
 * Nội dung email chứa mã OTP của IAM.
 */
final class OtpEmailTemplates {

  record Content(String subject, String body) {}

  private OtpEmailTemplates() {
  }

  static Content registration(String fullName, String code, Duration validity) {
    return new Content(
        "[EduFit] Mã xác thực đăng ký: " + code,
        """
        Xin chào %s,

        Cảm ơn bạn đã đăng ký EduFit. Mã xác thực email của bạn là:

            %s

        Mã có hiệu lực trong %d phút và chỉ dùng được một lần.
        Nếu bạn không đăng ký EduFit, hãy bỏ qua email này; tài khoản sẽ không được tạo.
        """.formatted(fullName, code, validity.toMinutes())
    );
  }

  static Content passwordReset(String fullName, String code, Duration validity) {
    return new Content(
        "[EduFit] Mã đặt lại mật khẩu: " + code,
        """
        Xin chào %s,

        Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản EduFit của bạn. Mã xác thực là:

            %s

        Mã có hiệu lực trong %d phút và chỉ dùng được một lần.
        Nếu bạn không yêu cầu, hãy bỏ qua email này; mật khẩu của bạn không thay đổi.
        """.formatted(fullName, code, validity.toMinutes())
    );
  }
}

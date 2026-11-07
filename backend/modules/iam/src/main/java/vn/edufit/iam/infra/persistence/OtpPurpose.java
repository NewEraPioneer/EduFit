package vn.edufit.iam.infra.persistence;

/**
 * Mục đích của một thử thách mã OTP gửi qua email.
 */
public enum OtpPurpose {
  /** Xác thực email khi đăng ký; tài khoản chỉ được tạo sau khi nhập đúng mã. */
  REGISTRATION,

  /** Xác thực chủ sở hữu email trước khi cho phép đặt lại mật khẩu. */
  PASSWORD_RESET
}

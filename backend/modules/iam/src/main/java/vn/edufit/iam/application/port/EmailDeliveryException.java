package vn.edufit.iam.application.port;

/**
 * Không gửi được email qua adapter hạ tầng (SMTP lỗi, sai cấu hình...).
 */
public class EmailDeliveryException extends RuntimeException {

  public EmailDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}

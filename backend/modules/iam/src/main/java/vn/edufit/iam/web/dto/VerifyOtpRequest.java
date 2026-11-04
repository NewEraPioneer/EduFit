package vn.edufit.iam.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Payload nhập mã OTP 6 số gửi qua email (xác thực đăng ký, quên mật khẩu).
 */
public record VerifyOtpRequest(
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    String email,

    @NotBlank(message = "Mã xác thực không được để trống")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã xác thực gồm đúng 6 chữ số")
    String code
) {}

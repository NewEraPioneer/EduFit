package vn.edufit.iam.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload chỉ chứa email: yêu cầu mã quên mật khẩu, gửi lại mã xác thực đăng ký.
 */
public record EmailRequest(
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    String email
) {}

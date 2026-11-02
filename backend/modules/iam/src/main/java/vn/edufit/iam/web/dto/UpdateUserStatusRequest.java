package vn.edufit.iam.web.dto;

import jakarta.validation.constraints.NotNull;
import vn.edufit.iam.api.AccountStatus;

/**
 * Payload yêu cầu cập nhật trạng thái hoạt động của tài khoản người dùng.
 */
public record UpdateUserStatusRequest(
    @NotNull(message = "Trạng thái tài khoản không được để trống")
    AccountStatus status,
    String reason
) {
}

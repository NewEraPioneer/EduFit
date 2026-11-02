package vn.edufit.scheduling.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.edufit.scheduling.domain.model.CancelReason;

/**
 * Request DTO gửi lên khi hủy buổi học đã lên lịch (UC4.5).
 */
public record CancelSessionRequest(
    @NotNull(message = "cancelReason không được để trống")
    CancelReason reason,

    @Size(max = 500, message = "comment không được vượt quá 500 ký tự")
    String comment
) {}

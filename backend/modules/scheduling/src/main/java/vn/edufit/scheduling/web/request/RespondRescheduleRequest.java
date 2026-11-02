package vn.edufit.scheduling.web.request;

import jakarta.validation.constraints.Size;

/**
 * Request DTO gửi lên khi phản hồi đề xuất đổi lịch học (UC4.4).
 */
public record RespondRescheduleRequest(
    boolean accept,

    @Size(max = 500, message = "responseReason không được vượt quá 500 ký tự")
    String responseReason
) {}

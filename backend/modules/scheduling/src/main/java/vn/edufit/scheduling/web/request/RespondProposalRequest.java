package vn.edufit.scheduling.web.request;

import jakarta.validation.constraints.Size;

/**
 * Request DTO gửi lên khi phản hồi đề xuất buổi học (UC4.3: Chấp nhận hoặc Từ chối).
 */
public record RespondProposalRequest(
    boolean accept,

    @Size(max = 500, message = "reason không được vượt quá 500 ký tự")
    String reason
) {}

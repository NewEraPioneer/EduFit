package vn.edufit.scheduling.web.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO cho API phản hồi đề xuất đổi lịch học định kỳ của lớp học (UC4.8).
 */
public record RespondRecurringScheduleRequest(
    @NotNull(message = "proposalId không được để trống")
    UUID proposalId,
    @NotNull(message = "accept không được để trống")
    Boolean accept,
    String reason
) {}

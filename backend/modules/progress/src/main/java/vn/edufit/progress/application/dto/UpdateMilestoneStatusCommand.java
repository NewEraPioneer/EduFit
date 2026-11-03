package vn.edufit.progress.application.dto;

import java.time.LocalDate;
import vn.edufit.progress.domain.model.MilestoneStatus;

/**
 * @param reason lý do bắt buộc khi mở lại cột mốc đã đạt ({@code ACHIEVED → IN_PROGRESS}).
 */
public record UpdateMilestoneStatusCommand(
    Integer version,
    MilestoneStatus status,
    LocalDate achievedDate,
    String reason
) {}

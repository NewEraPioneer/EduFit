package vn.edufit.progress.application.dto;

import java.time.LocalDate;
import vn.edufit.progress.domain.model.MilestoneStatus;

public record UpdateMilestoneStatusCommand(
    Integer version,
    MilestoneStatus status,
    LocalDate achievedDate
) {}

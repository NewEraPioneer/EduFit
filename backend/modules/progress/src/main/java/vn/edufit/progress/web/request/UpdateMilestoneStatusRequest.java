package vn.edufit.progress.web.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import vn.edufit.progress.application.dto.UpdateMilestoneStatusCommand;
import vn.edufit.progress.domain.model.MilestoneStatus;

public record UpdateMilestoneStatusRequest(
    @NotNull(message = "version không được để trống")
    Integer version,

    @NotNull(message = "status không được để trống")
    MilestoneStatus status,

    LocalDate achievedDate
) {
  public UpdateMilestoneStatusCommand toCommand() {
    return new UpdateMilestoneStatusCommand(version, status, achievedDate);
  }
}

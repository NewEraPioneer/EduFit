package vn.edufit.progress.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import vn.edufit.progress.application.dto.UpdateMilestoneStatusCommand;
import vn.edufit.progress.domain.model.MilestoneStatus;

public record UpdateMilestoneStatusRequest(
    @NotNull(message = "version không được để trống")
    Integer version,

    @NotNull(message = "status không được để trống")
    MilestoneStatus status,

    LocalDate achievedDate,

    @Size(max = 300, message = "Lý do mở lại tối đa 300 ký tự")
    String reason
) {
  public UpdateMilestoneStatusCommand toCommand() {
    return new UpdateMilestoneStatusCommand(version, status, achievedDate, reason);
  }
}

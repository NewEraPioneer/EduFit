package vn.edufit.progress.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import vn.edufit.progress.application.dto.UpdatePlanCommand;

public record UpdatePlanRequest(
    @NotNull(message = "version không được để trống")
    Integer version,

    @Size(max = 1000, message = "summary tối đa 1000 ký tự")
    String summary,

    @NotEmpty(message = "Danh sách cột mốc không được để trống")
    @Size(min = 1, max = 20, message = "Kế hoạch phải có từ 1 đến 20 cột mốc")
    @Valid
    List<UpdateMilestoneItem> milestones
) {
  public record UpdateMilestoneItem(
      UUID milestoneId,

      @NotNull(message = "Tiêu đề cột mốc không được để trống")
      @Size(min = 1, max = 200, message = "Tiêu đề cột mốc từ 1 đến 200 ký tự")
      String title,

      String description,

      LocalDate targetDate,

      Integer weight
  ) {}

  public UpdatePlanCommand toCommand() {
    return new UpdatePlanCommand(
        version,
        summary,
        milestones != null
            ? milestones.stream()
                .map(m -> new UpdatePlanCommand.UpdateMilestoneItem(m.milestoneId(), m.title(), m.description(), m.targetDate(), m.weight()))
                .toList()
            : List.of()
    );
  }
}

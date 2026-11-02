package vn.edufit.progress.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import vn.edufit.progress.application.dto.CreatePlanCommand;

public record CreatePlanRequest(
    @NotNull(message = "classId không được để trống")
    UUID classId,

    @Size(max = 1000, message = "summary tối đa 1000 ký tự")
    String summary,

    @NotEmpty(message = "Danh sách cột mốc không được để trống")
    @Size(min = 1, max = 20, message = "Kế hoạch phải có từ 1 đến 20 cột mốc")
    @Valid
    List<CreateMilestoneItem> milestones
) {
  public record CreateMilestoneItem(
      @NotNull(message = "Tiêu đề cột mốc không được để trống")
      @Size(min = 1, max = 200, message = "Tiêu đề cột mốc từ 1 đến 200 ký tự")
      String title,

      String description,

      LocalDate targetDate,

      Integer weight
  ) {}

  public CreatePlanCommand toCommand() {
    return new CreatePlanCommand(
        classId,
        summary,
        milestones != null
            ? milestones.stream()
                .map(m -> new CreatePlanCommand.CreateMilestoneItem(m.title(), m.description(), m.targetDate(), m.weight()))
                .toList()
            : List.of()
    );
  }
}

package vn.edufit.progress.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import vn.edufit.progress.application.dto.UpdatePlanCommand;
import vn.edufit.progress.domain.policy.RoadmapShapePolicy;

public record UpdatePlanRequest(
    @NotNull(message = "version không được để trống")
    Integer version,

    @Size(max = 1000, message = "summary tối đa 1000 ký tự")
    String summary,

    @NotEmpty(message = "Danh sách cột mốc không được để trống")
    @Size(min = RoadmapShapePolicy.MIN_MILESTONES, max = RoadmapShapePolicy.MAX_MILESTONES, message = "Lộ trình phải có từ 3 đến 8 cột mốc")
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

      Integer weight,

      @Size(max = 4, message = "Mỗi cột mốc có tối đa 4 tiêu chí đạt")
      @Valid
      List<CriterionItem> criteria
  ) {}

  /** {@code criterionId} null nghĩa là tiêu chí mới. */
  public record CriterionItem(
      UUID criterionId,

      @NotBlank(message = "Nội dung tiêu chí đạt không được để trống")
      @Size(max = 200, message = "Nội dung tiêu chí đạt tối đa 200 ký tự")
      String content
  ) {}

  public UpdatePlanCommand toCommand() {
    return new UpdatePlanCommand(
        version,
        summary,
        milestones != null
            ? milestones.stream()
                .map(m -> new UpdatePlanCommand.UpdateMilestoneItem(
                    m.milestoneId(), m.title(), m.description(), m.targetDate(), m.weight(),
                    m.criteria() != null
                        ? m.criteria().stream()
                            .map(c -> new UpdatePlanCommand.CriterionItem(c.criterionId(), c.content()))
                            .toList()
                        : List.of()))
                .toList()
            : List.of()
    );
  }
}

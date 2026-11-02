package vn.edufit.progress.application.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UpdatePlanCommand(
    Integer version,
    String summary,
    List<UpdateMilestoneItem> milestones
) {
  public record UpdateMilestoneItem(
      UUID milestoneId,
      String title,
      String description,
      LocalDate targetDate,
      Integer weight,
      List<CriterionItem> criteria
  ) {}

  /** {@code criterionId} null nghĩa là tiêu chí mới. */
  public record CriterionItem(UUID criterionId, String content) {}
}

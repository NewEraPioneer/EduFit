package vn.edufit.progress.application.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreatePlanCommand(
    UUID classId,
    String summary,
    List<CreateMilestoneItem> milestones
) {
  public record CreateMilestoneItem(
      String title,
      String description,
      LocalDate targetDate,
      Integer weight
  ) {}
}

package vn.edufit.progress.web.response;

import java.time.LocalDate;
import java.util.UUID;
import vn.edufit.progress.application.dto.ProgressViews;
import vn.edufit.progress.domain.model.Milestone;

public record MilestoneResponse(
    UUID milestoneId,
    String title,
    String description,
    LocalDate targetDate,
    int weight,
    String status,
    LocalDate achievedDate,
    boolean isArchived,
    int sortOrder
) {
  public static MilestoneResponse from(Milestone m) {
    return new MilestoneResponse(
        m.getMilestoneId(),
        m.getTitle(),
        m.getDescription(),
        m.getTargetDate(),
        m.getWeight(),
        m.getStatus().name(),
        m.getAchievedDate(),
        m.isArchived(),
        m.getSortOrder()
    );
  }

  public static MilestoneResponse from(ProgressViews.MilestoneDetailView m) {
    if (m == null) return null;
    return new MilestoneResponse(
        m.milestoneId(),
        m.title(),
        m.description(),
        m.targetDate(),
        m.weight(),
        m.status(),
        m.achievedDate(),
        m.isArchived(),
        m.sortOrder()
    );
  }
}

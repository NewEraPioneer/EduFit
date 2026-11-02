package vn.edufit.progress.web.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import vn.edufit.progress.application.dto.ProgressViews;
import vn.edufit.progress.application.dto.ProgressViews.MilestoneCriterionView;

public record MilestoneResponse(
    UUID milestoneId,
    String title,
    String description,
    LocalDate targetDate,
    LocalDate expectedDate,
    boolean dateInterpolated,
    int weight,
    String status,
    LocalDate achievedDate,
    boolean isArchived,
    int sortOrder,
    Integer suggestedSessions,
    UUID topicId,
    List<MilestoneCriterionView> criteria
) {
  public static MilestoneResponse from(ProgressViews.MilestoneDetailView m) {
    if (m == null) return null;
    return new MilestoneResponse(
        m.milestoneId(),
        m.title(),
        m.description(),
        m.targetDate(),
        m.expectedDate(),
        m.dateInterpolated(),
        m.weight(),
        m.status(),
        m.achievedDate(),
        m.isArchived(),
        m.sortOrder(),
        m.suggestedSessions(),
        m.topicId(),
        m.criteria()
    );
  }
}

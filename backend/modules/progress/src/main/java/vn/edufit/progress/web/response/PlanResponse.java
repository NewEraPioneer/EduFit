package vn.edufit.progress.web.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.edufit.progress.application.dto.ProgressViews;
import vn.edufit.progress.domain.model.LearningPlan;

public record PlanResponse(
    UUID planId,
    UUID classId,
    String summary,
    int version,
    Integer progressPercentage,
    List<MilestoneResponse> milestones,
    Instant createdAt,
    Instant updatedAt
) {
  public static PlanResponse from(LearningPlan plan) {
    if (plan == null) return null;
    List<MilestoneResponse> milestoneResponses = plan.getMilestones().stream()
        .map(MilestoneResponse::from)
        .toList();
    return new PlanResponse(
        plan.getPlanId(),
        plan.getClassId(),
        plan.getSummary(),
        plan.getVersion(),
        plan.calculateProgressPercentage(),
        milestoneResponses,
        plan.getCreatedAt(),
        plan.getUpdatedAt()
    );
  }

  public static PlanResponse from(ProgressViews.PlanDetailView view) {
    if (view == null) return null;
    List<MilestoneResponse> milestoneResponses = view.milestones() != null
        ? view.milestones().stream().map(MilestoneResponse::from).toList()
        : List.of();
    return new PlanResponse(
        view.planId(),
        view.classId(),
        view.summary(),
        view.version(),
        view.progressPercentage(),
        milestoneResponses,
        view.createdAt(),
        view.updatedAt()
    );
  }
}

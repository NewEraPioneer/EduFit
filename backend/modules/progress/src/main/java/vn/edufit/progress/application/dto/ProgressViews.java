package vn.edufit.progress.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.scheduling.api.dto.ClassSessionStatsView;

public class ProgressViews {

  public record ProgressSeriesPointView(
      LocalDate date,
      int progressPercent
  ) {}

  public record ClassSelectorItemView(
      UUID classId,
      String title,
      String status,
      Integer progressPercentage
  ) {}

  public record MilestoneDetailView(
      UUID milestoneId,
      String title,
      String description,
      LocalDate targetDate,
      int weight,
      String status,
      LocalDate achievedDate,
      boolean isArchived,
      int sortOrder
  ) {}

  public record PlanDetailView(
      UUID planId,
      UUID classId,
      String summary,
      int version,
      Integer progressPercentage,
      List<MilestoneDetailView> milestones,
      Instant createdAt,
      Instant updatedAt
  ) {
    public static PlanDetailView from(LearningPlan plan) {
      if (plan == null) return null;
      List<MilestoneDetailView> milestoneViews = plan.getMilestones().stream()
          .map(m -> new MilestoneDetailView(
              m.getMilestoneId(),
              m.getTitle(),
              m.getDescription(),
              m.getTargetDate(),
              m.getWeight(),
              m.getStatus().name(),
              m.getAchievedDate(),
              m.isArchived(),
              m.getSortOrder()
          ))
          .toList();
      return new PlanDetailView(
          plan.getPlanId(),
          plan.getClassId(),
          plan.getSummary(),
          plan.getVersion(),
          plan.calculateProgressPercentage(),
          milestoneViews,
          plan.getCreatedAt(),
          plan.getUpdatedAt()
      );
    }
  }

  public record StudentDashboardView(
      UUID studentId,
      String studentName,
      List<ClassSelectorItemView> classes,
      UUID selectedClassId,
      UUID tutorId,
      String tutorName,
      UUID needId,
      String goalTitle,
      Integer progressPercentage,
      PlanDetailView plan,
      ClassSessionStatsView sessionStats,
      List<ProgressSeriesPointView> progressSeries,
      List<SessionNoteTimelineView> recentNotes,
      boolean hasMoreNotes
  ) {}

  public record ParentStudentSummaryView(
      UUID studentId,
      String studentName,
      List<ClassSelectorItemView> classes,
      StudentDashboardView activeClassDashboard
  ) {}

  public record ParentDashboardView(
      List<ParentStudentSummaryView> students
  ) {}

  public record TeachingHistoryItemView(
      UUID classId,
      UUID studentId,
      String studentName,
      UUID needId,
      String goalTitle,
      String status,
      Integer progressPercentage,
      Instant startedAt,
      Instant endedAt,
      ClassSessionStatsView sessionStats
  ) {}
}

package vn.edufit.progress.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneCriterion;
import vn.edufit.progress.domain.policy.RoadmapShapePolicy;
import vn.edufit.scheduling.api.dto.ClassSessionStatsView;

public class ProgressViews {

  public record MilestoneCriterionView(
      UUID criterionId,
      String content,
      boolean checked,
      Instant checkedAt
  ) {
    public static MilestoneCriterionView from(MilestoneCriterion c) {
      return new MilestoneCriterionView(c.getCriterionId(), c.getContent(), c.isChecked(), c.getCheckedAt());
    }
  }

  public record ClassSelectorItemView(
      UUID classId,
      String title,
      String status,
      Integer progressPercentage
  ) {}

  /**
   * @param expectedDate     hạn hiệu lực: {@code targetDate} nếu gia sư đặt, không thì ngày nội suy (có thể null).
   * @param dateInterpolated {@code expectedDate} do hệ thống nội suy giữa hai mốc đã biết.
   */
  public record MilestoneDetailView(
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
    public static MilestoneDetailView from(Milestone m, RoadmapShapePolicy.ExpectedDate expected) {
      return new MilestoneDetailView(
          m.getMilestoneId(),
          m.getTitle(),
          m.getDescription(),
          m.getTargetDate(),
          expected != null ? expected.date() : m.getTargetDate(),
          expected != null && expected.interpolated(),
          m.getWeight(),
          m.getStatus().name(),
          m.getAchievedDate(),
          m.isArchived(),
          m.getSortOrder(),
          m.getSuggestedSessions(),
          m.getTopicId(),
          m.getCriteria().stream().map(MilestoneCriterionView::from).toList()
      );
    }
  }

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
      Map<UUID, RoadmapShapePolicy.ExpectedDate> expected = plan.expectedTargetDates();
      List<MilestoneDetailView> milestoneViews = plan.getMilestones().stream()
          .map(m -> MilestoneDetailView.from(m, expected.get(m.getMilestoneId())))
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
      ProgressOverviewView overview,
      List<SessionNoteTimelineView> recentNotes,
      boolean hasMoreNotes
  ) {}

  /** Ba thẻ tổng quan của dashboard tiến độ, mọi con số đều tính ở backend. */
  public record ProgressOverviewView(
      String stage,
      OnTrackView onTrack,
      GoalProgressView goalProgress,
      AttendanceView attendance,
      List<MilestoneLevelView> milestoneLevels,
      List<SelfRatingPointView> selfRatings,
      ForecastView forecast,
      RadarView radar
  ) {}

  /**
   * Dự báo "còn X buổi cho Y cột mốc". {@code basis} = PACE (nhịp thực tế) hoặc SUGGESTED (số buổi gợi ý);
   * {@code estimatedFinishDate} chỉ có khi lớp học đều. Không dự báo được thì chỉ có {@code unavailableReason}.
   */
  public record ForecastView(
      Integer remainingSessions,
      Integer remainingMilestones,
      String basis,
      LocalDate estimatedFinishDate,
      String unavailableReason
  ) {}

  /** Radar năng lực theo chủ đề chuẩn; ít hơn 3 trục thì {@code axes} rỗng và có {@code unavailableReason}. */
  public record RadarView(List<RadarAxisView> axes, String unavailableReason) {}

  /** {@code level}: mức tự lập 1–4 của lần chấm gần nhất trong chủ đề. */
  public record RadarAxisView(UUID topicId, String topicName, int level) {}

  /**
   * Cảm nhận 1 chạm của học sinh trong cửa sổ 30 ngày, cũ trước mới sau, để phụ huynh xem xu hướng.
   * Rỗng khi người xem là gia sư.
   */
  public record SelfRatingPointView(UUID sessionId, Instant startAt, int rating) {}

  /** Thẻ 1 — "Có đúng hướng không?". */
  public record OnTrackView(String status, String explanation) {}

  /** Thẻ 2 — "Tiến độ mục tiêu chính". {@code progressDelta} so với đầu cửa sổ 30 ngày cuốn chiếu. */
  public record GoalProgressView(
      String goalTitle,
      Integer progressPercentage,
      Integer progressDelta,
      String highlightKind,
      String highlight,
      String reinforcement,
      String latestComment,
      boolean planAdjusted,
      LocalDate windowStart,
      LocalDate windowEnd
  ) {}

  /**
   * Thẻ 3 — "Chuyên cần & buổi tới". {@code focusMilestoneTitle} là cột mốc dự kiến của buổi tới nếu gia sư đã gắn
   * ({@code nextSessionPlanned = true}), ngược lại là cột mốc đang học.
   */
  public record AttendanceView(
      int attendedCount,
      int countedCount,
      Integer attendanceRate,
      int absentCount,
      int familyLateCancelCount,
      int familyEarlyCancelCount,
      int tutorCancelCount,
      Instant nextSessionStart,
      String focusMilestoneTitle,
      boolean nextSessionPlanned,
      boolean homeworkAssigned,
      List<MissedSessionView> missedSessions
  ) {}

  public record MissedSessionView(UUID sessionId, Instant startAt, String kind) {}

  /** Mức đầu vào (lần chấm đầu) và mức hiện tại của một cột mốc theo thang tự lập 1–4. */
  public record MilestoneLevelView(UUID milestoneId, Integer startLevel, Integer currentLevel, int sessionCount) {}

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

package vn.edufit.progress.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.progress.application.dto.ProgressViews.AttendanceView;
import vn.edufit.progress.application.dto.ProgressViews.ForecastView;
import vn.edufit.progress.application.dto.ProgressViews.GoalProgressView;
import vn.edufit.progress.application.dto.ProgressViews.MilestoneLevelView;
import vn.edufit.progress.application.dto.ProgressViews.MissedSessionView;
import vn.edufit.progress.application.dto.ProgressViews.OnTrackView;
import vn.edufit.progress.application.dto.ProgressViews.ProgressOverviewView;
import vn.edufit.progress.application.dto.ProgressViews.RadarAxisView;
import vn.edufit.progress.application.dto.ProgressViews.RadarView;
import vn.edufit.progress.application.dto.ProgressViews.SelfRatingPointView;
import vn.edufit.progress.application.dto.SessionNoteTimelineView;
import vn.edufit.progress.domain.model.HomeworkStatus;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneAssessment;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.model.Topic;
import vn.edufit.progress.domain.policy.AttendancePolicy;
import vn.edufit.progress.domain.policy.AttendancePolicy.ClassifiedSession;
import vn.edufit.progress.domain.policy.ForecastPolicy;
import vn.edufit.progress.domain.policy.GoalProgressPolicy;
import vn.edufit.progress.domain.policy.OnTrackPolicy;
import vn.edufit.progress.domain.policy.RoadmapShapePolicy;
import vn.edufit.progress.domain.policy.SessionMilestoneResolver;
import vn.edufit.progress.domain.policy.TopicRadarPolicy;
import vn.edufit.progress.domain.repository.PlanHistoryDomainRepository;
import vn.edufit.progress.domain.repository.SessionFeedbackDomainRepository;
import vn.edufit.progress.domain.repository.SessionMilestoneLinkDomainRepository;
import vn.edufit.progress.domain.repository.TopicDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;

/**
 * Tổng hợp 3 thẻ tổng quan (đúng hướng, tiến độ mục tiêu chính, chuyên cần) cho một lớp học.
 * Toàn bộ con số và câu giải thích được tính ở đây để frontend chỉ hiển thị.
 */
@Service
public class ProgressOverviewService {

  /** Các thao tác trên lộ trình làm % của 30 ngày trước không còn so sánh trực tiếp được. */
  static final Set<String> PLAN_ADJUSTMENT_ACTIONS = Set.of(
      "MILESTONE_CREATED", "MILESTONE_ARCHIVED", "MILESTONE_DELETED",
      LearningPlanService.ACTION_UPDATED, LearningPlanService.ACTION_REOPENED
  );

  static final String NO_PLAN_REASON = "Gia sư chưa lập lộ trình";

  private final SessionFeedbackDomainRepository feedbackRepository;
  private final SessionMilestoneLinkDomainRepository linkRepository;
  private final PlanHistoryDomainRepository historyRepository;
  private final TopicDomainRepository topicRepository;
  private final SessionFacade sessionFacade;
  private final ProfileFacade profileFacade;
  private final ConnectionFacade connectionFacade;
  private final Clock clock;

  public ProgressOverviewService(
      SessionFeedbackDomainRepository feedbackRepository,
      SessionMilestoneLinkDomainRepository linkRepository,
      PlanHistoryDomainRepository historyRepository,
      TopicDomainRepository topicRepository,
      SessionFacade sessionFacade,
      ProfileFacade profileFacade,
      ConnectionFacade connectionFacade,
      Clock clock
  ) {
    this.feedbackRepository = feedbackRepository;
    this.linkRepository = linkRepository;
    this.historyRepository = historyRepository;
    this.topicRepository = topicRepository;
    this.sessionFacade = sessionFacade;
    this.profileFacade = profileFacade;
    this.connectionFacade = connectionFacade;
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * @param plan     lộ trình của lớp, null nếu gia sư chưa lập.
   * @param timeline sổ liên lạc của lớp đã lọc theo người xem (nhận xét mới nhất, bài về nhà, cảm nhận của học sinh).
   */
  public ProgressOverviewView build(
      ConnectionFacade.ClassDetailView cls,
      LearningPlan plan,
      String goalTitle,
      List<SessionNoteTimelineView> timeline
  ) {
    Instant now = clock.instant();
    LocalDate today = LocalDate.now(clock);
    ZoneId zone = clock.getZone();
    GoalProgressPolicy.Window window = GoalProgressPolicy.window(now, today);

    List<ClassifiedSession> sessions = classifySessions(cls);
    AttendancePolicy.Summary attendance = AttendancePolicy.summarize(sessions, now);

    List<Milestone> active = plan != null ? plan.getActiveMilestones() : List.of();
    Map<UUID, List<MilestoneAssessment>> assessments = feedbackRepository
        .findAssessmentsByMilestoneIdIn(active.stream().map(Milestone::getMilestoneId).toList())
        .stream()
        .collect(Collectors.groupingBy(MilestoneAssessment::getMilestoneId));
    Optional<Milestone> focus = plan != null ? plan.findFocusMilestone() : Optional.empty();

    Optional<String> nextPlanned = plannedTitle(plan, attendance.nextSessionId());

    // Hạn hiệu lực: hạn gia sư đặt hoặc ngày nội suy giữa hai mốc đã biết (mục 3.4)
    Map<UUID, RoadmapShapePolicy.ExpectedDate> expected = plan != null ? plan.expectedTargetDates() : Map.of();
    OnTrackPolicy.Result onTrack = OnTrackPolicy.evaluate(
        plan != null,
        active.stream()
            .map(m -> new OnTrackPolicy.MilestoneSchedule(
                m.getTitle(),
                Optional.ofNullable(expected.get(m.getMilestoneId())).map(RoadmapShapePolicy.ExpectedDate::date).orElse(null),
                m.getStatus() == MilestoneStatus.ACHIEVED,
                m.getAchievedDate()))
            .toList(),
        attendance.attendedCount() > 0,
        sessions,
        plan != null ? plan.getCreatedAt().atZone(zone).toLocalDate() : today,
        today,
        zone
    );

    GoalProgressPolicy.Result goal = GoalProgressPolicy.evaluate(
        plan != null,
        plan != null ? plan.calculateProgressPercentage() : null,
        plan != null ? plan.calculateProgressPercentageAt(window.start(), window.startDate()) : null,
        active.stream().map(m -> toSnapshot(m, assessments.getOrDefault(m.getMilestoneId(), List.of()))).toList(),
        focus.map(Milestone::getTitle).orElse(null),
        window
    );

    Map<UUID, GoalProgressPolicy.LevelSummary> levelSummaries = active.stream()
        .collect(Collectors.toMap(Milestone::getMilestoneId, m -> GoalProgressPolicy.summarizeLevels(
            toLevelPoints(assessments.getOrDefault(m.getMilestoneId(), List.of())))));
    List<MilestoneLevelView> levels = active.stream()
        .map(m -> {
          GoalProgressPolicy.LevelSummary summary = levelSummaries.get(m.getMilestoneId());
          return new MilestoneLevelView(
              m.getMilestoneId(),
              summary.startLevel() != null ? summary.startLevel().value() : null,
              summary.currentLevel() != null ? summary.currentLevel().value() : null,
              summary.sessionCount());
        })
        .toList();

    return new ProgressOverviewView(
        goal.stage().name(),
        new OnTrackView(onTrack.status().name(), onTrack.explanation()),
        new GoalProgressView(
            goalTitle,
            plan != null ? plan.calculateProgressPercentage() : null,
            goal.progressDelta(),
            goal.highlightKind() != null ? goal.highlightKind().name() : null,
            goal.highlight(),
            goal.reinforcement(),
            latestComment(timeline),
            plan != null && isPlanAdjusted(plan, window.start()),
            window.startDate(),
            today
        ),
        new AttendanceView(
            attendance.attendedCount(),
            attendance.countedCount(),
            attendance.attendanceRate(),
            attendance.absentCount(),
            attendance.familyLateCancelCount(),
            attendance.familyEarlyCancelCount(),
            attendance.tutorCancelCount(),
            attendance.nextSessionStart(),
            nextPlanned.orElseGet(() -> focus.map(Milestone::getTitle).orElse(null)),
            nextPlanned.isPresent(),
            latestHomeworkAssigned(timeline),
            AttendancePolicy.missedSessions(sessions).stream()
                .map(s -> new MissedSessionView(s.sessionId(), s.startAt(), s.kind().name()))
                .toList()
        ),
        levels,
        selfRatingTrend(timeline, window.start()),
        forecast(plan, levelSummaries, sessions, now, today),
        radar(plan, assessments)
    );
  }

  private static ForecastView forecast(
      LearningPlan plan,
      Map<UUID, GoalProgressPolicy.LevelSummary> levelSummaries,
      List<ClassifiedSession> sessions,
      Instant now,
      LocalDate today
  ) {
    if (plan == null) return new ForecastView(null, null, null, null, NO_PLAN_REASON);
    Instant cadenceStart = now.minus(Duration.ofDays(ForecastPolicy.CADENCE_DAYS));
    int recentCompleted = (int) sessions.stream()
        .filter(s -> s.kind() == AttendancePolicy.AttendanceKind.COMPLETED
            && !s.startAt().isBefore(cadenceStart) && !s.startAt().isAfter(now))
        .count();
    ForecastPolicy.Result result = ForecastPolicy.evaluate(
        plan.orderedActiveMilestones().stream()
            .map(m -> new ForecastPolicy.MilestoneEffort(
                m.getStatus() == MilestoneStatus.ACHIEVED,
                m.getSuggestedSessions(),
                levelSummaries.get(m.getMilestoneId()).sessionCount()))
            .toList(),
        recentCompleted,
        today);
    ForecastPolicy.Forecast f = result.forecast();
    return f == null
        ? new ForecastView(null, null, null, null, result.unavailableReason())
        : new ForecastView(f.remainingSessions(), f.remainingMilestones(), f.basis().name(), f.estimatedFinishDate(), null);
  }

  private RadarView radar(LearningPlan plan, Map<UUID, List<MilestoneAssessment>> assessments) {
    if (plan == null) return new RadarView(List.of(), NO_PLAN_REASON);
    List<Milestone> tagged = plan.getActiveMilestones().stream().filter(m -> m.getTopicId() != null).toList();
    Map<UUID, Topic> topics = topicRepository
        .findByIdIn(tagged.stream().map(Milestone::getTopicId).collect(Collectors.toSet()))
        .stream()
        .collect(Collectors.toMap(Topic::getTopicId, t -> t));
    List<TopicRadarPolicy.TopicAssessment> latest = tagged.stream()
        .filter(m -> topics.containsKey(m.getTopicId()))
        .flatMap(m -> assessments.getOrDefault(m.getMilestoneId(), List.of()).stream()
            .max(Comparator.comparing(MilestoneAssessment::getCreatedAt))
            .map(a -> {
              Topic t = topics.get(m.getTopicId());
              return new TopicRadarPolicy.TopicAssessment(
                  t.getTopicId(), t.getName(), t.getSortOrder(), a.getLevel(), a.getCreatedAt());
            })
            .stream())
        .toList();
    TopicRadarPolicy.Result result = TopicRadarPolicy.evaluate(!tagged.isEmpty(), latest);
    return new RadarView(
        result.axes().stream().map(a -> new RadarAxisView(a.topicId(), a.topicName(), a.level())).toList(),
        result.unavailableReason());
  }

  private static List<SelfRatingPointView> selfRatingTrend(List<SessionNoteTimelineView> timeline, Instant windowStart) {
    return timeline.stream()
        .filter(t -> t.selfRating() != null && !t.startAt().isBefore(windowStart))
        .sorted(Comparator.comparing(SessionNoteTimelineView::startAt))
        .map(t -> new SelfRatingPointView(t.sessionId(), t.startAt(), t.selfRating()))
        .toList();
  }

  private List<ClassifiedSession> classifySessions(ConnectionFacade.ClassDetailView cls) {
    UUID tutorUserId = profileFacade.findTutorById(cls.tutorId()).map(TutorSummaryDto::userId).orElse(null);
    Set<UUID> familyUserIds = new HashSet<>(connectionFacade.findConfirmedParentUserIds(cls.studentId()));
    profileFacade.findStudentById(cls.studentId()).map(StudentSummaryDto::userId).ifPresent(familyUserIds::add);

    return sessionFacade.findAttendanceByClassId(cls.classId()).stream()
        .map(s -> AttendancePolicy.classify(
            new AttendancePolicy.SessionRecord(s.sessionId(), s.startAt(), s.status(), s.cancelledBy(), s.lateCancel()),
            tutorUserId,
            familyUserIds))
        .flatMap(Optional::stream)
        .toList();
  }

  /** Tên (các) cột mốc dự kiến của buổi tới, nối bằng " · ". */
  private Optional<String> plannedTitle(LearningPlan plan, UUID nextSessionId) {
    if (plan == null || nextSessionId == null) return Optional.empty();
    SessionMilestoneResolver.Resolved resolved =
        SessionMilestoneResolver.resolve(linkRepository.findBySessionIdIn(List.of(nextSessionId))).get(nextSessionId);
    if (resolved == null) return Optional.empty();
    String titles = resolved.milestoneIds().stream()
        .map(plan::findActiveMilestone)
        .flatMap(Optional::stream)
        .map(Milestone::getTitle)
        .collect(Collectors.joining(" · "));
    return titles.isEmpty() ? Optional.empty() : Optional.of(titles);
  }

  private boolean isPlanAdjusted(LearningPlan plan, Instant windowStart) {
    if (plan.getCreatedAt().isAfter(windowStart)) return false; // Lộ trình mới lập trong kỳ: so với 0%
    return historyRepository.findByPlanId(plan.getPlanId()).stream()
        .anyMatch(h -> h.getCreatedAt().isAfter(windowStart) && PLAN_ADJUSTMENT_ACTIONS.contains(h.getAction()));
  }

  private static GoalProgressPolicy.MilestoneSnapshot toSnapshot(Milestone m, List<MilestoneAssessment> assessments) {
    return new GoalProgressPolicy.MilestoneSnapshot(
        m.getMilestoneId(),
        m.getTitle(),
        m.getStatus() == MilestoneStatus.ACHIEVED,
        m.getAchievedDate(),
        m.getCriteria().stream()
            .map(c -> new GoalProgressPolicy.CriterionSnapshot(c.getContent(), c.isChecked(), c.getCheckedAt()))
            .toList(),
        toLevelPoints(assessments)
    );
  }

  private static List<GoalProgressPolicy.LevelPoint> toLevelPoints(List<MilestoneAssessment> assessments) {
    return assessments.stream()
        .map(a -> new GoalProgressPolicy.LevelPoint(a.getSessionId(), a.getLevel(), a.getCreatedAt()))
        .toList();
  }

  private static String latestComment(List<SessionNoteTimelineView> timeline) {
    return timeline.stream()
        .filter(t -> t.noteText() != null && !t.noteText().isBlank() && t.recordedAt() != null)
        .max(Comparator.comparing(SessionNoteTimelineView::recordedAt))
        .map(SessionNoteTimelineView::noteText)
        .orElse(null);
  }

  private static boolean latestHomeworkAssigned(List<SessionNoteTimelineView> timeline) {
    return timeline.stream()
        .filter(t -> t.feedback() != null)
        .max(Comparator.comparing(t -> t.feedback().createdAt()))
        .map(t -> HomeworkStatus.valueOf(t.feedback().homeworkStatus()).hasHomework())
        .orElse(false);
  }
}

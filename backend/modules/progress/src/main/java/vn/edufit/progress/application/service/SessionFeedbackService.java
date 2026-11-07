package vn.edufit.progress.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.progress.api.event.MilestoneAchievedEvent;
import vn.edufit.progress.application.dto.SessionFeedbackView;
import vn.edufit.progress.application.dto.SubmitSessionFeedbackCommand;
import vn.edufit.progress.domain.model.IndependenceLevel;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneAssessment;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.model.PlanHistory;
import vn.edufit.progress.domain.model.SessionFeedback;
import vn.edufit.progress.domain.policy.AssessmentCredibilityPolicy;
import vn.edufit.progress.domain.policy.FeedbackTimingPolicy;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.progress.domain.repository.PlanHistoryDomainRepository;
import vn.edufit.progress.domain.repository.SessionFeedbackDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionSummaryView;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Use case phản hồi 10 giây sau buổi học: chấm mức tự lập cho từng cột mốc được dạy (gắn liên kết "thực tế"),
 * tick tiêu chí, tùy chọn chốt cột mốc đạt, chip việc về nhà, chip thái độ và lời nhắn riêng cho phụ huynh —
 * trong một giao dịch. Điểm chấm phải qua {@link AssessmentCredibilityPolicy}; ghi sau 48 giờ thì "bổ sung muộn".
 */
@Slf4j
@Service
public class SessionFeedbackService {

  static final String ACTION_CRITERIA_CHECKED = "MILESTONE_CRITERIA_CHECKED";

  private final SessionFeedbackDomainRepository feedbackRepository;
  private final LearningPlanDomainRepository planRepository;
  private final PlanHistoryDomainRepository historyRepository;
  private final SessionFacade sessionFacade;
  private final ProgressAccessGuard accessGuard;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public SessionFeedbackService(
      SessionFeedbackDomainRepository feedbackRepository,
      LearningPlanDomainRepository planRepository,
      PlanHistoryDomainRepository historyRepository,
      SessionFacade sessionFacade,
      ProgressAccessGuard accessGuard,
      ApplicationEventPublisher events,
      Clock clock
  ) {
    this.feedbackRepository = feedbackRepository;
    this.planRepository = planRepository;
    this.historyRepository = historyRepository;
    this.sessionFacade = sessionFacade;
    this.accessGuard = accessGuard;
    this.events = events;
    this.clock = Objects.requireNonNull(clock);
  }

  @Transactional
  public SessionFeedbackView submit(CurrentUser actor, UUID sessionId, SubmitSessionFeedbackCommand cmd) {
    SessionSummaryView session = sessionFacade.findSessionById(sessionId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy buổi học với ID: " + sessionId));
    ConnectionFacade.ClassDetailView cls = accessGuard.requireClass(session.classId());
    accessGuard.requireTutorOfClass(actor, cls);
    accessGuard.requireActiveClass(cls, "Chỉ có thể phản hồi buổi học của lớp đang hoạt động");

    if (!"COMPLETED".equals(session.status())) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION, "Chỉ ghi phản hồi cho buổi học đã được ghi nhận hoàn thành");
    }
    if (feedbackRepository.existsBySessionId(sessionId)) {
      throw new ResourceConflictException(ErrorCode.CONFLICT_DETECTED, "Buổi học này đã có phản hồi");
    }

    LearningPlan plan = planRepository.findByClassId(cls.classId())
        .orElseThrow(() -> new InvalidOperationException(
            ErrorCode.INVALID_OPERATION, "Lớp học chưa có lộ trình. Hãy lập lộ trình trước khi phản hồi buổi học."));

    List<SubmitSessionFeedbackCommand.TaughtMilestone> taught = cmd.milestones() != null ? cmd.milestones() : List.of();
    LocalDate today = LocalDate.now(clock);
    Instant now = Instant.now(clock);
    Map<UUID, IndependenceLevel> previousLevels = latestLevels(taught);
    List<MilestoneAssessment> assessments = new ArrayList<>();
    List<Milestone> newlyAchieved = new ArrayList<>();

    for (SubmitSessionFeedbackCommand.TaughtMilestone item : taught) {
      Milestone milestone = plan.findActiveMilestone(item.milestoneId())
          .orElseThrow(() -> new InvalidOperationException(
              ErrorCode.VALIDATION_FAILED, "Cột mốc " + item.milestoneId() + " không thuộc lộ trình của lớp"));
      if (item.level() == null) {
        throw new InvalidOperationException(
            ErrorCode.VALIDATION_FAILED, "Cần chọn mức tự lập cho cột mốc \"" + milestone.getTitle() + "\"");
      }
      MilestoneAssessment assessment = new MilestoneAssessment(
          UUID.randomUUID(),
          milestone.getMilestoneId(),
          sessionId,
          IndependenceLevel.fromValue(item.level()),
          item.correctCount(),
          item.totalCount(),
          item.evidenceNote(),
          actor.getUserId(),
          now
      );
      applyCriteriaChecks(plan, milestone, item, actor, now);
      AssessmentCredibilityPolicy.check(previousLevels.get(milestone.getMilestoneId()), assessment, milestone);
      assessments.add(assessment);
      applyStatusChanges(plan, milestone, item, actor, today, now, newlyAchieved);
    }

    SessionFeedback feedback = new SessionFeedback(
        UUID.randomUUID(),
        sessionId,
        cls.classId(),
        cmd.homeworkStatus(),
        cmd.attitudeChips(),
        cmd.parentMessage(),
        actor.getUserId(),
        now,
        FeedbackTimingPolicy.isLate(session.endAt(), now),
        assessments
    );

    planRepository.save(plan);
    SessionFeedback saved = feedbackRepository.save(feedback);
    newlyAchieved.forEach(m -> events.publishEvent(
        MilestoneAchievedEvent.of(cls.classId(), cls.studentId(), m.getMilestoneId(), m.getTitle())));

    log.info("Ghi phản hồi buổi học thành công: sessionId={}, milestones={}, achieved={}",
        sessionId, assessments.size(), newlyAchieved.size());
    Map<UUID, String> titles = plan.getMilestones().stream()
        .collect(Collectors.toMap(Milestone::getMilestoneId, Milestone::getTitle));
    return SessionFeedbackView.from(saved, titles, true);
  }

  /** Mức của lần chấm gần nhất trước buổi này cho từng cột mốc được dạy (không có = điểm xuất phát). */
  private Map<UUID, IndependenceLevel> latestLevels(List<SubmitSessionFeedbackCommand.TaughtMilestone> taught) {
    List<UUID> milestoneIds = taught.stream().map(SubmitSessionFeedbackCommand.TaughtMilestone::milestoneId).toList();
    return feedbackRepository.findAssessmentsByMilestoneIdIn(milestoneIds).stream()
        .collect(Collectors.toMap(
            MilestoneAssessment::getMilestoneId,
            MilestoneAssessment::getLevel,
            (older, newer) -> newer)); // repository trả về theo thời gian tăng dần
  }

  private void applyCriteriaChecks(
      LearningPlan plan,
      Milestone milestone,
      SubmitSessionFeedbackCommand.TaughtMilestone item,
      CurrentUser actor,
      Instant now
  ) {
    if (item.checkedCriterionIds() != null && milestone.applyCriteriaChecks(item.checkedCriterionIds(), now)) {
      historyRepository.save(PlanHistory.of(
          plan.getPlanId(), milestone.getMilestoneId(), ACTION_CRITERIA_CHECKED, actor.getUserId(), null,
          checkedSummary(milestone), now
      ));
    }
  }

  private void applyStatusChanges(
      LearningPlan plan,
      Milestone milestone,
      SubmitSessionFeedbackCommand.TaughtMilestone item,
      CurrentUser actor,
      LocalDate today,
      Instant now,
      List<Milestone> newlyAchieved
  ) {
    UUID planId = plan.getPlanId();
    if (milestone.startProgress()) {
      historyRepository.save(PlanHistory.of(
          planId, milestone.getMilestoneId(), LearningPlanService.ACTION_STATUS_CHANGED, actor.getUserId(),
          MilestoneStatus.NOT_STARTED.name(), MilestoneStatus.IN_PROGRESS.name(), now
      ));
    }
    if (item.markAchieved()) {
      MilestoneStatus before = milestone.getStatus();
      milestone.achieve(today, today);
      historyRepository.save(PlanHistory.of(
          planId, milestone.getMilestoneId(), LearningPlanService.ACTION_STATUS_CHANGED, actor.getUserId(),
          before.name(), MilestoneStatus.ACHIEVED.name(), now
      ));
      newlyAchieved.add(milestone);
    }
  }

  private static String checkedSummary(Milestone milestone) {
    long checked = milestone.getCriteria().stream().filter(c -> c.isChecked()).count();
    return checked + "/" + milestone.getCriteria().size();
  }
}

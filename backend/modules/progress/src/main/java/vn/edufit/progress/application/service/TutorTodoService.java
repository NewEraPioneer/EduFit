package vn.edufit.progress.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.progress.application.dto.TutorTodoView;
import vn.edufit.progress.application.dto.TutorTodoView.ClassItem;
import vn.edufit.progress.application.dto.TutorTodoView.MissingFeedbackItem;
import vn.edufit.progress.application.dto.TutorTodoView.OverdueMilestoneItem;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.policy.FeedbackTimingPolicy;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.progress.domain.repository.SessionFeedbackDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionSummaryView;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;

/**
 * Ngăn "Việc cần làm" của gia sư (mục 3.14): buổi chưa phản hồi, lớp chưa có lộ trình, cột mốc quá hạn,
 * lộ trình thiếu ngày cuối. Chỉ xét lớp đang hoạt động.
 */
@Service
public class TutorTodoService {

  /** Chỉ nhắc buổi chưa phản hồi trong 30 ngày gần nhất (khớp cửa sổ cuốn chiếu của dashboard). */
  static final Duration FEEDBACK_LOOKBACK = Duration.ofDays(30);

  private final ConnectionFacade connectionFacade;
  private final ProfileFacade profileFacade;
  private final SessionFacade sessionFacade;
  private final LearningPlanDomainRepository planRepository;
  private final SessionFeedbackDomainRepository feedbackRepository;
  private final ProgressDisplayNames names;
  private final Clock clock;

  public TutorTodoService(
      ConnectionFacade connectionFacade,
      ProfileFacade profileFacade,
      SessionFacade sessionFacade,
      LearningPlanDomainRepository planRepository,
      SessionFeedbackDomainRepository feedbackRepository,
      ProgressDisplayNames names,
      Clock clock
  ) {
    this.connectionFacade = connectionFacade;
    this.profileFacade = profileFacade;
    this.sessionFacade = sessionFacade;
    this.planRepository = planRepository;
    this.feedbackRepository = feedbackRepository;
    this.names = names;
    this.clock = Objects.requireNonNull(clock);
  }

  @Transactional(readOnly = true)
  public TutorTodoView getTodos(CurrentUser actor) {
    if (!actor.hasRole("TUTOR")) {
      throw new ForbiddenOperationException("Chỉ tài khoản Gia sư mới có ngăn Việc cần làm");
    }
    UUID tutorId = profileFacade.getTutorIdByUserId(actor.getUserId());
    List<ConnectionFacade.ClassDetailView> classes = tutorId == null ? List.of()
        : connectionFacade.findClassesByTutor(tutorId).stream().filter(c -> "ACTIVE".equals(c.status())).toList();
    if (classes.isEmpty()) return new TutorTodoView(List.of(), List.of(), List.of(), List.of());

    Instant now = clock.instant();
    LocalDate today = LocalDate.now(clock);
    List<UUID> classIds = classes.stream().map(ConnectionFacade.ClassDetailView::classId).toList();
    Map<UUID, LearningPlan> plans = planRepository.findByClassIdIn(classIds).stream()
        .collect(Collectors.toMap(LearningPlan::getClassId, p -> p));
    Map<UUID, LearningNeedView> needs = profileFacade.findLearningNeeds(
        classes.stream().map(ConnectionFacade.ClassDetailView::needId).filter(Objects::nonNull).toList());
    Map<UUID, String> studentNames = new HashMap<>();
    classes.forEach(c -> studentNames.computeIfAbsent(c.studentId(), names::studentName));

    List<ClassItem> withoutPlan = new ArrayList<>();
    List<ClassItem> missingFinalDate = new ArrayList<>();
    List<OverdueMilestoneItem> overdue = new ArrayList<>();
    for (ConnectionFacade.ClassDetailView c : classes) {
      String studentName = studentNames.get(c.studentId());
      ClassItem item = new ClassItem(
          c.classId(), studentName,
          ProgressDisplayNames.needTitle(c.needId() != null ? needs.get(c.needId()) : null, "Lớp học"));
      LearningPlan plan = plans.get(c.classId());
      if (plan == null) {
        withoutPlan.add(item);
        continue;
      }
      List<Milestone> active = plan.getActiveMilestones().stream()
          .sorted(Comparator.comparingInt(Milestone::getSortOrder))
          .toList();
      if (!active.isEmpty() && active.getLast().getTargetDate() == null) missingFinalDate.add(item);
      active.stream()
          .filter(m -> m.getStatus() != MilestoneStatus.ACHIEVED && m.getTargetDate() != null
              && m.getTargetDate().isBefore(today))
          .forEach(m -> overdue.add(new OverdueMilestoneItem(c.classId(), studentName, m.getMilestoneId(),
              m.getTitle(), m.getTargetDate(), ChronoUnit.DAYS.between(m.getTargetDate(), today))));
    }
    overdue.sort(Comparator.comparingLong(OverdueMilestoneItem::daysLate).reversed());

    return new TutorTodoView(
        missingFeedback(classes, plans, studentNames, now), withoutPlan, overdue, missingFinalDate);
  }

  /** Buổi đã hoàn thành của lớp có lộ trình (mới phản hồi được) mà chưa có phản hồi, cũ nhất trước. */
  private List<MissingFeedbackItem> missingFeedback(
      List<ConnectionFacade.ClassDetailView> classes,
      Map<UUID, LearningPlan> plans,
      Map<UUID, String> studentNames,
      Instant now
  ) {
    List<UUID> plannedClassIds = classes.stream().map(ConnectionFacade.ClassDetailView::classId)
        .filter(plans::containsKey).toList();
    if (plannedClassIds.isEmpty()) return List.of();
    Map<UUID, UUID> studentByClass = classes.stream()
        .collect(Collectors.toMap(ConnectionFacade.ClassDetailView::classId, ConnectionFacade.ClassDetailView::studentId));

    List<SessionSummaryView> completed = sessionFacade
        .findSessionSummariesByClassIds(plannedClassIds, now.minus(FEEDBACK_LOOKBACK), now).stream()
        .filter(s -> "COMPLETED".equals(s.status()))
        .toList();
    Set<UUID> withFeedback = feedbackRepository.findSessionIdsWithFeedback(
        completed.stream().map(SessionSummaryView::sessionId).toList());
    return completed.stream()
        .filter(s -> !withFeedback.contains(s.sessionId()))
        .sorted(Comparator.comparing(SessionSummaryView::endAt))
        .map(s -> new MissingFeedbackItem(
            s.sessionId(),
            s.classId(),
            Optional.ofNullable(studentByClass.get(s.classId())).map(studentNames::get).orElse(null),
            s.startAt(),
            s.endAt(),
            FeedbackTimingPolicy.isLate(s.endAt(), now)))
        .toList();
  }
}

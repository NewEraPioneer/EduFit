package vn.edufit.progress.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.policy.FeedbackTimingPolicy;
import vn.edufit.progress.domain.policy.FeedbackTimingPolicy.ReminderStage;
import vn.edufit.progress.domain.repository.FeedbackReminderDomainRepository;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.progress.domain.repository.SessionFeedbackDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionSummaryView;
import vn.edufit.shared.notification.NotificationSink;

/**
 * Nhắc gia sư hoàn tất phản hồi 10 giây đã "để sau" (mục 3.6): mốc +12h và +24h kể từ khi buổi học kết thúc,
 * mỗi mốc gửi tối đa một lần. Chỉ nhắc buổi đã hoàn thành của lớp đã có lộ trình (lớp chưa có lộ trình
 * nằm ở ngăn "Việc cần làm").
 */
@Slf4j
@Service
public class FeedbackReminderService {

  static final String NOTIFICATION_TYPE = "SESSION_FEEDBACK_REMINDER";

  private final SessionFacade sessionFacade;
  private final SessionFeedbackDomainRepository feedbackRepository;
  private final FeedbackReminderDomainRepository reminderRepository;
  private final LearningPlanDomainRepository planRepository;
  private final ProfileFacade profileFacade;
  private final NotificationSink notifications;
  private final Clock clock;

  public FeedbackReminderService(
      SessionFacade sessionFacade,
      SessionFeedbackDomainRepository feedbackRepository,
      FeedbackReminderDomainRepository reminderRepository,
      LearningPlanDomainRepository planRepository,
      ProfileFacade profileFacade,
      NotificationSink notifications,
      Clock clock
  ) {
    this.sessionFacade = sessionFacade;
    this.feedbackRepository = feedbackRepository;
    this.reminderRepository = reminderRepository;
    this.planRepository = planRepository;
    this.profileFacade = profileFacade;
    this.notifications = notifications;
    this.clock = Objects.requireNonNull(clock);
  }

  /** @return số lần nhắc đã gửi trong lượt quét này. */
  @Transactional
  public int sendDueReminders() {
    Instant now = clock.instant();
    List<SessionSummaryView> candidates = sessionFacade.findCompletedSessionsEndedBetween(
        FeedbackTimingPolicy.reminderWindowStart(now), FeedbackTimingPolicy.reminderWindowEnd(now));
    if (candidates.isEmpty()) return 0;

    List<UUID> sessionIds = candidates.stream().map(SessionSummaryView::sessionId).toList();
    Set<UUID> withFeedback = feedbackRepository.findSessionIdsWithFeedback(sessionIds);
    Set<UUID> classesWithPlan = planRepository
        .findByClassIdIn(candidates.stream().map(SessionSummaryView::classId).distinct().toList())
        .stream()
        .map(LearningPlan::getClassId)
        .collect(Collectors.toSet());
    Map<UUID, Set<ReminderStage>> sent = reminderRepository.findSentStagesBySessionIdIn(sessionIds);

    int count = 0;
    for (SessionSummaryView s : candidates) {
      if (withFeedback.contains(s.sessionId()) || !classesWithPlan.contains(s.classId())) continue;
      Optional<ReminderStage> stage = FeedbackTimingPolicy.dueStage(
          s.endAt(), now, sent.getOrDefault(s.sessionId(), Set.of()));
      if (stage.isEmpty()) continue;
      Optional<UUID> tutorUserId = profileFacade.findTutorById(s.tutorId()).map(TutorSummaryDto::userId);
      if (tutorUserId.isEmpty()) continue;

      notifications.notify(
          tutorUserId.get(),
          s.studentId(),
          NOTIFICATION_TYPE,
          "Nhắc phản hồi buổi học",
          message(stage.get()),
          "SESSION",
          s.sessionId());
      reminderRepository.recordSent(s.sessionId(), stage.get(), now);
      count++;
    }
    if (count > 0) log.info("Đã nhắc gia sư phản hồi {} buổi học", count);
    return count;
  }

  private static String message(ReminderStage stage) {
    long hours = stage.after().toHours();
    long left = FeedbackTimingPolicy.LATE_AFTER.toHours() - hours;
    return "Buổi học đã kết thúc " + hours + " giờ nhưng chưa có phản hồi 10 giây. Sau " + left
        + " giờ nữa phản hồi sẽ được đánh dấu \"bổ sung muộn\".";
  }
}

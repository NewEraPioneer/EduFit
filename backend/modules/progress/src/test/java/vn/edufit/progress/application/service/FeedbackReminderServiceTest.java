package vn.edufit.progress.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import org.mockito.ArgumentCaptor;
import vn.edufit.progress.api.event.FeedbackReminderDueEvent;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.policy.FeedbackTimingPolicy;
import vn.edufit.progress.domain.policy.FeedbackTimingPolicy.ReminderStage;
import vn.edufit.progress.domain.repository.FeedbackReminderDomainRepository;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.progress.domain.repository.SessionFeedbackDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionSummaryView;
import org.springframework.context.ApplicationEventPublisher;

class FeedbackReminderServiceTest {

  private final Instant now = Instant.parse("2026-11-02T10:00:00Z");
  private final UUID tutorId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID classWithPlan = UUID.randomUUID();
  private final UUID classWithoutPlan = UUID.randomUUID();

  private SessionFacade sessionFacade;
  private SessionFeedbackDomainRepository feedbackRepository;
  private FeedbackReminderDomainRepository reminderRepository;
  private ApplicationEventPublisher events;
  private FeedbackReminderService service;

  @BeforeEach
  void setUp() {
    sessionFacade = mock(SessionFacade.class);
    feedbackRepository = mock(SessionFeedbackDomainRepository.class);
    reminderRepository = mock(FeedbackReminderDomainRepository.class);
    LearningPlanDomainRepository planRepository = mock(LearningPlanDomainRepository.class);
    ProfileFacade profileFacade = mock(ProfileFacade.class);
    events = mock(ApplicationEventPublisher.class);
    service = new FeedbackReminderService(sessionFacade, feedbackRepository, reminderRepository, planRepository,
        events, Clock.fixed(now, ZoneId.of("UTC")));

    TutorSummaryDto tutor = mock(TutorSummaryDto.class);
    when(tutor.userId()).thenReturn(tutorUserId);
    when(profileFacade.findTutorById(tutorId)).thenReturn(Optional.of(tutor));
    when(planRepository.findByClassIdIn(any())).thenReturn(List.of(
        new LearningPlan(UUID.randomUUID(), classWithPlan, null, 0, now, now, List.of())));
  }

  private SessionSummaryView endedAgo(Duration ago, UUID classId) {
    Instant end = now.minus(ago);
    return new SessionSummaryView(UUID.randomUUID(), classId, tutorId, UUID.randomUUID(), end.minusSeconds(5400), end,
        "ONLINE", "COMPLETED", false, end.minusSeconds(86400));
  }

  @Test
  @DisplayName("Quét đúng cửa sổ (now−48h, now−12h], nhắc mốc đến hạn và ghi nhật ký chống gửi trùng")
  void sendDueReminders_notifiesTutorOncePerStage() {
    SessionSummaryView at13h = endedAgo(Duration.ofHours(13), classWithPlan);
    SessionSummaryView at25hAlreadyReminded = endedAgo(Duration.ofHours(25), classWithPlan);
    SessionSummaryView withFeedback = endedAgo(Duration.ofHours(30), classWithPlan);
    SessionSummaryView noPlan = endedAgo(Duration.ofHours(13), classWithoutPlan);
    when(sessionFacade.findCompletedSessionsEndedBetween(
        FeedbackTimingPolicy.reminderWindowStart(now), FeedbackTimingPolicy.reminderWindowEnd(now)))
        .thenReturn(List.of(at13h, at25hAlreadyReminded, withFeedback, noPlan));
    when(feedbackRepository.findSessionIdsWithFeedback(any())).thenReturn(Set.of(withFeedback.sessionId()));
    when(reminderRepository.findSentStagesBySessionIdIn(any())).thenReturn(Map.of(
        at25hAlreadyReminded.sessionId(), EnumSet.of(ReminderStage.H12, ReminderStage.H24)));

    assertThat(service.sendDueReminders()).isEqualTo(1);

    ArgumentCaptor<FeedbackReminderDueEvent> published = ArgumentCaptor.forClass(FeedbackReminderDueEvent.class);
    verify(events).publishEvent(published.capture());
    FeedbackReminderDueEvent event = published.getValue();
    assertThat(event.sessionId()).isEqualTo(at13h.sessionId());
    assertThat(event.studentId()).isEqualTo(at13h.studentId());
    assertThat(event.tutorId()).isEqualTo(tutorId);
    assertThat(event.hoursSinceEnd()).isEqualTo(12);
    assertThat(event.hoursLeft()).isEqualTo(FeedbackTimingPolicy.LATE_AFTER.toHours() - 12);
    verify(reminderRepository).recordSent(at13h.sessionId(), ReminderStage.H12, now);
  }

  @Test
  @DisplayName("Không có buổi nào trong cửa sổ thì không truy vấn thêm")
  void sendDueReminders_noCandidates() {
    when(sessionFacade.findCompletedSessionsEndedBetween(any(), any())).thenReturn(List.of());
    assertThat(service.sendDueReminders()).isZero();
    verify(feedbackRepository, never()).findSessionIdsWithFeedback(any());
  }
}

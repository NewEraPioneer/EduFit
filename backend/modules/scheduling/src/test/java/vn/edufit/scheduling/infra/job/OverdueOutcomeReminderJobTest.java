package vn.edufit.scheduling.infra.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import vn.edufit.scheduling.api.event.OverdueOutcomeReminderEvent;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test: OverdueOutcomeReminderJob - Quét nhắc nhở ghi nhận kết quả ca học (FR-20)")
class OverdueOutcomeReminderJobTest {

  @Mock
  private TutoringSessionDomainRepository sessionRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private final Instant fixedNow = Instant.parse("2026-10-15T18:00:00Z");
  private final Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

  private OverdueOutcomeReminderJob job;

  @BeforeEach
  void setUp() {
    job = new OverdueOutcomeReminderJob(sessionRepository, eventPublisher, fixedClock);
  }

  @Test
  @DisplayName("Khi không có buổi học nào kết thúc chưa ghi nhận kết quả, không bắn sự kiện")
  void scanAndRemindOverdueOutcomes_whenEmpty_doesNothing() {
    when(sessionRepository.findOverdueOutcomeSessions(fixedNow)).thenReturn(List.of());

    job.scanAndRemindOverdueOutcomes();

    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("Quét phát hiện buổi học đã kết thúc trong vòng 48h chưa ghi nhận kết quả, phát sinh sự kiện nhắc nhở")
  void scanAndRemindOverdueOutcomes_withEligibleSessions_publishesEvent() {
    UUID sessionId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID classId = UUID.randomUUID();

    Instant startAt = fixedNow.minus(3, ChronoUnit.HOURS);
    Instant endAt = fixedNow.minus(1, ChronoUnit.HOURS); // kết thúc cách đây 1 giờ

    TutoringSession overdueSession = new TutoringSession(
        sessionId, classId, tutorId, studentId,
        startAt, endAt,
        SessionMode.ONLINE, "link", null, null,
        SessionStatus.SCHEDULED, tutorId, studentId,
        fixedNow.minus(2, ChronoUnit.DAYS), null,
        startAt, null, null, null, null, false,
        null, null, 1, fixedNow.minus(3, ChronoUnit.DAYS)
    );

    when(sessionRepository.findOverdueOutcomeSessions(fixedNow)).thenReturn(List.of(overdueSession));

    job.scanAndRemindOverdueOutcomes();

    ArgumentCaptor<OverdueOutcomeReminderEvent> eventCaptor =
        ArgumentCaptor.forClass(OverdueOutcomeReminderEvent.class);
    verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

    OverdueOutcomeReminderEvent event = eventCaptor.getValue();
    assertThat(event.sessionId()).isEqualTo(sessionId);
    assertThat(event.tutorId()).isEqualTo(tutorId);
    assertThat(event.classId()).isEqualTo(classId);
    assertThat(event.endAt()).isEqualTo(endAt);
  }

  @Test
  @DisplayName("Bỏ qua các buổi học kết thúc quá 48h (vượt ngoài lookback window) để tránh spam")
  void scanAndRemindOverdueOutcomes_filtersOutVeryOldSessions() {
    UUID sessionId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID classId = UUID.randomUUID();

    Instant startAt = fixedNow.minus(60, ChronoUnit.HOURS);
    Instant endAt = fixedNow.minus(58, ChronoUnit.HOURS); // kết thúc 58h trước (quá 48h)

    TutoringSession veryOldSession = new TutoringSession(
        sessionId, classId, tutorId, studentId,
        startAt, endAt,
        SessionMode.ONLINE, "link", null, null,
        SessionStatus.SCHEDULED, tutorId, studentId,
        fixedNow.minus(5, ChronoUnit.DAYS), null,
        startAt, null, null, null, null, false,
        null, null, 1, fixedNow.minus(6, ChronoUnit.DAYS)
    );

    when(sessionRepository.findOverdueOutcomeSessions(fixedNow)).thenReturn(List.of(veryOldSession));

    job.scanAndRemindOverdueOutcomes();

    verify(eventPublisher, never()).publishEvent(any());
  }
}

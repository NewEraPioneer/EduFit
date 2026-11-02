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
import vn.edufit.scheduling.api.event.SessionUpcomingReminderEvent;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test: SessionReminderJob - Quét nhắc nhở lịch học trước 24h & 2h (FR-31)")
class SessionReminderJobTest {

  @Mock
  private TutoringSessionDomainRepository sessionRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private final Instant fixedNow = Instant.parse("2026-10-15T10:00:00Z");
  private final Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

  private SessionReminderJob job;

  @BeforeEach
  void setUp() {
    job = new SessionReminderJob(sessionRepository, eventPublisher, fixedClock);
  }

  @Test
  @DisplayName("Khi không có buổi học nào sắp tới, không bắn sự kiện nhắc nhở")
  void scanAndSendSessionReminders_whenNoUpcomingSessions_doesNotPublishEvent() {
    Instant window24hStart = fixedNow.plus(24, ChronoUnit.HOURS);
    Instant window24hEnd = window24hStart.plus(10, ChronoUnit.MINUTES);
    Instant window2hStart = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant window2hEnd = window2hStart.plus(10, ChronoUnit.MINUTES);

    when(sessionRepository.findUpcomingSessions(window24hStart, window24hEnd)).thenReturn(List.of());
    when(sessionRepository.findUpcomingSessions(window2hStart, window2hEnd)).thenReturn(List.of());

    job.scanAndSendSessionReminders();

    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("Quét phát hiện buổi học trước 24 giờ và trước 2 giờ, bắn đầy đủ sự kiện tương ứng")
  void scanAndSendSessionReminders_withUpcomingSessions_publishesEventsCorrectly() {
    Instant window24hStart = fixedNow.plus(24, ChronoUnit.HOURS);
    Instant window24hEnd = window24hStart.plus(10, ChronoUnit.MINUTES);
    Instant window2hStart = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant window2hEnd = window2hStart.plus(10, ChronoUnit.MINUTES);

    UUID session24hId = UUID.randomUUID();
    UUID session2hId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID classId = UUID.randomUUID();

    TutoringSession session24h = new TutoringSession(
        session24hId, classId, tutorId, studentId,
        window24hStart.plus(5, ChronoUnit.MINUTES),
        window24hStart.plus(65, ChronoUnit.MINUTES),
        SessionMode.ONLINE, "link", null, null,
        SessionStatus.SCHEDULED, tutorId, studentId,
        fixedNow.minus(1, ChronoUnit.DAYS), null,
        window24hStart, null, null, null, null, false,
        null, null, 1, fixedNow.minus(2, ChronoUnit.DAYS)
    );

    TutoringSession session2h = new TutoringSession(
        session2hId, classId, tutorId, studentId,
        window2hStart.plus(5, ChronoUnit.MINUTES),
        window2hStart.plus(65, ChronoUnit.MINUTES),
        SessionMode.ONLINE, "link", null, null,
        SessionStatus.SCHEDULED, tutorId, studentId,
        fixedNow.minus(1, ChronoUnit.DAYS), null,
        window2hStart, null, null, null, null, false,
        null, null, 1, fixedNow.minus(2, ChronoUnit.DAYS)
    );

    when(sessionRepository.findUpcomingSessions(window24hStart, window24hEnd)).thenReturn(List.of(session24h));
    when(sessionRepository.findUpcomingSessions(window2hStart, window2hEnd)).thenReturn(List.of(session2h));

    job.scanAndSendSessionReminders();

    ArgumentCaptor<SessionUpcomingReminderEvent> eventCaptor =
        ArgumentCaptor.forClass(SessionUpcomingReminderEvent.class);
    verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());

    List<SessionUpcomingReminderEvent> events = eventCaptor.getAllValues();
    assertThat(events).hasSize(2);

    SessionUpcomingReminderEvent event24h = events.stream()
        .filter(e -> e.sessionId().equals(session24hId))
        .findFirst()
        .orElseThrow();
    assertThat(event24h.hoursBefore()).isEqualTo(24);
    assertThat(event24h.tutorId()).isEqualTo(tutorId);
    assertThat(event24h.studentId()).isEqualTo(studentId);

    SessionUpcomingReminderEvent event2h = events.stream()
        .filter(e -> e.sessionId().equals(session2hId))
        .findFirst()
        .orElseThrow();
    assertThat(event2h.hoursBefore()).isEqualTo(2);
    assertThat(event2h.sessionId()).isEqualTo(session2hId);
  }
}

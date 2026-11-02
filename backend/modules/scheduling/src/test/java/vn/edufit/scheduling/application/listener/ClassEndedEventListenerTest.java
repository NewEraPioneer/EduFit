package vn.edufit.scheduling.application.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.connection.api.event.TutoringClassEndedEvent;
import vn.edufit.scheduling.application.service.RescheduleClassScheduleService;
import vn.edufit.scheduling.domain.model.CancelReason;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;

@ExtendWith(MockitoExtension.class)
class ClassEndedEventListenerTest {

  @Mock
  private TutoringSessionDomainRepository sessionRepository;

  @Mock
  private RescheduleClassScheduleService rescheduleClassScheduleService;

  private final Instant fixedNow = Instant.parse("2026-10-20T10:00:00Z");
  private final Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);

  private ClassEndedEventListener listener;

  @BeforeEach
  void setUp() {
    listener = new ClassEndedEventListener(sessionRepository, rescheduleClassScheduleService, clock);
  }

  @Test
  @DisplayName("Khi nhận TutoringClassEndedEvent, cascade hủy tất cả các buổi học tương lai chưa hoàn thành và hủy đề xuất lịch định kỳ")
  void shouldCancelAllFutureActiveSessionsWhenClassEnds() {
    UUID classId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID endedBy = UUID.randomUUID();

    TutoringSession scheduledSession = TutoringSession.createScheduled(
        UUID.randomUUID(), classId, tutorId, studentId,
        fixedNow.plusSeconds(3600), fixedNow.plusSeconds(7200),
        SessionMode.ONLINE, "https://meet.google.com/abc", null, null,
        tutorId, studentId, fixedNow.minusSeconds(3600)
    );

    TutoringSession proposedSession = TutoringSession.createProposed(
        UUID.randomUUID(), classId, tutorId, studentId,
        fixedNow.plusSeconds(86400), fixedNow.plusSeconds(90000),
        SessionMode.ONLINE, "https://meet.google.com/def", null, null,
        tutorId, fixedNow.plusSeconds(80000), fixedNow.minusSeconds(1000)
    );

    when(sessionRepository.findByClassId(classId, fixedNow, null))
        .thenReturn(List.of(scheduledSession, proposedSession));

    TutoringClassEndedEvent event = new TutoringClassEndedEvent(
        classId, studentId, tutorId, endedBy, "Học sinh chuyển trường", fixedNow
    );

    listener.onClassEnded(event);

    assertEquals(SessionStatus.CANCELLED, scheduledSession.getStatus());
    assertEquals(endedBy, scheduledSession.getCancelledBy());
    assertEquals("Học sinh chuyển trường", scheduledSession.getCancelComment());
    assertEquals(CancelReason.OTHER, scheduledSession.getCancelReason());
    assertFalse(scheduledSession.isLateCancel());

    assertEquals(SessionStatus.CANCELLED, proposedSession.getStatus());
    assertEquals(endedBy, proposedSession.getCancelledBy());
    assertEquals("Học sinh chuyển trường", proposedSession.getCancelComment());

    verify(sessionRepository, times(2)).save(any(TutoringSession.class));
    verify(rescheduleClassScheduleService).cancelActiveProposalForClass(classId, "Học sinh chuyển trường", fixedNow);
  }
}

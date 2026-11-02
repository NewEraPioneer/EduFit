package vn.edufit.scheduling.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import vn.edufit.scheduling.api.event.SessionOutcomeRecordedEvent;
import vn.edufit.scheduling.application.port.ProgressSyncPort;
import vn.edufit.scheduling.application.service.RecordSessionOutcomeService;
import vn.edufit.scheduling.application.service.SchedulingSecurityResolver;
import vn.edufit.scheduling.application.service.SimpleCurrentUser;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Unit Test cho {@link RecordSessionOutcomeService} (UC4.6).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Service: Ghi nhận kết quả buổi học (RecordSessionOutcomeService)")
class RecordSessionOutcomeServiceTest {

  @Mock
  private TutoringSessionDomainRepository sessionRepository;

  @Mock
  private SessionHistoryDomainRepository historyRepository;

  @Mock
  private SchedulingSecurityResolver schedulingSecurityResolver;

  @Mock
  private ProgressSyncPort progressSyncPort;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private Clock fixedClock;
  private RecordSessionOutcomeService service;

  private final UUID sessionId = UUID.randomUUID();
  private final UUID classId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID studentUserId = UUID.randomUUID();
  private final Instant fixedNow = Instant.parse("2026-10-25T12:00:00Z");

  private CurrentUser tutorUser;
  private CurrentUser studentUser;
  private TutoringSession scheduledSession;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    service = new RecordSessionOutcomeService(
        sessionRepository,
        historyRepository,
        schedulingSecurityResolver,
        progressSyncPort,
        eventPublisher,
        fixedClock
    );

    tutorUser = SimpleCurrentUser.of(tutorUserId, "TUTOR");
    studentUser = SimpleCurrentUser.of(studentUserId, "STUDENT");

    // Session took place from 10:00 to 11:30 (before fixedNow 12:00)
    Instant startAt = fixedNow.minus(2, ChronoUnit.HOURS);
    Instant endAt = fixedNow.minus(30, ChronoUnit.MINUTES);
    scheduledSession = new TutoringSession(
        sessionId, classId, tutorId, studentId,
        startAt, endAt, SessionMode.ONLINE, null, null, null,
        SessionStatus.SCHEDULED, studentUserId, tutorUserId, fixedNow.minus(1, ChronoUnit.DAYS),
        null, startAt, null, null, null, null, false, null, null, 1, fixedNow.minus(2, ChronoUnit.DAYS)
    );
  }

  @Test
  @DisplayName("Gia sư ghi nhận kết quả COMPLETED kèm biên bản học tập thành công")
  void shouldRecordCompletedOutcomeSuccessfully() {
    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(scheduledSession));
    when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    TutoringSession result = service.recordOutcome(
        sessionId,
        tutorUser,
        SessionStatus.COMPLETED,
        "Học sinh nắm vững kiến thức phương trình lượng giác"
    );

    assertNotNull(result);
    assertEquals(SessionStatus.COMPLETED, result.getStatus());
    assertEquals(tutorUserId, result.getOutcomeRecordedBy());

    verify(schedulingSecurityResolver).requireTutorAccess(tutorUser, tutorId);
    verify(sessionRepository).save(scheduledSession);
    verify(progressSyncPort).recordSessionNote(
        eq(sessionId),
        eq(classId),
        eq("Học sinh nắm vững kiến thức phương trình lượng giác"),
        eq(fixedNow)
    );
    verify(historyRepository).save(any());
    verify(eventPublisher).publishEvent(any(SessionOutcomeRecordedEvent.class));
  }

  @Test
  @DisplayName("Gia sư ghi nhận kết quả ABSENT thành công")
  void shouldRecordAbsentOutcomeSuccessfully() {
    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(scheduledSession));
    when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    TutoringSession result = service.recordOutcome(
        sessionId,
        tutorUser,
        SessionStatus.ABSENT,
        null
    );

    assertNotNull(result);
    assertEquals(SessionStatus.ABSENT, result.getStatus());
    assertEquals(tutorUserId, result.getOutcomeRecordedBy());

    verify(schedulingSecurityResolver).requireTutorAccess(tutorUser, tutorId);
    verify(progressSyncPort, never()).recordSessionNote(any(), any(), any(), any());
  }

  @Test
  @DisplayName("Ném InvalidOperationException khi cố ghi nhận kết quả trước khi buổi học bắt đầu")
  void shouldThrowWhenRecordingBeforeSessionStarts() {
    // Session is in the future
    Instant futureStart = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant futureEnd = futureStart.plus(90, ChronoUnit.MINUTES);
    TutoringSession futureSession = new TutoringSession(
        sessionId, classId, tutorId, studentId,
        futureStart, futureEnd, SessionMode.ONLINE, null, null, null,
        SessionStatus.SCHEDULED, studentUserId, tutorUserId, fixedNow.minus(1, ChronoUnit.DAYS),
        null, futureStart, null, null, null, null, false, null, null, 1, fixedNow.minus(2, ChronoUnit.DAYS)
    );

    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(futureSession));

    assertThrows(InvalidOperationException.class, () ->
        service.recordOutcome(sessionId, tutorUser, SessionStatus.COMPLETED, "Ghi sớm")
    );

    verify(sessionRepository, never()).save(any());
  }

  @Test
  @DisplayName("Ném ForbiddenOperationException nếu không phải Gia sư của buổi học")
  void shouldThrowForbiddenWhenNonTutorRecordsOutcome() {
    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(scheduledSession));
    doThrow(new ForbiddenOperationException("Chỉ Gia sư của buổi học mới có quyền ghi nhận kết quả"))
        .when(schedulingSecurityResolver).requireTutorAccess(studentUser, tutorId);

    assertThrows(ForbiddenOperationException.class, () ->
        service.recordOutcome(sessionId, studentUser, SessionStatus.COMPLETED, "Học sinh cố ghi")
    );

    verify(sessionRepository, never()).save(any());
  }
}

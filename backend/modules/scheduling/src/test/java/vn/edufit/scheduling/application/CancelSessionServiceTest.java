package vn.edufit.scheduling.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import vn.edufit.scheduling.api.event.SessionCancelledEvent;
import vn.edufit.scheduling.application.service.CancelSessionService;
import vn.edufit.scheduling.application.service.SchedulingSecurityResolver;
import vn.edufit.scheduling.application.service.SimpleCurrentUser;
import vn.edufit.scheduling.domain.model.CancelReason;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;

/**
 * Unit Test cho {@link CancelSessionService} (UC4.5).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Service: Hủy buổi học đã lên lịch (CancelSessionService)")
class CancelSessionServiceTest {

  @Mock
  private TutoringSessionDomainRepository sessionRepository;

  @Mock
  private SessionHistoryDomainRepository historyRepository;

  @Mock
  private SchedulingSecurityResolver schedulingSecurityResolver;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private Clock fixedClock;
  private CancelSessionService cancelSessionService;

  private final UUID sessionId = UUID.randomUUID();
  private final UUID classId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID studentUserId = UUID.randomUUID();
  private final UUID parentUserId = UUID.randomUUID();
  private final Instant fixedNow = Instant.parse("2026-10-25T08:00:00Z");

  private CurrentUser studentUser;
  private CurrentUser tutorUser;
  private CurrentUser parentUser;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    cancelSessionService = new CancelSessionService(
        sessionRepository,
        historyRepository,
        schedulingSecurityResolver,
        eventPublisher,
        fixedClock
    );

    studentUser = SimpleCurrentUser.of(studentUserId, "STUDENT");
    tutorUser = SimpleCurrentUser.of(tutorUserId, "TUTOR");
    parentUser = SimpleCurrentUser.of(parentUserId, "PARENT");
  }

  @Test
  @DisplayName("Hủy bình thường trước giờ học > 12h bởi Học sinh -> isLateCancel = false (BR-46)")
  void shouldCancelNormallyWhenMoreThan12Hours() {
    Instant startAt = fixedNow.plus(24, ChronoUnit.HOURS); // Trước 24h
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);

    TutoringSession scheduledSession = new TutoringSession(
        sessionId, classId, tutorId, studentId,
        startAt, endAt, SessionMode.ONLINE, null, null, null,
        SessionStatus.SCHEDULED, studentUserId, tutorUserId, fixedNow.minus(1, ChronoUnit.DAYS),
        null, startAt, null, null, null, null, false, null, null, 1, fixedNow.minus(2, ChronoUnit.DAYS)
    );

    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(scheduledSession));
    when(sessionRepository.save(any(TutoringSession.class))).thenAnswer(inv -> inv.getArgument(0));

    TutoringSession result = cancelSessionService.cancelSession(
        sessionId,
        studentUser,
        CancelReason.PERSONAL_REASON,
        "Em bị ốm xin nghỉ trước 1 ngày"
    );

    assertNotNull(result);
    assertEquals(SessionStatus.CANCELLED, result.getStatus());
    assertFalse(result.isLateCancel(), "Hủy trước 24h không bị coi là trễ");
    assertEquals(CancelReason.PERSONAL_REASON, result.getCancelReason());
    assertEquals(studentUserId, result.getCancelledBy());

    verify(schedulingSecurityResolver).requireCancelAccess(studentUser, scheduledSession);
    verify(sessionRepository).save(any());
    verify(historyRepository).save(any());
    verify(eventPublisher).publishEvent(any(SessionCancelledEvent.class));
  }

  @Test
  @DisplayName("Hủy trễ sát giờ học < 12h bởi Gia sư -> isLateCancel = true (BR-46)")
  void shouldMarkLateCancelWhenLessThan12Hours() {
    Instant startAt = fixedNow.plus(4, ChronoUnit.HOURS); // Chỉ trước 4h (< 12h)
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);

    TutoringSession scheduledSession = new TutoringSession(
        sessionId, classId, tutorId, studentId,
        startAt, endAt, SessionMode.ONLINE, null, null, null,
        SessionStatus.SCHEDULED, studentUserId, tutorUserId, fixedNow.minus(1, ChronoUnit.DAYS),
        null, startAt, null, null, null, null, false, null, null, 1, fixedNow.minus(2, ChronoUnit.DAYS)
    );

    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(scheduledSession));
    when(sessionRepository.save(any(TutoringSession.class))).thenAnswer(inv -> inv.getArgument(0));

    TutoringSession result = cancelSessionService.cancelSession(
        sessionId,
        tutorUser,
        CancelReason.PERSONAL_REASON,
        "Thầy có việc gấp gia đình"
    );

    assertNotNull(result);
    assertEquals(SessionStatus.CANCELLED, result.getStatus());
    assertTrue(result.isLateCancel(), "Hủy trước 4h bị đánh dấu isLateCancel = true");
    assertEquals(tutorUserId, result.getCancelledBy());

    verify(schedulingSecurityResolver).requireCancelAccess(tutorUser, scheduledSession);
    verify(sessionRepository).save(any());
    verify(eventPublisher).publishEvent(any(SessionCancelledEvent.class));
  }

  @Test
  @DisplayName("Hủy thành công bởi Phụ huynh có liên kết với học sinh")
  void shouldCancelSuccessfullyByParent() {
    Instant startAt = fixedNow.plus(24, ChronoUnit.HOURS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);

    TutoringSession scheduledSession = new TutoringSession(
        sessionId, classId, tutorId, studentId,
        startAt, endAt, SessionMode.ONLINE, null, null, null,
        SessionStatus.SCHEDULED, studentUserId, tutorUserId, fixedNow.minus(1, ChronoUnit.DAYS),
        null, startAt, null, null, null, null, false, null, null, 1, fixedNow.minus(2, ChronoUnit.DAYS)
    );

    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(scheduledSession));
    when(sessionRepository.save(any(TutoringSession.class))).thenAnswer(inv -> inv.getArgument(0));

    TutoringSession result = cancelSessionService.cancelSession(
        sessionId,
        parentUser,
        CancelReason.PERSONAL_REASON,
        "Phụ huynh xin nghỉ cho cháu"
    );

    assertNotNull(result);
    assertEquals(SessionStatus.CANCELLED, result.getStatus());
    assertEquals(parentUserId, result.getCancelledBy());
    verify(schedulingSecurityResolver).requireCancelAccess(parentUser, scheduledSession);
  }

  @Test
  @DisplayName("Ném ForbiddenOperationException nếu người ngoài không có quyền cố tình hủy buổi học")
  void shouldThrowForbiddenWhenCancelledByStranger() {
    CurrentUser stranger = SimpleCurrentUser.of(UUID.randomUUID(), "STUDENT");
    Instant startAt = fixedNow.plus(24, ChronoUnit.HOURS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);

    TutoringSession scheduledSession = new TutoringSession(
        sessionId, classId, tutorId, studentId,
        startAt, endAt, SessionMode.ONLINE, null, null, null,
        SessionStatus.SCHEDULED, studentUserId, tutorUserId, fixedNow.minus(1, ChronoUnit.DAYS),
        null, startAt, null, null, null, null, false, null, null, 1, fixedNow.minus(2, ChronoUnit.DAYS)
    );

    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(scheduledSession));
    doThrow(new ForbiddenOperationException("Bạn không có quyền hủy buổi học này"))
        .when(schedulingSecurityResolver).requireCancelAccess(stranger, scheduledSession);

    assertThrows(ForbiddenOperationException.class, () ->
        cancelSessionService.cancelSession(
            sessionId,
            stranger,
            CancelReason.OTHER,
            "Tôi muốn hủy"
        )
    );
  }
}

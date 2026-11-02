package vn.edufit.scheduling.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import vn.edufit.scheduling.api.event.SessionProposedEvent;
import vn.edufit.scheduling.application.port.ConnectionValidationPort;
import vn.edufit.scheduling.application.service.ProposeSessionService;
import vn.edufit.scheduling.application.service.SchedulingSecurityResolver;
import vn.edufit.scheduling.application.service.SimpleCurrentUser;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Unit Test cho {@link ProposeSessionService} (UC4.1).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Service: Đề xuất buổi học mới (ProposeSessionService)")
class ProposeSessionServiceTest {

  @Mock
  private TutoringSessionDomainRepository sessionRepository;

  @Mock
  private SessionHistoryDomainRepository historyRepository;

  @Mock
  private ConnectionValidationPort connectionValidationPort;

  @Mock
  private SchedulingSecurityResolver schedulingSecurityResolver;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private Clock fixedClock;
  private ProposeSessionService proposeSessionService;

  private final UUID classId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID parentUserId = UUID.randomUUID();
  private final Instant fixedNow = Instant.parse("2026-10-25T08:00:00Z");

  private CurrentUser tutorUser;
  private CurrentUser parentUser;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    proposeSessionService = new ProposeSessionService(
        sessionRepository,
        historyRepository,
        connectionValidationPort,
        schedulingSecurityResolver,
        eventPublisher,
        fixedClock
    );

    tutorUser = SimpleCurrentUser.of(tutorUserId, "TUTOR");
    parentUser = SimpleCurrentUser.of(parentUserId, "PARENT");
  }

  @Test
  @DisplayName("Đề xuất buổi học thành công khi Gia sư đề xuất hợp lệ và không bị trùng lịch")
  void shouldProposeSessionSuccessfullyByTutor() {
    Instant startAt = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);

    when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);
    when(sessionRepository.findTutorBusySlots(tutorId, startAt, endAt)).thenReturn(Collections.emptyList());
    when(sessionRepository.findCalendarSessions(studentId, startAt, endAt)).thenReturn(Collections.emptyList());
    when(sessionRepository.save(any(TutoringSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

    TutoringSession session = proposeSessionService.proposeSession(
        classId,
        tutorId,
        studentId,
        startAt,
        endAt,
        SessionMode.ONLINE,
        "https://meet.google.com/xyz",
        "Buổi ôn tập toán số 1",
        "Chào em, thầy hẹn lịch ôn tập nhé",
        tutorUser
    );

    assertNotNull(session);
    assertEquals(SessionStatus.PROPOSED, session.getStatus());
    assertEquals(tutorUserId, session.getProposedBy());

    verify(schedulingSecurityResolver).requireProposeAccess(tutorUser, tutorId, studentId);
    verify(sessionRepository).save(any(TutoringSession.class));
    verify(historyRepository).save(any());
    verify(eventPublisher).publishEvent(any(SessionProposedEvent.class));
  }

  @Test
  @DisplayName("Đề xuất buổi học thành công khi Phụ huynh đại diện học sinh đề xuất")
  void shouldProposeSessionSuccessfullyByParent() {
    Instant startAt = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);

    when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);
    when(sessionRepository.findTutorBusySlots(tutorId, startAt, endAt)).thenReturn(Collections.emptyList());
    when(sessionRepository.findCalendarSessions(studentId, startAt, endAt)).thenReturn(Collections.emptyList());
    when(sessionRepository.save(any(TutoringSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

    TutoringSession session = proposeSessionService.proposeSession(
        classId,
        tutorId,
        studentId,
        startAt,
        endAt,
        SessionMode.ONLINE,
        "https://meet.google.com/xyz",
        "Buổi ôn tập toán số 1",
        "Chào thầy, phụ huynh đăng ký lịch học cho cháu",
        parentUser
    );

    assertNotNull(session);
    assertEquals(SessionStatus.PROPOSED, session.getStatus());
    assertEquals(parentUserId, session.getProposedBy());

    verify(schedulingSecurityResolver).requireProposeAccess(parentUser, tutorId, studentId);
  }

  @Test
  @DisplayName("Ném ForbiddenOperationException nếu người đề xuất không có quyền truy cập lớp học")
  void shouldThrowForbiddenWhenProposedByStranger() {
    CurrentUser stranger = SimpleCurrentUser.of(UUID.randomUUID(), "STUDENT");
    Instant startAt = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant endAt = startAt.plus(60, ChronoUnit.MINUTES);

    doThrow(new ForbiddenOperationException("Chỉ Gia sư hoặc Học sinh trong lớp học mới có quyền đề xuất lịch học"))
        .when(schedulingSecurityResolver).requireProposeAccess(stranger, tutorId, studentId);

    assertThrows(ForbiddenOperationException.class, () ->
        proposeSessionService.proposeSession(
            classId,
            tutorId,
            studentId,
            startAt,
            endAt,
            SessionMode.ONLINE,
            null,
            null,
            null,
            stranger
        )
    );

    verify(sessionRepository, never()).save(any());
  }

  @Test
  @DisplayName("Ném InvalidOperationException nếu kết nối lớp học chưa kích hoạt")
  void shouldThrowInvalidOperationWhenConnectionInactive() {
    Instant startAt = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant endAt = startAt.plus(60, ChronoUnit.MINUTES);

    when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(false);

    assertThrows(InvalidOperationException.class, () ->
        proposeSessionService.proposeSession(
            classId,
            tutorId,
            studentId,
            startAt,
            endAt,
            SessionMode.ONLINE,
            null,
            null,
            null,
            tutorUser
        )
    );

    verify(sessionRepository, never()).save(any());
  }

  @Test
  @DisplayName("Ném ResourceConflictException nếu Gia sư đã có lịch dạy bị trùng (BR-42)")
  void shouldThrowConflictWhenTutorHasOverlappingSession() {
    Instant startAt = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant endAt = startAt.plus(60, ChronoUnit.MINUTES);

    TutoringSession existingSession = TutoringSession.createProposed(
        UUID.randomUUID(),
        classId,
        tutorId,
        studentId,
        startAt.minus(15, ChronoUnit.MINUTES),
        endAt.plus(15, ChronoUnit.MINUTES),
        SessionMode.ONLINE,
        null,
        null,
        null,
        tutorUserId,
        startAt,
        fixedNow
    );

    when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);
    when(sessionRepository.findTutorBusySlots(tutorId, startAt, endAt)).thenReturn(List.of(existingSession));

    ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
        proposeSessionService.proposeSession(
            classId,
            tutorId,
            studentId,
            startAt,
            endAt,
            SessionMode.ONLINE,
            null,
            null,
            null,
            tutorUser
        )
    );

    assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
    verify(sessionRepository, never()).save(any());
  }

  @Test
  @DisplayName("Bẫy lỗi DataIntegrityViolationException từ DB GiST constraint và ném SCHEDULE_OVERLAP (Tầng 2 Defense)")
  void shouldCatchDataIntegrityViolationAndThrowScheduleOverlap() {
    Instant startAt = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant endAt = startAt.plus(60, ChronoUnit.MINUTES);

    when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);
    when(sessionRepository.findTutorBusySlots(tutorId, startAt, endAt)).thenReturn(Collections.emptyList());
    when(sessionRepository.findCalendarSessions(studentId, startAt, endAt)).thenReturn(Collections.emptyList());
    when(sessionRepository.save(any(TutoringSession.class)))
        .thenThrow(new DataIntegrityViolationException("ex_tutoring_session_no_tutor_overlap"));

    ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
        proposeSessionService.proposeSession(
            classId,
            tutorId,
            studentId,
            startAt,
            endAt,
            SessionMode.ONLINE,
            null,
            null,
            null,
            tutorUser
        )
    );

    assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
  }

  @Test
  @DisplayName("Tự động phân giải tutorId và studentId từ classId khi đề xuất buổi lẻ")
  void shouldAutoResolveTutorAndStudentIdFromActiveClass() {
    Instant startAt = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);

    UUID resolvedTutorId = UUID.randomUUID();
    UUID resolvedStudentId = UUID.randomUUID();
    var activeClassInfo = new ConnectionValidationPort.ActiveClassInfo(classId, resolvedStudentId, resolvedTutorId, null);

    when(connectionValidationPort.findActiveClassById(classId)).thenReturn(java.util.Optional.of(activeClassInfo));
    when(connectionValidationPort.isConnectionActive(classId, resolvedTutorId, resolvedStudentId)).thenReturn(true);
    when(sessionRepository.findTutorBusySlots(resolvedTutorId, startAt, endAt)).thenReturn(Collections.emptyList());
    when(sessionRepository.findCalendarSessions(resolvedStudentId, startAt, endAt)).thenReturn(Collections.emptyList());
    when(sessionRepository.save(any(TutoringSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // Client truyền tutorId và studentId là null hoặc ID khác
    TutoringSession session = proposeSessionService.proposeSession(
        classId,
        null,
        null,
        startAt,
        endAt,
        SessionMode.ONLINE,
        "https://meet.google.com/xyz",
        "Buổi học giải tích",
        "Thầy hẹn lịch nhé",
        tutorUser
    );

    assertNotNull(session);
    assertEquals(resolvedTutorId, session.getTutorId());
    assertEquals(resolvedStudentId, session.getStudentId());
    verify(schedulingSecurityResolver).requireProposeAccess(tutorUser, resolvedTutorId, resolvedStudentId);
    verify(sessionRepository).save(any(TutoringSession.class));
  }

  @Test
  @DisplayName("Ném InvalidOperationException khi classId là null")
  void shouldThrowWhenClassIdIsNull() {
    Instant startAt = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);

    assertThrows(InvalidOperationException.class, () ->
        proposeSessionService.proposeSession(
            null,
            tutorId,
            studentId,
            startAt,
            endAt,
            SessionMode.ONLINE,
            null,
            null,
            null,
            tutorUser
        )
    );
  }
}

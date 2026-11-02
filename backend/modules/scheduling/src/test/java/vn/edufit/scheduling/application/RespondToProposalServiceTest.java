package vn.edufit.scheduling.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import vn.edufit.scheduling.api.event.SessionScheduledEvent;
import vn.edufit.scheduling.application.service.RespondToProposalService;
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
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Unit Test cho {@link RespondToProposalService} (UC4.3).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Service: Phản hồi đề xuất buổi học (RespondToProposalService)")
class RespondToProposalServiceTest {

  @Mock
  private TutoringSessionDomainRepository sessionRepository;

  @Mock
  private SessionHistoryDomainRepository historyRepository;

  @Mock
  private SchedulingSecurityResolver schedulingSecurityResolver;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private Clock fixedClock;
  private RespondToProposalService respondToProposalService;

  private final UUID sessionId = UUID.randomUUID();
  private final UUID classId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID studentUserId = UUID.randomUUID();
  private final UUID parentUserId = UUID.randomUUID();
  private final Instant fixedNow = Instant.parse("2026-10-25T08:00:00Z");

  private CurrentUser tutorUser;
  private CurrentUser parentUser;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    respondToProposalService = new RespondToProposalService(
        sessionRepository,
        historyRepository,
        schedulingSecurityResolver,
        eventPublisher,
        fixedClock
    );

    tutorUser = SimpleCurrentUser.of(tutorUserId, "TUTOR");
    parentUser = SimpleCurrentUser.of(parentUserId, "PARENT");
  }

  @Test
  @DisplayName("Chấp nhận đề xuất thành công (ACCEPT) bởi Gia sư -> Chuyển sang SCHEDULED và phát sự kiện")
  void shouldAcceptProposalSuccessfullyByTutor() {
    Instant startAt = fixedNow.plus(2, ChronoUnit.DAYS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);
    Instant expiresAt = startAt.minus(2, ChronoUnit.HOURS);

    TutoringSession proposedSession = TutoringSession.createProposed(
        sessionId,
        classId,
        tutorId,
        studentId,
        startAt,
        endAt,
        SessionMode.ONLINE,
        null,
        null,
        null,
        studentUserId, // Học sinh đề xuất
        expiresAt,
        fixedNow.minus(1, ChronoUnit.HOURS)
    );

    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(proposedSession));
    when(sessionRepository.findTutorBusySlots(tutorId, startAt, endAt)).thenReturn(Collections.emptyList());
    when(sessionRepository.save(any(TutoringSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // Gia sư chấp thuận
    TutoringSession scheduledSession = respondToProposalService.respondToProposal(
        sessionId,
        tutorUser,
        true,
        null
    );

    assertNotNull(scheduledSession);
    assertEquals(SessionStatus.SCHEDULED, scheduledSession.getStatus());
    assertEquals(tutorUserId, scheduledSession.getRespondedBy());

    verify(schedulingSecurityResolver).requireCounterpartyResponseAccess(tutorUser, proposedSession);
    verify(sessionRepository).save(any(TutoringSession.class));
    verify(historyRepository).save(any());
    verify(eventPublisher).publishEvent(any(SessionScheduledEvent.class));
  }

  @Test
  @DisplayName("Chấp nhận đề xuất thành công (ACCEPT) bởi Phụ huynh khi Gia sư đề xuất")
  void shouldAcceptProposalSuccessfullyByParent() {
    Instant startAt = fixedNow.plus(2, ChronoUnit.DAYS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);
    Instant expiresAt = startAt.minus(2, ChronoUnit.HOURS);

    TutoringSession proposedSession = TutoringSession.createProposed(
        sessionId,
        classId,
        tutorId,
        studentId,
        startAt,
        endAt,
        SessionMode.ONLINE,
        null,
        null,
        null,
        tutorUserId, // Gia sư đề xuất
        expiresAt,
        fixedNow.minus(1, ChronoUnit.HOURS)
    );

    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(proposedSession));
    when(sessionRepository.findTutorBusySlots(tutorId, startAt, endAt)).thenReturn(Collections.emptyList());
    when(sessionRepository.save(any(TutoringSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // Phụ huynh chấp thuận
    TutoringSession scheduledSession = respondToProposalService.respondToProposal(
        sessionId,
        parentUser,
        true,
        null
    );

    assertNotNull(scheduledSession);
    assertEquals(SessionStatus.SCHEDULED, scheduledSession.getStatus());
    assertEquals(parentUserId, scheduledSession.getRespondedBy());

    verify(schedulingSecurityResolver).requireCounterpartyResponseAccess(parentUser, proposedSession);
  }

  @Test
  @DisplayName("Từ chối đề xuất thành công (REJECT) -> Chuyển sang REJECTED và lưu lý do")
  void shouldRejectProposalSuccessfully() {
    Instant startAt = fixedNow.plus(2, ChronoUnit.DAYS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);
    Instant expiresAt = startAt.minus(2, ChronoUnit.HOURS);

    TutoringSession proposedSession = TutoringSession.createProposed(
        sessionId,
        classId,
        tutorId,
        studentId,
        startAt,
        endAt,
        SessionMode.ONLINE,
        null,
        null,
        null,
        studentUserId,
        expiresAt,
        fixedNow.minus(1, ChronoUnit.HOURS)
    );

    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(proposedSession));
    when(sessionRepository.save(any(TutoringSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

    TutoringSession rejectedSession = respondToProposalService.respondToProposal(
        sessionId,
        tutorUser,
        false,
        "Thầy bận đột xuất vào giờ này"
    );

    assertEquals(SessionStatus.REJECTED, rejectedSession.getStatus());
    assertEquals("Thầy bận đột xuất vào giờ này", rejectedSession.getResponseReason());
    assertEquals(tutorUserId, rejectedSession.getRespondedBy());
    verify(sessionRepository).save(any(TutoringSession.class));
  }

  @Test
  @DisplayName("Ném ForbiddenOperationException nếu không đủ thẩm quyền phản hồi đề xuất")
  void shouldThrowForbiddenWhenProposerTriesToRespond() {
    Instant startAt = fixedNow.plus(2, ChronoUnit.DAYS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);
    Instant expiresAt = startAt.minus(2, ChronoUnit.HOURS);

    TutoringSession proposedSession = TutoringSession.createProposed(
        sessionId,
        classId,
        tutorId,
        studentId,
        startAt,
        endAt,
        SessionMode.ONLINE,
        null,
        null,
        null,
        studentUserId,
        expiresAt,
        fixedNow.minus(1, ChronoUnit.HOURS)
    );

    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(proposedSession));
    doThrow(new ForbiddenOperationException("Bạn không thể tự phản hồi đề xuất do chính mình tạo ra"))
        .when(schedulingSecurityResolver).requireCounterpartyResponseAccess(any(), eq(proposedSession));

    assertThrows(ForbiddenOperationException.class, () ->
        respondToProposalService.respondToProposal(
            sessionId,
            SimpleCurrentUser.of(studentUserId, "STUDENT"),
            true,
            null
        )
    );
  }

  @Test
  @DisplayName("Bẫy lỗi GiST constraint khi accept và ném SCHEDULE_OVERLAP (Tầng 2)")
  void shouldCatchExclusionConstraintViolationOnAccept() {
    Instant startAt = fixedNow.plus(2, ChronoUnit.DAYS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);
    Instant expiresAt = startAt.minus(2, ChronoUnit.HOURS);

    TutoringSession proposedSession = TutoringSession.createProposed(
        sessionId,
        classId,
        tutorId,
        studentId,
        startAt,
        endAt,
        SessionMode.ONLINE,
        null,
        null,
        null,
        studentUserId,
        expiresAt,
        fixedNow.minus(1, ChronoUnit.HOURS)
    );

    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(proposedSession));
    when(sessionRepository.findTutorBusySlots(tutorId, startAt, endAt)).thenReturn(Collections.emptyList());
    when(sessionRepository.save(any(TutoringSession.class)))
        .thenThrow(new DataIntegrityViolationException("ex_tutoring_session_no_tutor_overlap"));

    ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
        respondToProposalService.respondToProposal(
            sessionId,
            tutorUser,
            true,
            null
        )
    );

    assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
  }
}

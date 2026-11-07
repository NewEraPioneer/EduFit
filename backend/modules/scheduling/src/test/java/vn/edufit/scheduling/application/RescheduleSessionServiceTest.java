package vn.edufit.scheduling.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import vn.edufit.scheduling.api.event.SessionRescheduleProposedEvent;
import vn.edufit.scheduling.api.event.SessionRescheduleRejectedEvent;
import vn.edufit.scheduling.api.event.SessionRescheduledEvent;
import vn.edufit.scheduling.application.service.RescheduleSessionService;
import vn.edufit.scheduling.application.service.SchedulingSecurityResolver;
import vn.edufit.scheduling.application.service.SimpleCurrentUser;
import vn.edufit.scheduling.domain.model.ProposalStatus;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionRescheduleProposal;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.domain.repository.SessionRescheduleProposalDomainRepository;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Unit Test cho {@link RescheduleSessionService} (UC4.4).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Service: Đổi lịch buổi học (RescheduleSessionService)")
class RescheduleSessionServiceTest {

  @Mock
  private TutoringSessionDomainRepository sessionRepository;

  @Mock
  private SessionRescheduleProposalDomainRepository proposalRepository;

  @Mock
  private SessionHistoryDomainRepository historyRepository;

  @Mock
  private SchedulingSecurityResolver schedulingSecurityResolver;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private Clock fixedClock;
  private RescheduleSessionService service;

  private final UUID sessionId = UUID.randomUUID();
  private final UUID classId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID studentUserId = UUID.randomUUID();
  private final UUID parentUserId = UUID.randomUUID();
  private final UUID proposalId = UUID.randomUUID();
  private final Instant fixedNow = Instant.parse("2026-10-25T08:00:00Z");

  private CurrentUser tutorUser;
  private CurrentUser studentUser;
  private CurrentUser parentUser;
  private TutoringSession scheduledSession;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    service = new RescheduleSessionService(
        sessionRepository,
        proposalRepository,
        historyRepository,
        schedulingSecurityResolver,
        eventPublisher,
        fixedClock
    );

    tutorUser = SimpleCurrentUser.of(tutorUserId, "TUTOR");
    studentUser = SimpleCurrentUser.of(studentUserId, "STUDENT");
    parentUser = SimpleCurrentUser.of(parentUserId, "PARENT");

    Instant startAt = fixedNow.plus(2, ChronoUnit.DAYS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);
    scheduledSession = new TutoringSession(
        sessionId, classId, tutorId, studentId,
        startAt, endAt, SessionMode.ONLINE, null, null, null,
        SessionStatus.SCHEDULED, studentUserId, tutorUserId, fixedNow.minus(1, ChronoUnit.DAYS),
        null, startAt, null, null, null, null, false, null, null, 1, fixedNow.minus(2, ChronoUnit.DAYS)
    );
  }

  @Test
  @DisplayName("Gia sư tạo đề xuất đổi lịch thành công")
  void shouldProposeRescheduleSuccessfullyByTutor() {
    Instant newStartAt = fixedNow.plus(4, ChronoUnit.DAYS);
    Instant newEndAt = newStartAt.plus(90, ChronoUnit.MINUTES);

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(scheduledSession));
    when(proposalRepository.findPendingBySessionId(sessionId)).thenReturn(Optional.empty());
    when(sessionRepository.findTutorBusySlots(tutorId, newStartAt, newEndAt)).thenReturn(Collections.emptyList());
    when(proposalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    SessionRescheduleProposal proposal = service.proposeReschedule(
        sessionId,
        tutorUser,
        newStartAt,
        newEndAt,
        "Thầy xin dời lịch sang thứ 6"
    );

    assertNotNull(proposal);
    assertEquals(ProposalStatus.PENDING, proposal.getStatus());
    assertEquals(tutorUserId, proposal.getProposedBy());

    verify(schedulingSecurityResolver).requireProposeAccess(tutorUser, tutorId, studentId);
    verify(proposalRepository).save(any());
    verify(historyRepository).save(any());
    verify(eventPublisher).publishEvent(any(SessionRescheduleProposedEvent.class));
  }

  @Test
  @DisplayName("Phụ huynh tạo đề xuất đổi lịch thành công cho học sinh")
  void shouldProposeRescheduleSuccessfullyByParent() {
    Instant newStartAt = fixedNow.plus(4, ChronoUnit.DAYS);
    Instant newEndAt = newStartAt.plus(90, ChronoUnit.MINUTES);

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(scheduledSession));
    when(proposalRepository.findPendingBySessionId(sessionId)).thenReturn(Optional.empty());
    when(sessionRepository.findTutorBusySlots(tutorId, newStartAt, newEndAt)).thenReturn(Collections.emptyList());
    when(proposalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    SessionRescheduleProposal proposal = service.proposeReschedule(
        sessionId,
        parentUser,
        newStartAt,
        newEndAt,
        "Gia đình xin đổi sang chiều thứ 6"
    );

    assertNotNull(proposal);
    assertEquals(ProposalStatus.PENDING, proposal.getStatus());
    assertEquals(parentUserId, proposal.getProposedBy());

    verify(schedulingSecurityResolver).requireProposeAccess(parentUser, tutorId, studentId);
  }

  @Test
  @DisplayName("Ném InvalidOperationException khi buổi học đã có 1 đề xuất PENDING (BR-44)")
  void shouldThrowWhenPendingProposalAlreadyExists() {
    Instant newStartAt = fixedNow.plus(4, ChronoUnit.DAYS);
    Instant newEndAt = newStartAt.plus(90, ChronoUnit.MINUTES);

    SessionRescheduleProposal existingPending = SessionRescheduleProposal.create(
        UUID.randomUUID(), sessionId, studentUserId, newStartAt, newEndAt, "Trùng", newStartAt, fixedNow
    );

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(scheduledSession));
    when(proposalRepository.findPendingBySessionId(sessionId)).thenReturn(Optional.of(existingPending));

    assertThrows(InvalidOperationException.class, () ->
        service.proposeReschedule(sessionId, tutorUser, newStartAt, newEndAt, "Thử tạo đề xuất thứ 2")
    );
  }

  @Test
  @DisplayName("Chấp nhận đề xuất đổi lịch thành công (ACCEPT) -> Cập nhật lịch học mới")
  void shouldAcceptRescheduleSuccessfully() {
    Instant newStartAt = fixedNow.plus(4, ChronoUnit.DAYS);
    Instant newEndAt = newStartAt.plus(90, ChronoUnit.MINUTES);

    SessionRescheduleProposal proposal = SessionRescheduleProposal.create(
        proposalId, sessionId, tutorUserId, newStartAt, newEndAt, "Dời giờ", newStartAt, fixedNow
    );

    when(proposalRepository.findById(proposalId)).thenReturn(Optional.of(proposal));
    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(scheduledSession));
    when(sessionRepository.findTutorBusySlots(tutorId, newStartAt, newEndAt)).thenReturn(Collections.emptyList());
    when(proposalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // Học sinh chấp thuận đề xuất đổi lịch của Gia sư
    SessionRescheduleProposal result = service.respondToReschedule(proposalId, studentUser, true, null);

    assertEquals(ProposalStatus.ACCEPTED, result.getStatus());
    assertEquals(studentUserId, result.getRespondedBy());
    assertEquals(newStartAt, scheduledSession.getStartAt());
    assertEquals(newEndAt, scheduledSession.getEndAt());

    verify(schedulingSecurityResolver).requireCounterpartyResponseAccess(studentUser, scheduledSession, tutorUserId);
    verify(sessionRepository).save(scheduledSession);
    verify(eventPublisher).publishEvent(any(SessionRescheduledEvent.class));
  }

  @Test
  @DisplayName("Từ chối đề xuất đổi lịch (REJECT) -> Giữ nguyên lịch học cũ")
  void shouldRejectRescheduleSuccessfully() {
    Instant newStartAt = fixedNow.plus(4, ChronoUnit.DAYS);
    Instant newEndAt = newStartAt.plus(90, ChronoUnit.MINUTES);
    Instant oldStartAt = scheduledSession.getStartAt();

    SessionRescheduleProposal proposal = SessionRescheduleProposal.create(
        proposalId, sessionId, tutorUserId, newStartAt, newEndAt, "Dời giờ", newStartAt, fixedNow
    );

    when(proposalRepository.findById(proposalId)).thenReturn(Optional.of(proposal));
    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(scheduledSession));
    when(proposalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    SessionRescheduleProposal result = service.respondToReschedule(
        proposalId,
        studentUser,
        false,
        "Em bị kẹt lịch học ở trường"
    );

    assertEquals(ProposalStatus.REJECTED, result.getStatus());
    assertEquals("Em bị kẹt lịch học ở trường", result.getResponseReason());
    assertEquals(oldStartAt, scheduledSession.getStartAt()); // Không đổi giờ
    verify(eventPublisher).publishEvent(any(SessionRescheduleRejectedEvent.class));
  }

  @Test
  @DisplayName("Ném ForbiddenOperationException nếu người phản hồi không có quyền đối tác")
  void shouldThrowForbiddenWhenUnauthorizedResponder() {
    Instant newStartAt = fixedNow.plus(4, ChronoUnit.DAYS);
    Instant newEndAt = newStartAt.plus(90, ChronoUnit.MINUTES);

    SessionRescheduleProposal proposal = SessionRescheduleProposal.create(
        proposalId, sessionId, tutorUserId, newStartAt, newEndAt, "Dời giờ", newStartAt, fixedNow
    );

    when(proposalRepository.findById(proposalId)).thenReturn(Optional.of(proposal));
    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(scheduledSession));
    doThrow(new ForbiddenOperationException("Bạn không phải thành viên của buổi học này"))
        .when(schedulingSecurityResolver).requireCounterpartyResponseAccess(any(), eq(scheduledSession), eq(tutorUserId));

    CurrentUser stranger = SimpleCurrentUser.of(UUID.randomUUID(), "STUDENT");
    assertThrows(ForbiddenOperationException.class, () ->
        service.respondToReschedule(proposalId, stranger, true, null)
    );
  }

  @Test
  @DisplayName("Ném InvalidOperationException khi chấp nhận đề xuất đổi lịch nhưng buổi học không ở trạng thái SCHEDULED")
  void shouldThrowWhenAcceptingRescheduleForNonScheduledSession() {
    Instant newStartAt = fixedNow.plus(4, ChronoUnit.DAYS);
    Instant newEndAt = newStartAt.plus(90, ChronoUnit.MINUTES);

    SessionRescheduleProposal proposal = SessionRescheduleProposal.create(
        proposalId, sessionId, tutorUserId, newStartAt, newEndAt, "Dời giờ", newStartAt, fixedNow
    );

    TutoringSession cancelledSession = new TutoringSession(
        sessionId, classId, tutorId, studentId,
        scheduledSession.getStartAt(), scheduledSession.getEndAt(), SessionMode.ONLINE, null, null, null,
        SessionStatus.CANCELLED, studentUserId, tutorUserId, fixedNow.minus(1, ChronoUnit.DAYS),
        null, scheduledSession.getStartAt(), null, null, null, null, false, null, null, 1, fixedNow.minus(2, ChronoUnit.DAYS)
    );

    when(proposalRepository.findById(proposalId)).thenReturn(Optional.of(proposal));
    when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(cancelledSession));

    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
        service.respondToReschedule(proposalId, studentUser, true, null)
    );

    assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
    verify(sessionRepository, never()).save(any());
  }

  @Test
  @DisplayName("Tra cứu thành công đề xuất đổi lịch đang chờ xử lý của buổi học (findActiveProposalBySessionId)")
  void shouldFindActiveProposalBySessionIdSuccessfully() {
    Instant newStartAt = fixedNow.plus(4, ChronoUnit.DAYS);
    Instant newEndAt = newStartAt.plus(90, ChronoUnit.MINUTES);

    SessionRescheduleProposal proposal = SessionRescheduleProposal.create(
        proposalId, sessionId, tutorUserId, newStartAt, newEndAt, "Dời giờ", newStartAt, fixedNow
    );

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(scheduledSession));
    when(proposalRepository.findPendingBySessionId(sessionId)).thenReturn(Optional.of(proposal));

    Optional<SessionRescheduleProposal> result = service.findActiveProposalBySessionId(sessionId, studentUser);

    assertTrue(result.isPresent());
    assertEquals(proposalId, result.get().getProposalId());
    verify(schedulingSecurityResolver).requireSessionAccess(studentUser, scheduledSession);
  }

  @Test
  @DisplayName("Người không thuộc buổi học không được xem đề xuất đổi lịch")
  void shouldRejectProposalLookupForNonMember() {
    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(scheduledSession));
    doThrow(new ForbiddenOperationException("Bạn không phải thành viên của buổi học này"))
        .when(schedulingSecurityResolver).requireSessionAccess(studentUser, scheduledSession);

    assertThrows(ForbiddenOperationException.class,
        () -> service.findActiveProposalBySessionId(sessionId, studentUser));
    verify(proposalRepository, never()).findPendingBySessionId(any());
  }
}

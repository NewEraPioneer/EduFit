package vn.edufit.scheduling.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import vn.edufit.scheduling.api.event.SessionProposedEvent;
import vn.edufit.scheduling.api.event.SessionScheduledEvent;
import vn.edufit.scheduling.application.port.ConnectionValidationPort;
import vn.edufit.scheduling.application.service.BatchProposeSessionService;
import vn.edufit.scheduling.application.service.BatchRespondSessionService;
import vn.edufit.scheduling.application.service.SchedulingSecurityResolver;
import vn.edufit.scheduling.application.service.SimpleCurrentUser;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.scheduling.web.request.BatchProposeSessionRequest;
import vn.edufit.scheduling.web.request.BatchProposeSessionRequest.BatchProposeItemRequest;
import vn.edufit.scheduling.web.request.BatchRespondSessionRequest;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Service: Xử lý lô đề xuất & phản hồi hàng loạt (Batch Scheduling)")
class BatchSchedulingTest {

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
  private BatchProposeSessionService batchProposeService;
  private BatchRespondSessionService batchRespondService;

  private final UUID classId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID studentUserId = UUID.randomUUID();
  private final Instant fixedNow = Instant.parse("2026-10-25T08:00:00Z");

  private CurrentUser tutorUser;
  private CurrentUser studentUser;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    batchProposeService = new BatchProposeSessionService(
        sessionRepository,
        historyRepository,
        connectionValidationPort,
        schedulingSecurityResolver,
        eventPublisher,
        fixedClock
    );
    batchRespondService = new BatchRespondSessionService(
        sessionRepository,
        historyRepository,
        schedulingSecurityResolver,
        eventPublisher,
        fixedClock
    );

    tutorUser = SimpleCurrentUser.of(tutorUserId, "TUTOR");
    studentUser = SimpleCurrentUser.of(studentUserId, "STUDENT");
  }

  private List<TutoringSession> proposeBatchHelper(BatchProposeSessionRequest req, CurrentUser user) {
    List<BatchProposeSessionService.ProposeItemCommand> items = req.items().stream()
        .map(i -> new BatchProposeSessionService.ProposeItemCommand(
            i.startAt(), i.endAt(), i.mode(), i.placeOrLink(), i.repeatNote(), i.message()
        ))
        .toList();
    return batchProposeService.proposeBatch(req.classId(), req.tutorId(), req.studentId(), items, user);
  }

  private List<TutoringSession> respondBatchHelper(BatchRespondSessionRequest req, CurrentUser user) {
    return batchRespondService.respondBatch(req.sessionIds(), req.accept(), req.reason(), user);
  }

  // ==========================================
  // BATCH PROPOSE TESTS
  // ==========================================

  @Test
  @DisplayName("Batch Propose: Đề xuất hàng loạt thành công khi tất cả các buổi học hợp lệ")
  void shouldProposeBatchSuccessfully() {
    Instant slot1Start = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant slot1End = slot1Start.plus(90, ChronoUnit.MINUTES);
    Instant slot2Start = fixedNow.plus(26, ChronoUnit.HOURS);
    Instant slot2End = slot2Start.plus(90, ChronoUnit.MINUTES);

    when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);
    when(sessionRepository.findTutorBusySlots(any(), any(), any())).thenReturn(Collections.emptyList());
    when(sessionRepository.findCalendarSessions(any(), any(), any())).thenReturn(Collections.emptyList());
    when(sessionRepository.save(any(TutoringSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

    BatchProposeSessionRequest req = new BatchProposeSessionRequest(
        classId,
        tutorId,
        studentId,
        List.of(
            new BatchProposeItemRequest(slot1Start, slot1End, SessionMode.ONLINE, null, null, "Buổi 1"),
            new BatchProposeItemRequest(slot2Start, slot2End, SessionMode.ONLINE, null, null, "Buổi 2")
        )
    );

    List<TutoringSession> results = proposeBatchHelper(req, tutorUser);

    assertEquals(2, results.size());
    assertEquals(SessionStatus.PROPOSED, results.get(0).getStatus());
    assertEquals(SessionStatus.PROPOSED, results.get(1).getStatus());
    verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(any(SessionProposedEvent.class));
  }

  @Test
  @DisplayName("Batch Propose: Chặn đề xuất và rollback nếu các buổi học trong lô bị trùng giờ nhau (Intra-batch Overlap)")
  void shouldRejectBatchOnIntraBatchOverlap() {
    Instant slot1Start = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant slot1End = slot1Start.plus(90, ChronoUnit.MINUTES);
    // Buổi 2 trùng giờ với buổi 1
    Instant slot2Start = slot1Start.plus(30, ChronoUnit.MINUTES);
    Instant slot2End = slot2Start.plus(90, ChronoUnit.MINUTES);

    when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);

    BatchProposeSessionRequest req = new BatchProposeSessionRequest(
        classId,
        tutorId,
        studentId,
        List.of(
            new BatchProposeItemRequest(slot1Start, slot1End, SessionMode.ONLINE, null, null, "Buổi 1"),
            new BatchProposeItemRequest(slot2Start, slot2End, SessionMode.ONLINE, null, null, "Buổi 2 trùng")
        )
    );

    ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
        proposeBatchHelper(req, tutorUser)
    );
    assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
    verify(sessionRepository, never()).save(any());
  }

  @Test
  @DisplayName("Batch Propose: Chặn đề xuất và rollback nếu 1 buổi học trong lô trùng với lịch dạy đã có của Gia sư")
  void shouldRejectBatchOnTutorScheduleConflict() {
    Instant slot1Start = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant slot1End = slot1Start.plus(90, ChronoUnit.MINUTES);

    when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);

    // Gia sư đã có ca dạy khác trùng khung giờ của slot 1
    TutoringSession existingBusy = TutoringSession.createProposed(
        UUID.randomUUID(), classId, tutorId, studentId,
        slot1Start, slot1End, SessionMode.ONLINE, null, null, null,
        tutorUserId, fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
    );
    when(sessionRepository.findTutorBusySlots(tutorId, slot1Start, slot1End))
        .thenReturn(List.of(existingBusy));

    BatchProposeSessionRequest req = new BatchProposeSessionRequest(
        classId,
        tutorId,
        studentId,
        List.of(new BatchProposeItemRequest(slot1Start, slot1End, SessionMode.ONLINE, null, null, "Buổi 1"))
    );

    assertThrows(ResourceConflictException.class, () ->
        proposeBatchHelper(req, tutorUser)
    );
    verify(sessionRepository, never()).save(any());
  }

  @Test
  @DisplayName("Batch Propose: Từ chối nếu thời điểm bắt đầu ở trong quá khứ")
  void shouldRejectBatchWhenStartAtInPast() {
    Instant pastStart = fixedNow.minus(10, ChronoUnit.MINUTES);
    Instant pastEnd = pastStart.plus(60, ChronoUnit.MINUTES);

    when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);

    BatchProposeSessionRequest req = new BatchProposeSessionRequest(
        classId,
        tutorId,
        studentId,
        List.of(new BatchProposeItemRequest(pastStart, pastEnd, SessionMode.ONLINE, null, null, "Buổi quá khứ"))
    );

    assertThrows(InvalidOperationException.class, () ->
        proposeBatchHelper(req, tutorUser)
    );
  }

  @Test
  @DisplayName("Batch Propose: Bẫy lỗi GiST Exclusion Constraint và chuyển thành SCHEDULE_OVERLAP (HTTP 409)")
  void shouldTrapGiSTExclusionConstraintOnProposeBatch() {
    Instant slotStart = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant slotEnd = slotStart.plus(90, ChronoUnit.MINUTES);

    when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);
    when(sessionRepository.findTutorBusySlots(any(), any(), any())).thenReturn(Collections.emptyList());
    when(sessionRepository.findCalendarSessions(any(), any(), any())).thenReturn(Collections.emptyList());
    when(sessionRepository.save(any())).thenThrow(new DataIntegrityViolationException("exclusion constraint violation"));

    BatchProposeSessionRequest req = new BatchProposeSessionRequest(
        classId,
        tutorId,
        studentId,
        List.of(new BatchProposeItemRequest(slotStart, slotEnd, SessionMode.ONLINE, null, null, "Buổi 1"))
    );

    ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
        proposeBatchHelper(req, tutorUser)
    );
    assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
  }

  // ==========================================
  // BATCH RESPOND TESTS
  // ==========================================

  @Test
  @DisplayName("Batch Respond: Phản hồi chấp thuận (ACCEPT) thành công toàn bộ lô")
  void shouldAcceptBatchSuccessfully() {
    UUID session1Id = UUID.randomUUID();
    UUID session2Id = UUID.randomUUID();

    Instant start1 = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant end1 = start1.plus(90, ChronoUnit.MINUTES);
    Instant start2 = fixedNow.plus(26, ChronoUnit.HOURS);
    Instant end2 = start2.plus(90, ChronoUnit.MINUTES);

    TutoringSession s1 = TutoringSession.createProposed(
        session1Id, classId, tutorId, studentId, start1, end1, SessionMode.ONLINE, null, null, null,
        tutorUserId, fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
    );
    TutoringSession s2 = TutoringSession.createProposed(
        session2Id, classId, tutorId, studentId, start2, end2, SessionMode.ONLINE, null, null, null,
        tutorUserId, fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
    );

    when(sessionRepository.findByIdForUpdate(session1Id)).thenReturn(Optional.of(s1));
    when(sessionRepository.findByIdForUpdate(session2Id)).thenReturn(Optional.of(s2));
    when(sessionRepository.findTutorBusySlots(any(), any(), any())).thenReturn(Collections.emptyList());
    when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    BatchRespondSessionRequest req = new BatchRespondSessionRequest(
        List.of(session1Id, session2Id),
        true,
        null
    );

    List<TutoringSession> results = respondBatchHelper(req, studentUser);

    assertEquals(2, results.size());
    assertEquals(SessionStatus.SCHEDULED, results.get(0).getStatus());
    assertEquals(SessionStatus.SCHEDULED, results.get(1).getStatus());
    verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(any(SessionScheduledEvent.class));
  }

  @Test
  @DisplayName("Batch Respond: Phản hồi từ chối (REJECT) thành công toàn bộ lô")
  void shouldRejectBatchSuccessfully() {
    UUID session1Id = UUID.randomUUID();
    UUID session2Id = UUID.randomUUID();

    Instant start1 = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant end1 = start1.plus(90, ChronoUnit.MINUTES);

    TutoringSession s1 = TutoringSession.createProposed(
        session1Id, classId, tutorId, studentId, start1, end1, SessionMode.ONLINE, null, null, null,
        tutorUserId, fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
    );
    TutoringSession s2 = TutoringSession.createProposed(
        session2Id, classId, tutorId, studentId, start1.plus(1, ChronoUnit.DAYS), end1.plus(1, ChronoUnit.DAYS),
        SessionMode.ONLINE, null, null, null, tutorUserId, fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
    );

    when(sessionRepository.findByIdForUpdate(session1Id)).thenReturn(Optional.of(s1));
    when(sessionRepository.findByIdForUpdate(session2Id)).thenReturn(Optional.of(s2));
    when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    BatchRespondSessionRequest req = new BatchRespondSessionRequest(
        List.of(session1Id, session2Id),
        false,
        "Không phù hợp thời gian"
    );

    List<TutoringSession> results = respondBatchHelper(req, studentUser);

    assertEquals(2, results.size());
    assertEquals(SessionStatus.REJECTED, results.get(0).getStatus());
    assertEquals(SessionStatus.REJECTED, results.get(1).getStatus());
    verify(eventPublisher, never()).publishEvent(any(SessionScheduledEvent.class));
  }

  @Test
  @DisplayName("Batch Respond: Từ chối nếu người phản hồi không phải là đối tác (Forbidden)")
  void shouldRejectBatchWhenResponderIsNotCounterparty() {
    UUID session1Id = UUID.randomUUID();
    Instant start1 = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant end1 = start1.plus(90, ChronoUnit.MINUTES);

    TutoringSession s1 = TutoringSession.createProposed(
        session1Id, classId, tutorId, studentId, start1, end1, SessionMode.ONLINE, null, null, null,
        tutorUserId, fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
    );
    when(sessionRepository.findByIdForUpdate(session1Id)).thenReturn(Optional.of(s1));
    doThrow(new ForbiddenOperationException("Bạn không phải thành viên của buổi học này"))
        .when(schedulingSecurityResolver).requireCounterpartyResponseAccess(any(), any());

    BatchRespondSessionRequest req = new BatchRespondSessionRequest(
        List.of(session1Id),
        true,
        null
    );

    assertThrows(ForbiddenOperationException.class, () ->
        respondBatchHelper(req, tutorUser)
    );
  }
}

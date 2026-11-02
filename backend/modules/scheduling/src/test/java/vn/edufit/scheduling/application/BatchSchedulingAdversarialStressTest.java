package vn.edufit.scheduling.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import vn.edufit.scheduling.api.event.SessionProposedEvent;
import vn.edufit.scheduling.api.event.SessionScheduledEvent;
import vn.edufit.scheduling.application.port.ConnectionValidationPort;
import vn.edufit.scheduling.application.service.BatchProposeSessionService;
import vn.edufit.scheduling.application.service.BatchProposeSessionService.ProposeItemCommand;
import vn.edufit.scheduling.application.service.BatchRespondSessionService;
import vn.edufit.scheduling.application.service.SchedulingSecurityResolver;
import vn.edufit.scheduling.application.service.SimpleCurrentUser;
import vn.edufit.scheduling.domain.model.CancelReason;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Adversarial Stress & Chaos Test Suite for Batch Propose and Batch Respond Services.
 *
 * Scenarios tested:
 * 1. Intra-batch overlaps (partial, identical, nested, reverse-order, multi-session chain).
 * 2. Boundary touch (adjacent slots [start, end) and [end, nextStart)) must succeed.
 * 3. All-or-Nothing atomic integrity: 1 conflict in N sessions ensures 0 sessions saved.
 * 4. Batch Respond with non-existent, cancelled, scheduled, or expired sessions.
 * 5. Batch Respond intra-batch conflicts (same tutor or same student).
 * 6. Batch Respond deduplication and unauthorized counterparty access.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Adversarial Stress Test: Batch Propose & Batch Respond Invariants")
class BatchSchedulingAdversarialStressTest {

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
  private final Instant fixedNow = Instant.parse("2026-11-01T08:00:00Z");

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

    org.mockito.Mockito.lenient().when(sessionRepository.save(any(TutoringSession.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    org.mockito.Mockito.lenient().when(sessionRepository.findTutorBusySlots(any(), any(), any()))
        .thenReturn(Collections.emptyList());
    org.mockito.Mockito.lenient().when(sessionRepository.findCalendarSessions(any(), any(), any()))
        .thenReturn(Collections.emptyList());
  }

  // =========================================================================
  // 1. INTRA-BATCH OVERLAPS IN BATCH PROPOSE
  // =========================================================================
  @Nested
  @DisplayName("1. Batch Propose Intra-Batch Overlap Checks")
  class BatchProposeIntraBatchOverlapTests {

    @Test
    @DisplayName("Adversarial: 3 sessions where S1 and S2 partially overlap -> fails entire batch with SCHEDULE_OVERLAP")
    void shouldFailBatchWhenTwoSessionsPartiallyOverlap() {
      Instant s1Start = fixedNow.plus(2, ChronoUnit.HOURS);
      Instant s1End = s1Start.plus(90, ChronoUnit.MINUTES);
      Instant s2Start = s1Start.plus(60, ChronoUnit.MINUTES); // Overlaps s1 by 30 mins
      Instant s2End = s2Start.plus(90, ChronoUnit.MINUTES);
      Instant s3Start = fixedNow.plus(24, ChronoUnit.HOURS);
      Instant s3End = s3Start.plus(90, ChronoUnit.MINUTES);

      when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);

      List<ProposeItemCommand> items = List.of(
          new ProposeItemCommand(s1Start, s1End, SessionMode.ONLINE, null, null, "S1"),
          new ProposeItemCommand(s2Start, s2End, SessionMode.ONLINE, null, null, "S2 Overlap"),
          new ProposeItemCommand(s3Start, s3End, SessionMode.ONLINE, null, null, "S3 Valid")
      );

      ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
          batchProposeService.proposeBatch(classId, tutorId, studentId, items, tutorUser)
      );

      assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
      verify(sessionRepository, never()).save(any());
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Adversarial: 2 sessions with exactly identical start and end time -> fails entire batch")
    void shouldFailBatchWhenTwoSessionsHaveIdenticalTimes() {
      Instant sStart = fixedNow.plus(4, ChronoUnit.HOURS);
      Instant sEnd = sStart.plus(120, ChronoUnit.MINUTES);

      when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);

      List<ProposeItemCommand> items = List.of(
          new ProposeItemCommand(sStart, sEnd, SessionMode.ONLINE, null, null, "Duplicate 1"),
          new ProposeItemCommand(sStart, sEnd, SessionMode.ONLINE, null, null, "Duplicate 2")
      );

      ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
          batchProposeService.proposeBatch(classId, tutorId, studentId, items, tutorUser)
      );

      assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
      verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Adversarial: Sub-interval nesting (S2 is completely enclosed inside S1) -> fails entire batch")
    void shouldFailBatchWhenSessionIsCompletelyNestedInsideAnother() {
      Instant s1Start = fixedNow.plus(2, ChronoUnit.HOURS);
      Instant s1End = s1Start.plus(180, ChronoUnit.MINUTES); // 3-hour session
      Instant s2Start = s1Start.plus(30, ChronoUnit.MINUTES);
      Instant s2End = s2Start.plus(60, ChronoUnit.MINUTES); // 1-hour session nested inside S1

      when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);

      List<ProposeItemCommand> items = List.of(
          new ProposeItemCommand(s1Start, s1End, SessionMode.ONLINE, null, null, "Outer"),
          new ProposeItemCommand(s2Start, s2End, SessionMode.ONLINE, null, null, "Inner")
      );

      ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
          batchProposeService.proposeBatch(classId, tutorId, studentId, items, tutorUser)
      );

      assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
      verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Adversarial: Reverse-order overlap (S2 starts before S1 and overlaps) -> detected and rejected")
    void shouldFailBatchWhenReverseOrderItemsOverlap() {
      Instant s1Start = fixedNow.plus(10, ChronoUnit.HOURS);
      Instant s1End = s1Start.plus(90, ChronoUnit.MINUTES);
      Instant s2Start = fixedNow.plus(9, ChronoUnit.HOURS);
      Instant s2End = s2Start.plus(90, ChronoUnit.MINUTES); // Overlaps s1Start

      when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);

      List<ProposeItemCommand> items = List.of(
          new ProposeItemCommand(s1Start, s1End, SessionMode.ONLINE, null, null, "Later item in list"),
          new ProposeItemCommand(s2Start, s2End, SessionMode.ONLINE, null, null, "Earlier time item")
      );

      ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
          batchProposeService.proposeBatch(classId, tutorId, studentId, items, tutorUser)
      );

      assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
      verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Boundary Check: Adjacent slots [start1, end1) and [end1, end2) MUST SUCCEED (no overlap)")
    void shouldAllowAdjacentConsecutiveSlotsWithoutOverlap() {
      Instant s1Start = fixedNow.plus(2, ChronoUnit.HOURS);
      Instant s1End = s1Start.plus(90, ChronoUnit.MINUTES);
      Instant s2Start = s1End; // Exactly touches s1End
      Instant s2End = s2Start.plus(90, ChronoUnit.MINUTES);

      when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);
      when(sessionRepository.findTutorBusySlots(any(), any(), any())).thenReturn(Collections.emptyList());
      when(sessionRepository.findCalendarSessions(any(), any(), any())).thenReturn(Collections.emptyList());
      when(sessionRepository.save(any(TutoringSession.class))).thenAnswer(inv -> inv.getArgument(0));

      List<ProposeItemCommand> items = List.of(
          new ProposeItemCommand(s1Start, s1End, SessionMode.ONLINE, null, null, "Slot 1"),
          new ProposeItemCommand(s2Start, s2End, SessionMode.ONLINE, null, null, "Slot 2 (Adjacent)")
      );

      List<TutoringSession> results = batchProposeService.proposeBatch(classId, tutorId, studentId, items, tutorUser);

      assertEquals(2, results.size());
      verify(sessionRepository, times(2)).save(any());
    }
  }

  // =========================================================================
  // 2. ALL-OR-NOTHING ATOMIC INTEGRITY IN BATCH PROPOSE
  // =========================================================================
  @Nested
  @DisplayName("2. Batch Propose All-or-Nothing Atomic Rollback")
  class BatchProposeAtomicRollbackTests {

    @Test
    @DisplayName("All-or-Nothing: When 1 of 5 items conflicts with DB tutor busy slots -> 0 sessions saved")
    void shouldNotPersistAnySessionWhenOneOfFiveConflictsWithTutorBusySlot() {
      when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);

      List<ProposeItemCommand> items = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        Instant start = fixedNow.plus(2 + (i * 4), ChronoUnit.HOURS);
        Instant end = start.plus(90, ChronoUnit.MINUTES);
        items.add(new ProposeItemCommand(start, end, SessionMode.ONLINE, null, null, "Session " + i));
      }

      // Slot 4 (index 3) conflicts with tutor busy slot in DB
      ProposeItemCommand conflictingItem = items.get(3);
      TutoringSession existingBusy = TutoringSession.createProposed(
          UUID.randomUUID(), classId, tutorId, studentId,
          conflictingItem.startAt(), conflictingItem.endAt(),
          SessionMode.ONLINE, null, null, null, tutorUserId,
          fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );

      when(sessionRepository.findTutorBusySlots(tutorId, conflictingItem.startAt(), conflictingItem.endAt()))
          .thenReturn(List.of(existingBusy));

      ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
          batchProposeService.proposeBatch(classId, tutorId, studentId, items, tutorUser)
      );

      assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
      // Zero sessions should ever be saved or published
      verify(sessionRepository, never()).save(any());
      verify(historyRepository, never()).save(any());
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("All-or-Nothing: When 1 of 4 items conflicts with Student's existing scheduled session -> 0 sessions saved")
    void shouldNotPersistAnySessionWhenOneConflictsWithStudentSchedule() {
      when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);

      List<ProposeItemCommand> items = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        Instant start = fixedNow.plus(3 + (i * 3), ChronoUnit.HOURS);
        Instant end = start.plus(60, ChronoUnit.MINUTES);
        items.add(new ProposeItemCommand(start, end, SessionMode.ONLINE, null, null, "Item " + i));
      }

      ProposeItemCommand conflictingItem = items.get(2);
      TutoringSession studentBusy = TutoringSession.createScheduled(
          UUID.randomUUID(), classId, UUID.randomUUID(), studentId,
          conflictingItem.startAt(), conflictingItem.endAt(),
          SessionMode.ONLINE, null, null, null, tutorUserId, studentUserId, fixedNow
      );

      when(sessionRepository.findTutorBusySlots(any(), any(), any())).thenReturn(Collections.emptyList());
      when(sessionRepository.findCalendarSessions(studentId, conflictingItem.startAt(), conflictingItem.endAt()))
          .thenReturn(List.of(studentBusy));

      ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
          batchProposeService.proposeBatch(classId, tutorId, studentId, items, tutorUser)
      );

      assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
      verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("All-or-Nothing: Item 4 has invalid duration (> 240 mins) -> fails with VALIDATION_FAILED, 0 saved")
    void shouldNotPersistAnySessionWhenOneItemHasInvalidDuration() {
      when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);

      Instant s1 = fixedNow.plus(2, ChronoUnit.HOURS);
      Instant s2 = fixedNow.plus(6, ChronoUnit.HOURS);
      Instant s3 = fixedNow.plus(10, ChronoUnit.HOURS);
      Instant s4 = fixedNow.plus(14, ChronoUnit.HOURS);

      List<ProposeItemCommand> items = List.of(
          new ProposeItemCommand(s1, s1.plus(60, ChronoUnit.MINUTES), SessionMode.ONLINE, null, null, "Valid 1"),
          new ProposeItemCommand(s2, s2.plus(60, ChronoUnit.MINUTES), SessionMode.ONLINE, null, null, "Valid 2"),
          new ProposeItemCommand(s3, s3.plus(60, ChronoUnit.MINUTES), SessionMode.ONLINE, null, null, "Valid 3"),
          new ProposeItemCommand(s4, s4.plus(300, ChronoUnit.MINUTES), SessionMode.ONLINE, null, null, "Invalid 300m")
      );

      InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
          batchProposeService.proposeBatch(classId, tutorId, studentId, items, tutorUser)
      );

      assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
      verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("All-or-Nothing: DataIntegrityViolationException during 2nd item save -> rethrows SCHEDULE_OVERLAP")
    void shouldCatchDataIntegrityViolationOnSaveAndRollback() {
      when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);
      when(sessionRepository.findTutorBusySlots(any(), any(), any())).thenReturn(Collections.emptyList());
      when(sessionRepository.findCalendarSessions(any(), any(), any())).thenReturn(Collections.emptyList());

      Instant s1 = fixedNow.plus(2, ChronoUnit.HOURS);
      Instant s2 = fixedNow.plus(6, ChronoUnit.HOURS);

      List<ProposeItemCommand> items = List.of(
          new ProposeItemCommand(s1, s1.plus(60, ChronoUnit.MINUTES), SessionMode.ONLINE, null, null, "S1"),
          new ProposeItemCommand(s2, s2.plus(60, ChronoUnit.MINUTES), SessionMode.ONLINE, null, null, "S2")
      );

      when(sessionRepository.save(any()))
          .thenAnswer(inv -> inv.getArgument(0))
          .thenThrow(new DataIntegrityViolationException("GiST exclusion violation"));

      ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
          batchProposeService.proposeBatch(classId, tutorId, studentId, items, tutorUser)
      );

      assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
    }
  }

  // =========================================================================
  // 3. INTRA-BATCH OVERLAPS IN BATCH RESPOND
  // =========================================================================
  @Nested
  @DisplayName("3. Batch Respond Intra-Batch Overlap Checks")
  class BatchRespondIntraBatchOverlapTests {

    @Test
    @DisplayName("Adversarial: Accepting batch where 2 sessions belong to the same tutor and overlap -> fails with SCHEDULE_OVERLAP")
    void shouldFailBatchRespondWhenSessionsForSameTutorOverlap() {
      UUID s1Id = UUID.randomUUID();
      UUID s2Id = UUID.randomUUID();

      Instant start1 = fixedNow.plus(2, ChronoUnit.HOURS);
      Instant end1 = start1.plus(90, ChronoUnit.MINUTES);
      Instant start2 = start1.plus(30, ChronoUnit.MINUTES); // Overlaps s1
      Instant end2 = start2.plus(90, ChronoUnit.MINUTES);

      TutoringSession s1 = TutoringSession.createProposed(
          s1Id, classId, tutorId, studentId, start1, end1,
          SessionMode.ONLINE, null, null, null, tutorUserId,
          fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );
      TutoringSession s2 = TutoringSession.createProposed(
          s2Id, classId, tutorId, studentId, start2, end2,
          SessionMode.ONLINE, null, null, null, tutorUserId,
          fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );

      when(sessionRepository.findByIdForUpdate(s1Id)).thenReturn(Optional.of(s1));
      when(sessionRepository.findByIdForUpdate(s2Id)).thenReturn(Optional.of(s2));

      ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
          batchRespondService.respondBatch(List.of(s1Id, s2Id), true, null, studentUser)
      );

      assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
      verify(sessionRepository, never()).save(any());
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Adversarial: Accepting batch where 2 sessions belong to the same student and overlap -> fails with SCHEDULE_OVERLAP")
    void shouldFailBatchRespondWhenSessionsForSameStudentOverlap() {
      UUID s1Id = UUID.randomUUID();
      UUID s2Id = UUID.randomUUID();
      UUID anotherTutorId = UUID.randomUUID();

      Instant start1 = fixedNow.plus(2, ChronoUnit.HOURS);
      Instant end1 = start1.plus(90, ChronoUnit.MINUTES);
      Instant start2 = start1.plus(45, ChronoUnit.MINUTES); // Overlaps s1
      Instant end2 = start2.plus(90, ChronoUnit.MINUTES);

      TutoringSession s1 = TutoringSession.createProposed(
          s1Id, classId, tutorId, studentId, start1, end1,
          SessionMode.ONLINE, null, null, null, tutorUserId,
          fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );
      TutoringSession s2 = TutoringSession.createProposed(
          s2Id, UUID.randomUUID(), anotherTutorId, studentId, start2, end2,
          SessionMode.ONLINE, null, null, null, UUID.randomUUID(),
          fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );

      when(sessionRepository.findByIdForUpdate(s1Id)).thenReturn(Optional.of(s1));
      when(sessionRepository.findByIdForUpdate(s2Id)).thenReturn(Optional.of(s2));

      ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
          batchRespondService.respondBatch(List.of(s1Id, s2Id), true, null, studentUser)
      );

      assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
      verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Adversarial: Rejecting batch with overlapping sessions MUST SUCCEED (rejection removes conflict)")
    void shouldAllowBatchRejectionEvenIfSessionsOverlap() {
      UUID s1Id = UUID.randomUUID();
      UUID s2Id = UUID.randomUUID();

      Instant start1 = fixedNow.plus(2, ChronoUnit.HOURS);
      Instant end1 = start1.plus(90, ChronoUnit.MINUTES);
      Instant start2 = start1.plus(30, ChronoUnit.MINUTES);
      Instant end2 = start2.plus(90, ChronoUnit.MINUTES);

      TutoringSession s1 = TutoringSession.createProposed(
          s1Id, classId, tutorId, studentId, start1, end1,
          SessionMode.ONLINE, null, null, null, tutorUserId,
          fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );
      TutoringSession s2 = TutoringSession.createProposed(
          s2Id, classId, tutorId, studentId, start2, end2,
          SessionMode.ONLINE, null, null, null, tutorUserId,
          fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );

      when(sessionRepository.findByIdForUpdate(s1Id)).thenReturn(Optional.of(s1));
      when(sessionRepository.findByIdForUpdate(s2Id)).thenReturn(Optional.of(s2));
      when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      List<TutoringSession> results = batchRespondService.respondBatch(
          List.of(s1Id, s2Id), false, "Overlapping requests rejected", studentUser
      );

      assertEquals(2, results.size());
      assertEquals(SessionStatus.REJECTED, results.get(0).getStatus());
      assertEquals(SessionStatus.REJECTED, results.get(1).getStatus());
      verify(sessionRepository, times(2)).save(any());
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("All-or-Nothing: When 1 of 2 items conflicts with Student's existing scheduled session -> 0 sessions saved")
    void shouldNotPersistAnySessionWhenOneConflictsWithStudentScheduleOnBatchRespond() {
      UUID s1Id = UUID.randomUUID();
      UUID s2Id = UUID.randomUUID();

      Instant start1 = fixedNow.plus(2, ChronoUnit.HOURS);
      Instant end1 = start1.plus(90, ChronoUnit.MINUTES);
      Instant start2 = fixedNow.plus(26, ChronoUnit.HOURS);
      Instant end2 = start2.plus(90, ChronoUnit.MINUTES);

      TutoringSession s1 = TutoringSession.createProposed(
          s1Id, classId, tutorId, studentId, start1, end1, SessionMode.ONLINE, null, null, null,
          tutorUserId, fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );
      TutoringSession s2 = TutoringSession.createProposed(
          s2Id, classId, tutorId, studentId, start2, end2, SessionMode.ONLINE, null, null, null,
          tutorUserId, fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );

      when(sessionRepository.findByIdForUpdate(s1Id)).thenReturn(Optional.of(s1));
      when(sessionRepository.findByIdForUpdate(s2Id)).thenReturn(Optional.of(s2));
      when(sessionRepository.findTutorBusySlots(any(), any(), any())).thenReturn(Collections.emptyList());

      // Student has another scheduled session overlapping with s2
      TutoringSession studentBusy = TutoringSession.createScheduled(
          UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), studentId,
          start2, end2, SessionMode.ONLINE, null, null, null, UUID.randomUUID(), studentUserId, fixedNow
      );
      when(sessionRepository.findCalendarSessions(studentId, start2, end2))
          .thenReturn(List.of(studentBusy));

      ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
          batchRespondService.respondBatch(List.of(s1Id, s2Id), true, null, studentUser)
      );

      assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
      verify(sessionRepository, never()).save(any());
      verify(eventPublisher, never()).publishEvent(any());
    }
  }

  // =========================================================================
  // 4. BATCH RESPOND INVALID SESSION STATES & ATOMIC ROLLBACK
  // =========================================================================
  @Nested
  @DisplayName("4. Batch Respond Invalid Session States & Rollback")
  class BatchRespondStateValidationTests {

    @Test
    @DisplayName("Adversarial: When one sessionId in batch does not exist -> fails with EntityNotFoundException, 0 updated")
    void shouldFailWhenOneSessionDoesNotExistInBatch() {
      UUID validId = UUID.randomUUID();
      UUID nonExistentId = UUID.randomUUID();

      Instant start = fixedNow.plus(2, ChronoUnit.HOURS);
      Instant end = start.plus(60, ChronoUnit.MINUTES);
      TutoringSession validSession = TutoringSession.createProposed(
          validId, classId, tutorId, studentId, start, end,
          SessionMode.ONLINE, null, null, null, tutorUserId,
          fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );

      when(sessionRepository.findByIdForUpdate(validId)).thenReturn(Optional.of(validSession));
      when(sessionRepository.findByIdForUpdate(nonExistentId)).thenReturn(Optional.empty());

      assertThrows(EntityNotFoundException.class, () ->
          batchRespondService.respondBatch(List.of(validId, nonExistentId), true, null, studentUser)
      );

      verify(sessionRepository, never()).save(any());
      verify(eventPublisher, never()).publishEvent(any());
    }


    @Test
    @DisplayName("Adversarial: When cancelled session is sorted first -> fails immediately on iteration 0, 0 saves")
    void shouldFailImmediatelyWhenFirstSortedSessionIsCancelled() {
      UUID idA = UUID.randomUUID();
      UUID idB = UUID.randomUUID();
      final UUID firstId = idA.compareTo(idB) < 0 ? idA : idB;
      final UUID secondId = idA.compareTo(idB) < 0 ? idB : idA;

      Instant start1 = fixedNow.plus(2, ChronoUnit.HOURS);
      Instant end1 = start1.plus(60, ChronoUnit.MINUTES);
      Instant start2 = fixedNow.plus(10, ChronoUnit.HOURS);
      Instant end2 = start2.plus(60, ChronoUnit.MINUTES);

      // firstId is CANCELLED, secondId is PROPOSED
      TutoringSession sCancelled = TutoringSession.createScheduled(
          firstId, classId, tutorId, studentId, start1, end1,
          SessionMode.ONLINE, null, null, null, tutorUserId, studentUserId, fixedNow
      );
      sCancelled.cancel(tutorUserId, CancelReason.SCHEDULE_CONFLICT, "Cancelled beforehand", false, fixedNow);

      TutoringSession sProposed = TutoringSession.createProposed(
          secondId, classId, tutorId, studentId, start2, end2,
          SessionMode.ONLINE, null, null, null, tutorUserId,
          fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );

      when(sessionRepository.findByIdForUpdate(firstId)).thenReturn(Optional.of(sCancelled));
      when(sessionRepository.findByIdForUpdate(secondId)).thenReturn(Optional.of(sProposed));

      InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
          batchRespondService.respondBatch(List.of(firstId, secondId), true, null, studentUser)
      );

      assertEquals(ErrorCode.SESSION_ALREADY_FINALIZED, ex.getErrorCode());
      verify(sessionRepository, never()).save(any());
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Adversarial: When cancelled session is sorted second, pre-validation prevents premature save and event emission")
    void shouldPreventPrematureSaveAndEventLeakWhenCancelledSessionIsSortedSecond() {
      UUID idA = UUID.randomUUID();
      UUID idB = UUID.randomUUID();
      final UUID firstId = idA.compareTo(idB) < 0 ? idA : idB;
      final UUID secondId = idA.compareTo(idB) < 0 ? idB : idA;

      Instant start1 = fixedNow.plus(2, ChronoUnit.HOURS);
      Instant end1 = start1.plus(60, ChronoUnit.MINUTES);
      Instant start2 = fixedNow.plus(10, ChronoUnit.HOURS);
      Instant end2 = start2.plus(60, ChronoUnit.MINUTES);

      // firstId is PROPOSED, secondId is CANCELLED
      TutoringSession sProposed = TutoringSession.createProposed(
          firstId, classId, tutorId, studentId, start1, end1,
          SessionMode.ONLINE, null, null, null, tutorUserId,
          fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );

      TutoringSession sCancelled = TutoringSession.createScheduled(
          secondId, classId, tutorId, studentId, start2, end2,
          SessionMode.ONLINE, null, null, null, tutorUserId, studentUserId, fixedNow
      );
      sCancelled.cancel(tutorUserId, CancelReason.SCHEDULE_CONFLICT, "Cancelled beforehand", false, fixedNow);

      when(sessionRepository.findByIdForUpdate(firstId)).thenReturn(Optional.of(sProposed));
      when(sessionRepository.findByIdForUpdate(secondId)).thenReturn(Optional.of(sCancelled));

      InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
          batchRespondService.respondBatch(List.of(firstId, secondId), true, null, studentUser)
      );

      assertEquals(ErrorCode.SESSION_ALREADY_FINALIZED, ex.getErrorCode());
      verify(sessionRepository, never()).save(any());
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Adversarial: When first sorted session is already SCHEDULED -> fails immediately on iteration 0, 0 saves")
    void shouldFailWhenFirstSortedSessionIsAlreadyScheduled() {
      UUID idA = UUID.randomUUID();
      UUID idB = UUID.randomUUID();
      final UUID firstId = idA.compareTo(idB) < 0 ? idA : idB;
      final UUID secondId = idA.compareTo(idB) < 0 ? idB : idA;

      Instant start1 = fixedNow.plus(2, ChronoUnit.HOURS);
      Instant end1 = start1.plus(60, ChronoUnit.MINUTES);
      Instant start2 = fixedNow.plus(10, ChronoUnit.HOURS);
      Instant end2 = start2.plus(60, ChronoUnit.MINUTES);

      // firstId is SCHEDULED, secondId is PROPOSED
      TutoringSession sScheduled = TutoringSession.createScheduled(
          firstId, classId, tutorId, studentId, start1, end1,
          SessionMode.ONLINE, null, null, null, tutorUserId, studentUserId, fixedNow
      );
      TutoringSession sProposed = TutoringSession.createProposed(
          secondId, classId, tutorId, studentId, start2, end2,
          SessionMode.ONLINE, null, null, null, tutorUserId,
          fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );

      when(sessionRepository.findByIdForUpdate(firstId)).thenReturn(Optional.of(sScheduled));
      when(sessionRepository.findByIdForUpdate(secondId)).thenReturn(Optional.of(sProposed));

      InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
          batchRespondService.respondBatch(List.of(firstId, secondId), true, null, studentUser)
      );

      assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
      verify(sessionRepository, never()).save(any());
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Adversarial: When first sorted session is EXPIRED -> fails immediately on iteration 0, 0 saves")
    void shouldFailWhenFirstSortedSessionIsExpired() {
      UUID idA = UUID.randomUUID();
      UUID idB = UUID.randomUUID();
      final UUID firstId = idA.compareTo(idB) < 0 ? idA : idB;
      final UUID secondId = idA.compareTo(idB) < 0 ? idB : idA;

      Instant start1 = fixedNow.plus(2, ChronoUnit.HOURS);
      Instant end1 = start1.plus(60, ChronoUnit.MINUTES);
      Instant start2 = fixedNow.plus(10, ChronoUnit.HOURS);
      Instant end2 = start2.plus(60, ChronoUnit.MINUTES);

      // firstId is EXPIRED
      TutoringSession sExpired = TutoringSession.createProposed(
          firstId, classId, tutorId, studentId, start1, end1,
          SessionMode.ONLINE, null, null, null, tutorUserId,
          fixedNow.minus(1, ChronoUnit.HOURS), fixedNow.minus(2, ChronoUnit.DAYS)
      );
      TutoringSession sProposed = TutoringSession.createProposed(
          secondId, classId, tutorId, studentId, start2, end2,
          SessionMode.ONLINE, null, null, null, tutorUserId,
          fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );

      when(sessionRepository.findByIdForUpdate(firstId)).thenReturn(Optional.of(sExpired));
      when(sessionRepository.findByIdForUpdate(secondId)).thenReturn(Optional.of(sProposed));

      InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
          batchRespondService.respondBatch(List.of(firstId, secondId), true, null, studentUser)
      );

      assertEquals(ErrorCode.PROPOSAL_EXPIRED, ex.getErrorCode());
      verify(sessionRepository, never()).save(any());
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Adversarial: Caller is not counterparty for one of the sessions -> fails with ForbiddenOperationException")
    void shouldFailWhenCallerIsNotCounterpartyForOneSession() {
      UUID s1Id = UUID.randomUUID();
      UUID s2Id = UUID.randomUUID();

      Instant start1 = fixedNow.plus(2, ChronoUnit.HOURS);
      Instant end1 = start1.plus(60, ChronoUnit.MINUTES);
      Instant start2 = fixedNow.plus(10, ChronoUnit.HOURS);
      Instant end2 = start2.plus(60, ChronoUnit.MINUTES);

      TutoringSession s1 = TutoringSession.createProposed(
          s1Id, classId, tutorId, studentId, start1, end1,
          SessionMode.ONLINE, null, null, null, tutorUserId,
          fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );

      TutoringSession s2 = TutoringSession.createProposed(
          s2Id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), start2, end2,
          SessionMode.ONLINE, null, null, null, UUID.randomUUID(),
          fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );

      when(sessionRepository.findByIdForUpdate(s1Id)).thenReturn(Optional.of(s1));
      when(sessionRepository.findByIdForUpdate(s2Id)).thenReturn(Optional.of(s2));
      doThrow(new ForbiddenOperationException("Bạn không phải thành viên của buổi học này"))
          .when(schedulingSecurityResolver).requireCounterpartyResponseAccess(studentUser, s2);

      assertThrows(ForbiddenOperationException.class, () ->
          batchRespondService.respondBatch(List.of(s1Id, s2Id), true, null, studentUser)
      );

      verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deduplication: Duplicate sessionIds in request are deduplicated and processed once")
    void shouldDeduplicateSessionIdsInBatchRespond() {
      UUID s1Id = UUID.randomUUID();

      Instant start1 = fixedNow.plus(2, ChronoUnit.HOURS);
      Instant end1 = start1.plus(60, ChronoUnit.MINUTES);

      TutoringSession s1 = TutoringSession.createProposed(
          s1Id, classId, tutorId, studentId, start1, end1,
          SessionMode.ONLINE, null, null, null, tutorUserId,
          fixedNow.plus(1, ChronoUnit.DAYS), fixedNow
      );

      when(sessionRepository.findByIdForUpdate(s1Id)).thenReturn(Optional.of(s1));
      when(sessionRepository.findTutorBusySlots(any(), any(), any())).thenReturn(Collections.emptyList());
      when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // Pass duplicate s1Id 3 times
      List<TutoringSession> results = batchRespondService.respondBatch(
          List.of(s1Id, s1Id, s1Id), true, null, studentUser
      );

      assertEquals(1, results.size());
      verify(sessionRepository, times(1)).findByIdForUpdate(s1Id);
      verify(sessionRepository, times(1)).save(any());
    }
  }

  // =========================================================================
  // 5. INPUT VALIDATION EDGE CASES
  // =========================================================================
  @Nested
  @DisplayName("5. Input Validation Edge Cases")
  class InputValidationEdgeCases {

    @Test
    @DisplayName("Batch Propose: Null or empty items list -> throws VALIDATION_FAILED")
    void shouldRejectEmptyOrNullItemsList() {
      assertThrows(InvalidOperationException.class, () ->
          batchProposeService.proposeBatch(classId, tutorId, studentId, null, tutorUser)
      );
      assertThrows(InvalidOperationException.class, () ->
          batchProposeService.proposeBatch(classId, tutorId, studentId, Collections.emptyList(), tutorUser)
      );
    }

    @Test
    @DisplayName("Batch Propose: Null classId -> throws VALIDATION_FAILED")
    void shouldRejectNullClassId() {
      Instant start = fixedNow.plus(2, ChronoUnit.HOURS);
      List<ProposeItemCommand> items = List.of(
          new ProposeItemCommand(start, start.plus(60, ChronoUnit.MINUTES), SessionMode.ONLINE, null, null, null)
      );
      assertThrows(InvalidOperationException.class, () ->
          batchProposeService.proposeBatch(null, tutorId, studentId, items, tutorUser)
      );
    }

    @Test
    @DisplayName("Batch Respond: Null or empty sessionIds list -> throws VALIDATION_FAILED")
    void shouldRejectEmptyOrNullSessionIdsList() {
      assertThrows(InvalidOperationException.class, () ->
          batchRespondService.respondBatch(null, true, null, studentUser)
      );
      assertThrows(InvalidOperationException.class, () ->
          batchRespondService.respondBatch(Collections.emptyList(), true, null, studentUser)
      );
    }
  }
}

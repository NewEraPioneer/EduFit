package vn.edufit.scheduling.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.scheduling.api.event.SessionCancelledEvent;
import vn.edufit.scheduling.api.event.SessionScheduledEvent;
import vn.edufit.scheduling.application.port.ConnectionValidationPort;
import vn.edufit.scheduling.application.port.ConnectionValidationPort.ActiveClassInfo;
import vn.edufit.scheduling.application.service.RescheduleClassScheduleService;
import vn.edufit.scheduling.application.service.SchedulingSecurityResolver;
import vn.edufit.scheduling.application.service.SimpleCurrentUser;
import vn.edufit.scheduling.domain.model.CancelReason;
import vn.edufit.scheduling.domain.model.ProposalStatus;
import vn.edufit.scheduling.domain.model.RecurringRescheduleProposal;
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
 * Empirical Adversarial Stress Test Suite: UC4.8 Recurring Reschedule
 * Focus areas:
 * 1. Bounded Horizon boundaries (<28d reject, 28d accept, 56d accept, >56d reject).
 * 2. Mutual consent (proposer cannot accept own proposal -> 403 ForbiddenOperationException).
 * 3. Automatic cancellation and regeneration (old SCHEDULED sessions cancelled, new sessions scheduled).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Adversarial QA Suite: UC4.8 Bounded Horizon & Mutual Consent Stress")
class UC48BoundedHorizonAndMutualConsentAdversarialTest {

  private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  @Mock
  private TutoringSessionDomainRepository sessionRepository;

  @Mock
  private SessionHistoryDomainRepository historyRepository;

  @Mock
  private ConnectionValidationPort connectionValidationPort;

  @Mock
  private ProfileFacade profileFacade;

  @Mock
  private ConnectionFacade connectionFacade;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private SchedulingSecurityResolver securityResolver;
  private RescheduleClassScheduleService service;

  private final UUID classId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID studentUserId = UUID.randomUUID();
  private final UUID parentUserId = UUID.randomUUID();
  private final UUID strangerUserId = UUID.randomUUID();

  // Fixed now: 2026-11-01T00:00:00Z (Sunday, 07:00 AM VN)
  private final Instant fixedNow = Instant.parse("2026-11-01T00:00:00Z");
  private final LocalDate today = LocalDate.ofInstant(fixedNow, VIETNAM_ZONE); // 2026-11-01

  private CurrentUser tutorActor;
  private CurrentUser studentActor;
  private CurrentUser parentActor;
  private CurrentUser strangerActor;

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(fixedNow, VIETNAM_ZONE);
    securityResolver = new SchedulingSecurityResolver(profileFacade, connectionFacade);
    service = new RescheduleClassScheduleService(
        sessionRepository,
        historyRepository,
        connectionValidationPort,
        securityResolver,
        eventPublisher,
        fixedClock,
        new TestRecurringProposals()
    );

    tutorActor = SimpleCurrentUser.of(tutorUserId, "TUTOR");
    studentActor = SimpleCurrentUser.of(studentUserId, "STUDENT");
    parentActor = SimpleCurrentUser.of(parentUserId, "PARENT");
    strangerActor = SimpleCurrentUser.of(strangerUserId, "STUDENT");

    // Default mock setup for active class
    lenient().when(connectionValidationPort.findActiveClassById(classId))
        .thenReturn(Optional.of(new ActiveClassInfo(classId, studentId, tutorId, UUID.randomUUID())));

    // Profile mock bindings
    TutorSummaryDto tutorDto = new TutorSummaryDto(tutorId, tutorUserId, "Thầy Hoàng", null, null, null, null, false, null, null, null, null, "VERIFIED", fixedNow, null, 0);
    lenient().when(profileFacade.findTutorByUserId(tutorUserId)).thenReturn(Optional.of(tutorDto));
    lenient().when(profileFacade.findTutorByUserId(studentUserId)).thenReturn(Optional.empty());
    lenient().when(profileFacade.findTutorByUserId(parentUserId)).thenReturn(Optional.empty());
    lenient().when(profileFacade.findTutorByUserId(strangerUserId)).thenReturn(Optional.empty());

    StudentSummaryDto studentDto = new StudentSummaryDto(studentId, studentUserId, 1, null, null, true);
    lenient().when(profileFacade.findStudentByUserId(studentUserId)).thenReturn(Optional.of(studentDto));
    lenient().when(profileFacade.findStudentByUserId(tutorUserId)).thenReturn(Optional.empty());
    lenient().when(profileFacade.findStudentByUserId(parentUserId)).thenReturn(Optional.empty());
    lenient().when(profileFacade.findStudentByUserId(strangerUserId)).thenReturn(Optional.empty());

    lenient().when(connectionFacade.hasConfirmedParentLink(any(), any())).thenReturn(false);
    lenient().when(connectionFacade.hasConfirmedParentLink(parentUserId, studentId)).thenReturn(true);
  }

  // =========================================================================
  // 1. BOUNDED HORIZON BOUNDARY STRESS TESTS
  // =========================================================================
  @Nested
  @DisplayName("1. Bounded Horizon Boundaries Stress")
  class BoundedHorizonBoundaryStress {

    @Test
    @DisplayName("Boundary: Window = 27 days (27 < 28) -> must REJECT with VALIDATION_FAILED")
    void shouldRejectWindowOf27Days() {
      LocalDate effectiveFrom = today.plusDays(1); // 2026-11-02
      LocalDate effectiveTo = effectiveFrom.plusDays(27); // 2026-11-29 (27 days between)

      InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
          service.proposeRecurringSchedule(
              classId, effectiveFrom, effectiveTo,
              List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE, null, "Đổi lịch", tutorActor
          )
      );

      assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
      assertTrue(ex.getMessage().contains("28 đến 56 ngày"));
      assertTrue(ex.getMessage().contains("27 ngày"));
    }

    @Test
    @DisplayName("Boundary: Window = 28 days (lower exact bound) -> must ACCEPT and return PENDING proposal")
    void shouldAcceptWindowOfExactly28Days() {
      LocalDate effectiveFrom = today.plusDays(1); // 2026-11-02
      LocalDate effectiveTo = effectiveFrom.plusDays(28); // 2026-11-30 (28 days between)

      RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
          classId, effectiveFrom, effectiveTo,
          List.of("MONDAY 18:00-20:00", "WEDNESDAY 18:00-20:00"),
          SessionMode.ONLINE, "Google Meet", "Đề xuất hợp lệ 28 ngày", tutorActor
      );

      assertNotNull(proposal);
      assertEquals(ProposalStatus.PENDING, proposal.getStatus());
      assertEquals(effectiveFrom, proposal.getEffectiveFrom());
      assertEquals(effectiveTo, proposal.getEffectiveTo());
      assertEquals(tutorUserId, proposal.getProposedBy());
    }

    @Test
    @DisplayName("Boundary: Window = 56 days (upper exact bound) -> must ACCEPT and return PENDING proposal")
    void shouldAcceptWindowOfExactly56Days() {
      LocalDate effectiveFrom = today.plusDays(1); // 2026-11-02
      LocalDate effectiveTo = effectiveFrom.plusDays(56); // 56 days between (8 weeks)

      RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
          classId, effectiveFrom, effectiveTo,
          List.of("TUESDAY 19:00-21:00"),
          SessionMode.ONLINE, null, "Đề xuất 8 tuần", tutorActor
      );

      assertNotNull(proposal);
      assertEquals(ProposalStatus.PENDING, proposal.getStatus());
      assertEquals(effectiveFrom, proposal.getEffectiveFrom());
      assertEquals(effectiveTo, proposal.getEffectiveTo());
    }

    @Test
    @DisplayName("Boundary: Window = 57 days (57 > 56) -> must REJECT with VALIDATION_FAILED")
    void shouldRejectWindowOf57Days() {
      LocalDate effectiveFrom = today.plusDays(1); // 2026-11-02
      LocalDate effectiveTo = effectiveFrom.plusDays(57); // 57 days between

      InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
          service.proposeRecurringSchedule(
              classId, effectiveFrom, effectiveTo,
              List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE, null, "Đổi lịch", tutorActor
          )
      );

      assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
      assertTrue(ex.getMessage().contains("28 đến 56 ngày"));
      assertTrue(ex.getMessage().contains("57 ngày"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-5, 0, 1, 14, 20, 26, 27})
    @DisplayName("Adversarial: Various windows < 28 days -> ALL must be REJECTED")
    void shouldRejectAllSub28DayWindows(int days) {
      LocalDate effectiveFrom = today.plusDays(1);
      LocalDate effectiveTo = effectiveFrom.plusDays(days);

      assertThrows(InvalidOperationException.class, () ->
          service.proposeRecurringSchedule(
              classId, effectiveFrom, effectiveTo,
              List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE, null, "Test", tutorActor
          )
      );
    }

    @ParameterizedTest
    @ValueSource(ints = {57, 58, 65, 84, 180, 365})
    @DisplayName("Adversarial: Various windows > 56 days -> ALL must be REJECTED")
    void shouldRejectAllSuper56DayWindows(int days) {
      LocalDate effectiveFrom = today.plusDays(1);
      LocalDate effectiveTo = effectiveFrom.plusDays(days);

      assertThrows(InvalidOperationException.class, () ->
          service.proposeRecurringSchedule(
              classId, effectiveFrom, effectiveTo,
              List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE, null, "Test", tutorActor
          )
      );
    }

    @Test
    @DisplayName("Edge Case: effectiveFrom is in past -> must REJECT with VALIDATION_FAILED")
    void shouldRejectPastEffectiveFrom() {
      LocalDate pastDate = today.minusDays(1);
      LocalDate effectiveTo = pastDate.plusDays(30);

      InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
          service.proposeRecurringSchedule(
              classId, pastDate, effectiveTo,
              List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE, null, "Test", tutorActor
          )
      );

      assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
      assertTrue(ex.getMessage().contains("không được ở trong quá khứ"));
    }
  }

  // =========================================================================
  // 2. MUTUAL CONSENT ENFORCEMENT STRESS TESTS
  // =========================================================================
  @Nested
  @DisplayName("2. Mutual Consent Stress")
  class MutualConsentStress {

    @Test
    @DisplayName("Mutual Consent: Tutor proposer attempting to ACCEPT their own proposal -> must throw ForbiddenOperationException (403)")
    void tutorProposerAttemptingToAcceptOwnProposalMustBeForbidden() {
      LocalDate effectiveFrom = today.plusDays(1);
      LocalDate effectiveTo = effectiveFrom.plusDays(28);

      RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
          classId, effectiveFrom, effectiveTo,
          List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE, null, "Tutor proposal", tutorActor
      );

      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          service.respondRecurringSchedule(proposal.getProposalId(), true, null, tutorActor)
      );

      assertTrue(ex.getMessage().contains("Bạn không thể tự phản hồi đề xuất do chính mình tạo ra"));
      // Verify proposal remains PENDING
      assertEquals(ProposalStatus.PENDING, proposal.getStatus());
      verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Mutual Consent: Tutor proposer attempting to REJECT their own proposal -> must throw ForbiddenOperationException (403)")
    void tutorProposerAttemptingToRejectOwnProposalMustBeForbidden() {
      LocalDate effectiveFrom = today.plusDays(1);
      LocalDate effectiveTo = effectiveFrom.plusDays(28);

      RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
          classId, effectiveFrom, effectiveTo,
          List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE, null, "Tutor proposal", tutorActor
      );

      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          service.respondRecurringSchedule(proposal.getProposalId(), false, "Tutor cancels self", tutorActor)
      );

      assertTrue(ex.getMessage().contains("Bạn không thể tự phản hồi đề xuất do chính mình tạo ra"));
      assertEquals(ProposalStatus.PENDING, proposal.getStatus());
    }

    @Test
    @DisplayName("Mutual Consent: Student proposer attempting to ACCEPT their own proposal -> must throw ForbiddenOperationException (403)")
    void studentProposerAttemptingToAcceptOwnProposalMustBeForbidden() {
      LocalDate effectiveFrom = today.plusDays(1);
      LocalDate effectiveTo = effectiveFrom.plusDays(28);

      RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
          classId, effectiveFrom, effectiveTo,
          List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE, null, "Student proposal", studentActor
      );

      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          service.respondRecurringSchedule(proposal.getProposalId(), true, null, studentActor)
      );

      assertTrue(ex.getMessage().contains("Bạn không thể tự phản hồi đề xuất do chính mình tạo ra"));
      assertEquals(ProposalStatus.PENDING, proposal.getStatus());
    }

    @Test
    @DisplayName("Mutual Consent: Unrelated stranger attempting to respond -> must throw ForbiddenOperationException (403)")
    void strangerAttemptingToRespondMustBeForbidden() {
      LocalDate effectiveFrom = today.plusDays(1);
      LocalDate effectiveTo = effectiveFrom.plusDays(28);

      RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
          classId, effectiveFrom, effectiveTo,
          List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE, null, "Proposal", tutorActor
      );

      when(profileFacade.findTutorByUserId(strangerUserId)).thenReturn(Optional.empty());
      when(profileFacade.findStudentByUserId(strangerUserId)).thenReturn(Optional.empty());
      when(connectionFacade.hasConfirmedParentLink(strangerUserId, studentId)).thenReturn(false);

      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          service.respondRecurringSchedule(proposal.getProposalId(), true, null, strangerActor)
      );

      assertTrue(ex.getMessage().contains("Bạn không phải thành viên của buổi học này"));
    }

    @Test
    @DisplayName("Mutual Consent: Counterparty (Student) successfully REJECTS -> proposal marked REJECTED, 0 sessions changed")
    void counterpartyStudentRejectsProposalSuccessfully() {
      LocalDate effectiveFrom = today.plusDays(1);
      LocalDate effectiveTo = effectiveFrom.plusDays(28);

      RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
          classId, effectiveFrom, effectiveTo,
          List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE, null, "Proposal", tutorActor
      );

      var result = service.respondRecurringSchedule(
          proposal.getProposalId(), false, "Thời gian không phù hợp", studentActor
      );

      assertEquals(ProposalStatus.REJECTED, result.proposal().getStatus());
      assertEquals(0, result.cancelledSessionCount());
      assertTrue(result.generatedSessions().isEmpty());
      verify(sessionRepository, never()).save(any());
      verify(eventPublisher, never()).publishEvent(any(SessionCancelledEvent.class));
      verify(eventPublisher, never()).publishEvent(any(SessionScheduledEvent.class));
    }

    @Test
    @DisplayName("Mutual Consent: Authorized Parent counterparty successfully ACCEPTS -> succeeds via confirmed parent link")
    void authorizedParentCounterpartyAcceptsProposalSuccessfully() {
      LocalDate effectiveFrom = today.plusDays(1);
      LocalDate effectiveTo = effectiveFrom.plusDays(28);

      RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
          classId, effectiveFrom, effectiveTo,
          List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE, null, "Tutor proposal", tutorActor
      );

      when(profileFacade.findTutorByUserId(parentUserId)).thenReturn(Optional.empty());
      when(profileFacade.findStudentByUserId(parentUserId)).thenReturn(Optional.empty());
      when(connectionFacade.hasConfirmedParentLink(parentUserId, studentId)).thenReturn(true);
      when(sessionRepository.findScheduledSessionsByClass(eq(classId), any(), any()))
          .thenReturn(Collections.emptyList());
      when(sessionRepository.findTutorBusySlots(any(), any(), any())).thenReturn(Collections.emptyList());
      when(sessionRepository.findCalendarSessions(any(), any(), any())).thenReturn(Collections.emptyList());
      when(sessionRepository.save(any(TutoringSession.class))).thenAnswer(inv -> inv.getArgument(0));

      var result = service.respondRecurringSchedule(proposal.getProposalId(), true, null, parentActor);

      assertEquals(ProposalStatus.ACCEPTED, result.proposal().getStatus());
      assertFalse(result.generatedSessions().isEmpty());
    }
  }

  // =========================================================================
  // 3. AUTOMATIC CANCELLATION AND REGENERATION STRESS TESTS
  // =========================================================================
  @Nested
  @DisplayName("3. Automatic Cancellation & Regeneration Stress")
  class AutomaticCancellationAndRegenerationStress {

    @Test
    @DisplayName("Verify old SCHEDULED sessions in horizon are CANCELLED and new sessions are SCHEDULED")
    void shouldCancelOldScheduledSessionsAndRegenerateNewScheduledSessions() {
      // 2026-11-01 (Sunday) is today.
      // Window: 2026-11-02 (Monday) to 2026-11-30 (Monday) = 28 days
      LocalDate effectiveFrom = today.plusDays(1); // Nov 2
      LocalDate effectiveTo = effectiveFrom.plusDays(28); // Nov 30

      RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
          classId, effectiveFrom, effectiveTo,
          List.of("MONDAY 18:00-20:00", "THURSDAY 18:00-20:00"),
          SessionMode.ONLINE, "Zoom Room", "New schedule for 4 weeks", tutorActor
      );

      // Create 2 existing scheduled sessions within horizon that must be cancelled
      Instant oldStart1 = effectiveFrom.plusDays(3).atTime(18, 0).atZone(VIETNAM_ZONE).toInstant();
      Instant oldEnd1 = oldStart1.plusSeconds(7200);
      TutoringSession oldSession1 = TutoringSession.createScheduled(
          UUID.randomUUID(), classId, tutorId, studentId, oldStart1, oldEnd1,
          SessionMode.ONLINE, null, null, null, tutorUserId, studentUserId, fixedNow
      );

      Instant oldStart2 = effectiveFrom.plusDays(10).atTime(18, 0).atZone(VIETNAM_ZONE).toInstant();
      Instant oldEnd2 = oldStart2.plusSeconds(7200);
      TutoringSession oldSession2 = TutoringSession.createScheduled(
          UUID.randomUUID(), classId, tutorId, studentId, oldStart2, oldEnd2,
          SessionMode.ONLINE, null, null, null, tutorUserId, studentUserId, fixedNow
      );

      when(sessionRepository.findScheduledSessionsByClass(eq(classId), any(), any()))
          .thenReturn(List.of(oldSession1, oldSession2));
      when(sessionRepository.findTutorBusySlots(any(), any(), any())).thenReturn(Collections.emptyList());
      when(sessionRepository.findCalendarSessions(any(), any(), any())).thenReturn(Collections.emptyList());
      when(sessionRepository.save(any(TutoringSession.class))).thenAnswer(inv -> inv.getArgument(0));

      // Student counterparty accepts
      var result = service.respondRecurringSchedule(proposal.getProposalId(), true, null, studentActor);

      // 1. Verify old sessions status transitions to CANCELLED
      assertEquals(2, result.cancelledSessionCount());
      assertEquals(SessionStatus.CANCELLED, oldSession1.getStatus());
      assertEquals(CancelReason.SCHEDULE_CONFLICT, oldSession1.getCancelReason());
      assertEquals(studentUserId, oldSession1.getCancelledBy());

      assertEquals(SessionStatus.CANCELLED, oldSession2.getStatus());
      assertEquals(CancelReason.SCHEDULE_CONFLICT, oldSession2.getCancelReason());
      assertEquals(studentUserId, oldSession2.getCancelledBy());

      // Cả lô chỉ phát một sự kiện gộp; không có sự kiện riêng cho từng buổi
      verify(eventPublisher, never()).publishEvent(any(SessionCancelledEvent.class));
      verify(eventPublisher, never()).publishEvent(any(SessionScheduledEvent.class));
      var batch = org.mockito.ArgumentCaptor.forClass(vn.edufit.scheduling.api.event.ClassScheduleChangedEvent.class);
      verify(eventPublisher).publishEvent(batch.capture());
      assertEquals(2, batch.getValue().cancelledCount());
      assertEquals(9, batch.getValue().generatedCount());

      // 2. Verify new sessions are regenerated with SCHEDULED status
      // Nov 2 to Nov 30 has 5 Mondays and 4 Thursdays -> 9 sessions total
      assertEquals(9, result.generatedSessions().size());
      for (TutoringSession genSession : result.generatedSessions()) {
        assertEquals(SessionStatus.SCHEDULED, genSession.getStatus());
        assertEquals(classId, genSession.getClassId());
        assertEquals(tutorId, genSession.getTutorId());
        assertEquals(studentId, genSession.getStudentId());
        assertEquals(tutorUserId, genSession.getProposedBy());
        assertEquals(studentUserId, genSession.getRespondedBy());
        assertEquals(SessionMode.ONLINE, genSession.getMode());
        assertEquals("Zoom Room", genSession.getPlaceOrLink());
      }

      assertEquals(ProposalStatus.ACCEPTED, result.proposal().getStatus());
    }

    @Test
    @DisplayName("Regeneration Conflict: Conflict with Tutor's other class busy slot -> must throw SCHEDULE_OVERLAP (409)")
    void shouldThrowConflictWhenTutorHasBusySlot() {
      LocalDate effectiveFrom = today.plusDays(1);
      LocalDate effectiveTo = effectiveFrom.plusDays(28);

      RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
          classId, effectiveFrom, effectiveTo,
          List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE, null, "Test", tutorActor
      );

      when(sessionRepository.findScheduledSessionsByClass(eq(classId), any(), any()))
          .thenReturn(Collections.emptyList());

      // Mock tutor busy slot overlapping on first Monday
      Instant conflictStart = effectiveFrom.atTime(18, 30).atZone(VIETNAM_ZONE).toInstant();
      Instant conflictEnd = conflictStart.plusSeconds(3600);
      TutoringSession busySession = TutoringSession.createScheduled(
          UUID.randomUUID(), UUID.randomUUID(), tutorId, UUID.randomUUID(),
          conflictStart, conflictEnd, SessionMode.ONLINE, null, null, null,
          UUID.randomUUID(), UUID.randomUUID(), fixedNow
      );

      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(List.of(busySession));

      ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
          service.respondRecurringSchedule(proposal.getProposalId(), true, null, studentActor)
      );

      assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
      assertTrue(ex.getMessage().contains("bị trùng với lịch dạy khác của Gia sư"));
    }

    @Test
    @DisplayName("Regeneration Conflict: Conflict with Student's other class scheduled session -> must throw SCHEDULE_OVERLAP (409)")
    void shouldThrowConflictWhenStudentHasBusySession() {
      LocalDate effectiveFrom = today.plusDays(1);
      LocalDate effectiveTo = effectiveFrom.plusDays(28);

      RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
          classId, effectiveFrom, effectiveTo,
          List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE, null, "Test", tutorActor
      );

      when(sessionRepository.findScheduledSessionsByClass(eq(classId), any(), any()))
          .thenReturn(Collections.emptyList());
      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(Collections.emptyList());

      // Mock student busy session
      Instant conflictStart = effectiveFrom.atTime(17, 30).atZone(VIETNAM_ZONE).toInstant();
      Instant conflictEnd = effectiveFrom.atTime(19, 0).atZone(VIETNAM_ZONE).toInstant();
      TutoringSession busyStudentSession = TutoringSession.createScheduled(
          UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), studentId,
          conflictStart, conflictEnd, SessionMode.ONLINE, null, null, null,
          UUID.randomUUID(), UUID.randomUUID(), fixedNow
      );

      when(sessionRepository.findCalendarSessions(eq(studentId), any(), any()))
          .thenReturn(List.of(busyStudentSession));

      ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
          service.respondRecurringSchedule(proposal.getProposalId(), true, null, studentActor)
      );

      assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
      assertTrue(ex.getMessage().contains("bị trùng với lịch học khác của Học sinh"));
    }

    @Test
    @DisplayName("GiST Exclusion Guard: DataIntegrityViolationException on sessionRepository.save -> translated to SCHEDULE_OVERLAP")
    void shouldTranslateDataIntegrityViolationToScheduleOverlap() {
      LocalDate effectiveFrom = today.plusDays(1);
      LocalDate effectiveTo = effectiveFrom.plusDays(28);

      RecurringRescheduleProposal proposal = service.proposeRecurringSchedule(
          classId, effectiveFrom, effectiveTo,
          List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE, null, "Test", tutorActor
      );

      when(sessionRepository.findScheduledSessionsByClass(eq(classId), any(), any()))
          .thenReturn(Collections.emptyList());
      when(sessionRepository.findTutorBusySlots(eq(tutorId), any(), any()))
          .thenReturn(Collections.emptyList());
      when(sessionRepository.findCalendarSessions(eq(studentId), any(), any()))
          .thenReturn(Collections.emptyList());

      when(sessionRepository.save(any(TutoringSession.class)))
          .thenThrow(new DataIntegrityViolationException("exclusion_tutor_no_overlap violation"));

      ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
          service.respondRecurringSchedule(proposal.getProposalId(), true, null, studentActor)
      );

      assertEquals(ErrorCode.SCHEDULE_OVERLAP, ex.getErrorCode());
      assertTrue(ex.getMessage().contains("trùng lịch với ca dạy/học khác trong cơ sở dữ liệu"));
    }
  }
}

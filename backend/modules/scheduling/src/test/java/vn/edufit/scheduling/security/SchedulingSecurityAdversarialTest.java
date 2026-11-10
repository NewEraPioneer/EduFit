package vn.edufit.scheduling.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.scheduling.application.port.ConnectionValidationPort;
import vn.edufit.scheduling.application.port.ProgressSyncPort;
import vn.edufit.scheduling.application.service.BatchProposeSessionService;
import vn.edufit.scheduling.application.service.BatchRespondSessionService;
import vn.edufit.scheduling.application.service.CancelSessionService;
import vn.edufit.scheduling.application.service.GetCalendarService;
import vn.edufit.scheduling.application.service.GetSessionDetailService;
import vn.edufit.scheduling.application.service.ProposeSessionService;
import vn.edufit.scheduling.application.service.RecordSessionOutcomeService;
import vn.edufit.scheduling.application.service.RescheduleClassScheduleService;
import vn.edufit.scheduling.application.service.RescheduleSessionService;
import vn.edufit.scheduling.application.service.RespondToProposalService;
import vn.edufit.scheduling.application.service.SchedulingSecurityResolver;
import vn.edufit.scheduling.application.service.SimpleCurrentUser;
import vn.edufit.scheduling.domain.model.CancelReason;
import vn.edufit.scheduling.domain.model.ProposalStatus;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionRescheduleProposal;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.domain.repository.SessionRescheduleProposalDomainRepository;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.scheduling.web.SessionController;
import vn.edufit.scheduling.web.response.SessionResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.response.ApiError;
import vn.edufit.shared.response.ApiResponse;

/**
 * Empirical Adversarial Test Suite for Scheduling Identity & Access Control.
 *
 * <p>Stress-tests:
 * 1. Unauthorized session detail retrieval on GET /api/v1/scheduling/sessions/{id} (403, 404, 401).
 * 2. Parent access with confirmed link vs unconfirmed link (propose, respond, reschedule, cancel, outcome, calendar).
 * 3. Proposing and responding to sessions with mismatched actors and self-response attempts.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Adversarial QA Suite: Scheduling Security, Identity Resolution & Access Defense")
class SchedulingSecurityAdversarialTest {

  @Mock private ProfileFacade profileFacade;
  @Mock private ConnectionFacade connectionFacade;
  @Mock private TutoringSessionDomainRepository sessionRepository;
  @Mock private SessionHistoryDomainRepository historyRepository;
  @Mock private SessionRescheduleProposalDomainRepository proposalRepository;
  @Mock private ConnectionValidationPort connectionValidationPort;
  @Mock private ProgressSyncPort progressSyncPort;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final Instant fixedNow = Instant.parse("2026-10-25T10:00:00Z");
  private final Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);

  private SchedulingSecurityResolver securityResolver;
  private GetSessionDetailService getSessionDetailService;
  private ProposeSessionService proposeSessionService;
  private RespondToProposalService respondToProposalService;
  private RescheduleSessionService rescheduleSessionService;
  private CancelSessionService cancelSessionService;
  private RecordSessionOutcomeService recordSessionOutcomeService;
  private GetCalendarService getCalendarService;

  // Actor identities
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID studentUserId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();

  private final UUID confirmedParentUserId = UUID.randomUUID();
  private final UUID unconfirmedParentUserId = UUID.randomUUID();
  private final UUID otherStudentUserId = UUID.randomUUID();
  private final UUID otherStudentId = UUID.randomUUID();
  private final UUID otherTutorUserId = UUID.randomUUID();
  private final UUID otherTutorId = UUID.randomUUID();
  private final UUID strangerUserId = UUID.randomUUID();
  private final UUID adminUserId = UUID.randomUUID();

  private final UUID classId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();

  private CurrentUser tutorActor;
  private CurrentUser studentActor;
  private CurrentUser confirmedParentActor;
  private CurrentUser unconfirmedParentActor;
  private CurrentUser otherTutorActor;
  private CurrentUser otherStudentActor;
  private CurrentUser strangerActor;
  private CurrentUser adminActor;

  @BeforeEach
  void setUp() {
    securityResolver = new SchedulingSecurityResolver(profileFacade, connectionFacade);
    getSessionDetailService = new GetSessionDetailService(sessionRepository, securityResolver);
    proposeSessionService = new ProposeSessionService(
        sessionRepository, historyRepository, connectionValidationPort, securityResolver, eventPublisher, clock
    );
    respondToProposalService = new RespondToProposalService(
        sessionRepository, historyRepository, securityResolver, eventPublisher, clock
    );
    rescheduleSessionService = new RescheduleSessionService(
        sessionRepository, proposalRepository, historyRepository, securityResolver, eventPublisher, clock
    );
    cancelSessionService = new CancelSessionService(
        sessionRepository, historyRepository, securityResolver, eventPublisher, clock
    );
    recordSessionOutcomeService = new RecordSessionOutcomeService(
        sessionRepository, historyRepository, securityResolver, progressSyncPort, eventPublisher, clock,
        mock(vn.edufit.scheduling.api.SessionFundingGuard.class)
    );
    getCalendarService = new GetCalendarService(sessionRepository, securityResolver);

    tutorActor = SimpleCurrentUser.of(tutorUserId, "TUTOR");
    studentActor = SimpleCurrentUser.of(studentUserId, "STUDENT");
    confirmedParentActor = SimpleCurrentUser.of(confirmedParentUserId, "PARENT");
    unconfirmedParentActor = SimpleCurrentUser.of(unconfirmedParentUserId, "PARENT");
    otherTutorActor = SimpleCurrentUser.of(otherTutorUserId, "TUTOR");
    otherStudentActor = SimpleCurrentUser.of(otherStudentUserId, "STUDENT");
    strangerActor = SimpleCurrentUser.of(strangerUserId, "STUDENT");
    adminActor = SimpleCurrentUser.of(adminUserId, "ADMIN");

    // Standard profile mocks
    lenient().when(profileFacade.findTutorByUserId(tutorUserId))
        .thenReturn(Optional.of(createTutorDto(tutorId, tutorUserId, "Thầy Tutor Chuẩn")));
    lenient().when(profileFacade.findTutorByUserId(otherTutorUserId))
        .thenReturn(Optional.of(createTutorDto(otherTutorId, otherTutorUserId, "Thầy Khác")));
    lenient().when(profileFacade.findTutorByUserId(studentUserId)).thenReturn(Optional.empty());
    lenient().when(profileFacade.findTutorByUserId(confirmedParentUserId)).thenReturn(Optional.empty());
    lenient().when(profileFacade.findTutorByUserId(unconfirmedParentUserId)).thenReturn(Optional.empty());
    lenient().when(profileFacade.findTutorByUserId(strangerUserId)).thenReturn(Optional.empty());

    lenient().when(profileFacade.findStudentByUserId(studentUserId))
        .thenReturn(Optional.of(new StudentSummaryDto(studentId, studentUserId, 1, null, null, true)));
    lenient().when(profileFacade.findStudentByUserId(otherStudentUserId))
        .thenReturn(Optional.of(new StudentSummaryDto(otherStudentId, otherStudentUserId, 1, null, null, true)));
    lenient().when(profileFacade.findStudentByUserId(tutorUserId)).thenReturn(Optional.empty());
    lenient().when(profileFacade.findStudentByUserId(confirmedParentUserId)).thenReturn(Optional.empty());
    lenient().when(profileFacade.findStudentByUserId(unconfirmedParentUserId)).thenReturn(Optional.empty());
    lenient().when(profileFacade.findStudentByUserId(strangerUserId)).thenReturn(Optional.empty());

    // Parent link mocks
    lenient().when(connectionFacade.hasConfirmedParentLink(confirmedParentUserId, studentId)).thenReturn(true);
    lenient().when(connectionFacade.hasConfirmedParentLink(unconfirmedParentUserId, studentId)).thenReturn(false);
    lenient().when(connectionFacade.hasConfirmedParentLink(strangerUserId, studentId)).thenReturn(false);
  }

  private TutorSummaryDto createTutorDto(UUID tid, UUID uid, String name) {
    return new TutorSummaryDto(tid, uid, name, null, null, null, null, false, null, null, null, null, "VERIFIED", fixedNow, null, 0);
  }

  private TutoringSession createMockSession(UUID proposedBy, SessionStatus status) {
    return new TutoringSession(
        sessionId, classId, tutorId, studentId,
        fixedNow.plusSeconds(7200), fixedNow.plusSeconds(14400),
        SessionMode.ONLINE, "https://meet.google.com/test", null, null,
        status, proposedBy, null, null, null,
        fixedNow.plusSeconds(3600), null, null, null, null,
        false, null, null, 1, fixedNow
    );
  }

  // =========================================================================
  // 1. GET /api/v1/scheduling/sessions/{id} ADVERSARIAL CHALLENGES
  // =========================================================================

  @Nested
  @DisplayName("1. Session Detail Access Control (GET /api/v1/scheduling/sessions/{id})")
  class SessionDetailAccessTests {

    @Test
    @DisplayName("404 NOT FOUND: Truy cập buổi học không tồn tại")
    void shouldReturn404WhenSessionNotFound() {
      UUID nonExistentId = UUID.randomUUID();
      when(sessionRepository.findById(nonExistentId)).thenReturn(Optional.empty());

      assertThrows(EntityNotFoundException.class, () ->
          getSessionDetailService.getSessionDetail(nonExistentId, studentActor));
    }

    @Test
    @DisplayName("403 FORBIDDEN: Học sinh lạ cố xem chi tiết buổi học của học sinh khác")
    void shouldReturn403WhenStrangerStudentAccessesSession() {
      TutoringSession session = createMockSession(tutorUserId, SessionStatus.SCHEDULED);
      when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          getSessionDetailService.getSessionDetail(sessionId, otherStudentActor));
      assertTrue(ex.getMessage().contains("không có quyền truy cập"));
    }

    @Test
    @DisplayName("403 FORBIDDEN: Gia sư khác cố xem chi tiết buổi học không thuộc lớp mình")
    void shouldReturn403WhenOtherTutorAccessesSession() {
      TutoringSession session = createMockSession(studentUserId, SessionStatus.SCHEDULED);
      when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          getSessionDetailService.getSessionDetail(sessionId, otherTutorActor));
      assertTrue(ex.getMessage().contains("không có quyền truy cập"));
    }

    @Test
    @DisplayName("403 FORBIDDEN: Phụ huynh chưa xác nhận liên kết cố xem chi tiết buổi học")
    void shouldReturn403WhenUnconfirmedParentAccessesSession() {
      TutoringSession session = createMockSession(tutorUserId, SessionStatus.SCHEDULED);
      when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          getSessionDetailService.getSessionDetail(sessionId, unconfirmedParentActor));
      assertTrue(ex.getMessage().contains("không có quyền truy cập"));
    }

    @Test
    @DisplayName("200 OK: Phụ huynh CÓ liên kết xác nhận xem chi tiết buổi học thành công")
    void shouldAllowConfirmedParentToAccessSession() {
      TutoringSession session = createMockSession(tutorUserId, SessionStatus.SCHEDULED);
      when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

      TutoringSession result = getSessionDetailService.getSessionDetail(sessionId, confirmedParentActor);
      assertNotNull(result);
      assertEquals(sessionId, result.getSessionId());
    }

    @Test
    @DisplayName("200 OK: Học sinh chính chủ xem chi tiết thành công")
    void shouldAllowActualStudentToAccessSession() {
      TutoringSession session = createMockSession(tutorUserId, SessionStatus.SCHEDULED);
      when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

      TutoringSession result = getSessionDetailService.getSessionDetail(sessionId, studentActor);
      assertNotNull(result);
      assertEquals(sessionId, result.getSessionId());
    }

    @Test
    @DisplayName("200 OK: Gia sư phụ trách buổi học xem chi tiết thành công")
    void shouldAllowActualTutorToAccessSession() {
      TutoringSession session = createMockSession(studentUserId, SessionStatus.SCHEDULED);
      when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

      TutoringSession result = getSessionDetailService.getSessionDetail(sessionId, tutorActor);
      assertNotNull(result);
      assertEquals(sessionId, result.getSessionId());
    }

    @Test
    @DisplayName("200 OK: ADMIN xem chi tiết buổi học thành công")
    void shouldAllowAdminToAccessSession() {
      TutoringSession session = createMockSession(studentUserId, SessionStatus.SCHEDULED);
      when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

      TutoringSession result = getSessionDetailService.getSessionDetail(sessionId, adminActor);
      assertNotNull(result);
      assertEquals(sessionId, result.getSessionId());
    }

    @Test
    @DisplayName("MockMvc Integration: Kiểm thử đầy đủ HTTP Status Code 200, 403, 404, 401 qua REST Endpoint")
    void testMockMvcSessionDetailHttpStatuses() throws Exception {
      @SuppressWarnings("unchecked")
      ObjectProvider<CurrentUser> currentUserProvider = mock(ObjectProvider.class);

      SessionController controller = new SessionController(
          proposeSessionService,
          mock(BatchProposeSessionService.class),
          respondToProposalService,
          mock(BatchRespondSessionService.class),
          rescheduleSessionService,
          mock(RescheduleClassScheduleService.class),
          cancelSessionService,
          recordSessionOutcomeService,
          getCalendarService,
          getSessionDetailService,
          currentUserProvider
      );

      MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
          .setControllerAdvice(new TestSecurityExceptionHandlerAdvice())
          .build();

      TutoringSession session = createMockSession(tutorUserId, SessionStatus.SCHEDULED);

      // 1. Unauthenticated -> 401 UNAUTHORIZED
      when(currentUserProvider.getIfAvailable()).thenReturn(null);
      mockMvc.perform(get("/api/v1/scheduling/sessions/" + sessionId))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

      // 2. Session Not Found -> 404 NOT FOUND
      when(currentUserProvider.getIfAvailable()).thenReturn(studentActor);
      UUID missingId = UUID.randomUUID();
      when(sessionRepository.findById(missingId)).thenReturn(Optional.empty());
      mockMvc.perform(get("/api/v1/scheduling/sessions/" + missingId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

      // 3. Unauthorized Stranger Student -> 403 FORBIDDEN
      when(currentUserProvider.getIfAvailable()).thenReturn(otherStudentActor);
      when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
      mockMvc.perform(get("/api/v1/scheduling/sessions/" + sessionId))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));

      // 4. Unconfirmed Parent -> 403 FORBIDDEN
      when(currentUserProvider.getIfAvailable()).thenReturn(unconfirmedParentActor);
      mockMvc.perform(get("/api/v1/scheduling/sessions/" + sessionId))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));

      // 5. Confirmed Parent -> 200 OK
      when(currentUserProvider.getIfAvailable()).thenReturn(confirmedParentActor);
      mockMvc.perform(get("/api/v1/scheduling/sessions/" + sessionId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.sessionId").value(sessionId.toString()));
    }
  }

  // =========================================================================
  // 2. CONFIRMED VS UNCONFIRMED PARENT ACCESS ACROSS ALL SCHEDULING OPERATIONS
  // =========================================================================

  @Nested
  @DisplayName("2. Confirmed vs Unconfirmed Parent Access across Full Lifecycle")
  class ParentAccessMatrixTests {

    @Test
    @DisplayName("Propose: Phụ huynh CÓ liên kết xác nhận được phép đề xuất lịch học")
    void shouldAllowConfirmedParentToProposeSession() {
      when(connectionValidationPort.isConnectionActive(classId, tutorId, studentId)).thenReturn(true);
      when(sessionRepository.findTutorBusySlots(any(), any(), any())).thenReturn(List.of());
      when(sessionRepository.findCalendarSessions(any(), any(), any())).thenReturn(List.of());
      when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      TutoringSession result = proposeSessionService.proposeSession(
          classId, tutorId, studentId,
          fixedNow.plusSeconds(7200), fixedNow.plusSeconds(14400),
          SessionMode.ONLINE, null, null, "Lời nhắn từ phụ huynh",
          confirmedParentActor
      );

      assertNotNull(result);
      assertEquals(confirmedParentUserId, result.getProposedBy());
    }

    @Test
    @DisplayName("Propose: Phụ huynh CHƯA xác nhận liên kết bị từ chối với 403 Forbidden")
    void shouldRejectUnconfirmedParentProposeSession() {
      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          proposeSessionService.proposeSession(
              classId, tutorId, studentId,
              fixedNow.plusSeconds(7200), fixedNow.plusSeconds(14400),
              SessionMode.ONLINE, null, null, "Lời nhắn",
              unconfirmedParentActor
          ));
      assertTrue(ex.getMessage().contains("Chỉ Gia sư hoặc Học sinh trong lớp học"));
    }

    @Test
    @DisplayName("Respond (Gia sư đề xuất): Phụ huynh CÓ liên kết được quyền chấp nhận/từ chối")
    void shouldAllowConfirmedParentToRespondToTutorProposal() {
      TutoringSession session = createMockSession(tutorUserId, SessionStatus.PROPOSED);
      when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
      when(sessionRepository.findTutorBusySlots(any(), any(), any())).thenReturn(List.of());
      when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      TutoringSession result = respondToProposalService.respondToProposal(
          sessionId, confirmedParentActor, true, null
      );

      assertEquals(SessionStatus.SCHEDULED, result.getStatus());
      assertEquals(confirmedParentUserId, result.getRespondedBy());
    }

    @Test
    @DisplayName("Respond (Gia sư đề xuất): Phụ huynh CHƯA liên kết bị từ chối với 403 Forbidden")
    void shouldRejectUnconfirmedParentRespondingToTutorProposal() {
      TutoringSession session = createMockSession(tutorUserId, SessionStatus.PROPOSED);
      when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          respondToProposalService.respondToProposal(sessionId, unconfirmedParentActor, true, null));
      assertTrue(ex.getMessage().contains("Bạn không phải thành viên của buổi học này"));
    }

    @Test
    @DisplayName("Respond (Học sinh đề xuất): Phụ huynh KHÔNG ĐƯỢC phép tự duyệt đề xuất của phe học sinh")
    void shouldRejectParentRespondingToStudentProposal() {
      TutoringSession session = createMockSession(studentUserId, SessionStatus.PROPOSED);
      when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          respondToProposalService.respondToProposal(sessionId, confirmedParentActor, true, null));
      assertTrue(ex.getMessage().contains("Chỉ Gia sư của buổi học mới có quyền phản hồi đề xuất này"));
    }

    @Test
    @DisplayName("Reschedule Propose: Phụ huynh CÓ liên kết được tạo đề xuất đổi lịch, CHƯA liên kết bị 403")
    void shouldCheckParentPermissionOnReschedulePropose() {
      TutoringSession session = createMockSession(studentUserId, SessionStatus.SCHEDULED);
      when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
      when(proposalRepository.findPendingBySessionId(sessionId)).thenReturn(Optional.empty());
      when(sessionRepository.findTutorBusySlots(any(), any(), any())).thenReturn(List.of());
      when(proposalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // Confirmed Parent -> Success
      SessionRescheduleProposal proposal = rescheduleSessionService.proposeReschedule(
          sessionId, confirmedParentActor,
          fixedNow.plusSeconds(86400), fixedNow.plusSeconds(93600), "Dời lịch do bận"
      );
      assertNotNull(proposal);
      assertEquals(confirmedParentUserId, proposal.getProposedBy());

      // Unconfirmed Parent -> 403
      assertThrows(ForbiddenOperationException.class, () ->
          rescheduleSessionService.proposeReschedule(
              sessionId, unconfirmedParentActor,
              fixedNow.plusSeconds(86400), fixedNow.plusSeconds(93600), "Dời lịch"
          ));
    }

    @Test
    @DisplayName("Reschedule Respond: Phụ huynh CÓ liên kết được phản hồi đề xuất từ Gia sư, CHƯA liên kết bị 403")
    void shouldCheckParentPermissionOnRescheduleRespond() {
      UUID proposalId = UUID.randomUUID();
      TutoringSession session = createMockSession(tutorUserId, SessionStatus.SCHEDULED);
      SessionRescheduleProposal proposal = SessionRescheduleProposal.create(
          proposalId, sessionId, tutorUserId,
          fixedNow.plusSeconds(86400), fixedNow.plusSeconds(93600),
          "Thầy đổi lịch", fixedNow.plusSeconds(86400), fixedNow
      );

      when(proposalRepository.findById(proposalId)).thenReturn(Optional.of(proposal));
      when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
      when(sessionRepository.findTutorBusySlots(any(), any(), any())).thenReturn(List.of());
      when(proposalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // Unconfirmed parent -> 403
      assertThrows(ForbiddenOperationException.class, () ->
          rescheduleSessionService.respondToReschedule(proposalId, unconfirmedParentActor, true, null));

      // Confirmed parent -> Success
      SessionRescheduleProposal accepted = rescheduleSessionService.respondToReschedule(
          proposalId, confirmedParentActor, true, null
      );
      assertEquals(ProposalStatus.ACCEPTED, accepted.getStatus());
      assertEquals(confirmedParentUserId, accepted.getRespondedBy());
    }

    @Test
    @DisplayName("Cancel: Phụ huynh CÓ liên kết được hủy ca học, CHƯA liên kết bị 403")
    void shouldCheckParentPermissionOnCancelSession() {
      TutoringSession session = createMockSession(tutorUserId, SessionStatus.SCHEDULED);
      when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
      when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // Unconfirmed parent -> 403
      assertThrows(ForbiddenOperationException.class, () ->
          cancelSessionService.cancelSession(sessionId, unconfirmedParentActor, CancelReason.PERSONAL_REASON, "Ốm"));

      // Confirmed parent -> Success
      TutoringSession cancelled = cancelSessionService.cancelSession(
          sessionId, confirmedParentActor, CancelReason.PERSONAL_REASON, "Con bị ốm"
      );
      assertEquals(SessionStatus.CANCELLED, cancelled.getStatus());
      assertEquals(confirmedParentUserId, cancelled.getCancelledBy());
    }

    @Test
    @DisplayName("Outcome: Phụ huynh KHÔNG BAO GIỜ được ghi nhận kết quả (Chỉ Gia sư)")
    void shouldRejectParentRecordingOutcomeEvenIfConfirmed() {
      TutoringSession session = createMockSession(tutorUserId, SessionStatus.SCHEDULED);
      when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          recordSessionOutcomeService.recordOutcome(sessionId, confirmedParentActor, SessionStatus.COMPLETED, "Xong"));
      assertTrue(ex.getMessage().contains("Chỉ Gia sư của buổi học mới có quyền ghi nhận kết quả"));
    }

    @Test
    @DisplayName("Calendar: Phụ huynh CÓ liên kết tra cứu được, CHƯA liên kết bị 403, không truyền studentId bị 400")
    void shouldCheckParentPermissionOnCalendarQuery() {
      when(sessionRepository.findCalendarSessions(eq(studentId), any(), any())).thenReturn(List.of());

      // 1. Phụ huynh không truyền studentId -> InvalidOperationException (400)
      InvalidOperationException badReqEx = assertThrows(InvalidOperationException.class, () ->
          getCalendarService.getCalendarSessions(confirmedParentActor, fixedNow, fixedNow.plusSeconds(86400), null));
      assertEquals(ErrorCode.VALIDATION_FAILED, badReqEx.getErrorCode());

      // 2. Phụ huynh chưa xác nhận liên kết -> ForbiddenOperationException (403)
      assertThrows(ForbiddenOperationException.class, () ->
          getCalendarService.getCalendarSessions(unconfirmedParentActor, fixedNow, fixedNow.plusSeconds(86400), studentId));

      // 3. Phụ huynh có liên kết xác nhận -> Thành công
      assertDoesNotThrow(() ->
          getCalendarService.getCalendarSessions(confirmedParentActor, fixedNow, fixedNow.plusSeconds(86400), studentId));
    }
  }

  // =========================================================================
  // 3. MISMATCHED ACTORS & SELF-RESPONSE ADVERSARIAL CHALLENGES
  // =========================================================================

  @Nested
  @DisplayName("3. Proposing & Responding with Mismatched Actors")
  class MismatchedActorTests {

    @Test
    @DisplayName("Propose Mismatch: Gia sư A đăng nhập nhưng khai khống tutorId là của Gia sư B")
    void shouldRejectTutorProposingWithOtherTutorId() {
      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          proposeSessionService.proposeSession(
              classId, otherTutorId, studentId,
              fixedNow.plusSeconds(7200), fixedNow.plusSeconds(14400),
              SessionMode.ONLINE, null, null, "Thử mạo danh gia sư",
              tutorActor
          ));
      assertTrue(ex.getMessage().contains("Chỉ Gia sư hoặc Học sinh trong lớp học mới có quyền đề xuất lịch học"));
    }

    @Test
    @DisplayName("Propose Mismatch: Học sinh A đăng nhập nhưng khai khống studentId là của Học sinh B")
    void shouldRejectStudentProposingWithOtherStudentId() {
      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          proposeSessionService.proposeSession(
              classId, tutorId, otherStudentId,
              fixedNow.plusSeconds(7200), fixedNow.plusSeconds(14400),
              SessionMode.ONLINE, null, null, "Thử mạo danh học sinh",
              studentActor
          ));
      assertTrue(ex.getMessage().contains("Chỉ Gia sư hoặc Học sinh trong lớp học mới có quyền đề xuất lịch học"));
    }

    @Test
    @DisplayName("Propose Mismatch: Người hoàn toàn xa lạ cố tạo đề xuất cho lớp học của người khác")
    void shouldRejectStrangerProposingSession() {
      assertThrows(ForbiddenOperationException.class, () ->
          proposeSessionService.proposeSession(
              classId, tutorId, studentId,
              fixedNow.plusSeconds(7200), fixedNow.plusSeconds(14400),
              SessionMode.ONLINE, null, null, "Xâm nhập",
              strangerActor
          ));
    }

    @Test
    @DisplayName("Self-Response Defense: Gia sư tạo đề xuất -> Gia sư KHÔNG THỂ tự bấm chấp nhận/từ chối")
    void shouldPreventTutorFromSelfRespondingToOwnProposal() {
      TutoringSession session = createMockSession(tutorUserId, SessionStatus.PROPOSED);
      when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          respondToProposalService.respondToProposal(sessionId, tutorActor, true, null));
      assertTrue(ex.getMessage().contains("Bạn không thể tự phản hồi đề xuất do chính mình tạo ra"));
    }

    @Test
    @DisplayName("Self-Response Defense: Học sinh tạo đề xuất -> Học sinh KHÔNG THỂ tự bấm chấp nhận/từ chối")
    void shouldPreventStudentFromSelfRespondingToOwnProposal() {
      TutoringSession session = createMockSession(studentUserId, SessionStatus.PROPOSED);
      when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          respondToProposalService.respondToProposal(sessionId, studentActor, true, null));
      assertTrue(ex.getMessage().contains("Bạn không thể tự phản hồi đề xuất do chính mình tạo ra"));
    }

    @Test
    @DisplayName("Self-Response Defense: Phụ huynh tạo đề xuất -> Phụ huynh KHÔNG THỂ tự bấm chấp thuận")
    void shouldPreventParentFromSelfRespondingToOwnProposal() {
      TutoringSession session = createMockSession(confirmedParentUserId, SessionStatus.PROPOSED);
      when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          respondToProposalService.respondToProposal(sessionId, confirmedParentActor, true, null));
      assertTrue(ex.getMessage().contains("Bạn không thể tự phản hồi đề xuất do chính mình tạo ra"));
    }

    @Test
    @DisplayName("Counterparty Defense: Gia sư khác (Gia sư B) cố phản hồi đề xuất của Học sinh gửi cho Gia sư A")
    void shouldPreventOtherTutorFromRespondingToSession() {
      TutoringSession session = createMockSession(studentUserId, SessionStatus.PROPOSED);
      when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          respondToProposalService.respondToProposal(sessionId, otherTutorActor, true, null));
      assertTrue(ex.getMessage().contains("Bạn không phải thành viên của buổi học này"));
    }

    @Test
    @DisplayName("Counterparty Defense: Học sinh khác (Học sinh B) cố phản hồi đề xuất của Gia sư gửi cho Học sinh A")
    void shouldPreventOtherStudentFromRespondingToSession() {
      TutoringSession session = createMockSession(tutorUserId, SessionStatus.PROPOSED);
      when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          respondToProposalService.respondToProposal(sessionId, otherStudentActor, true, null));
      assertTrue(ex.getMessage().contains("Bạn không phải thành viên của buổi học này"));
    }

    @Test
    @DisplayName("Reschedule Self-Response: Người tạo đề xuất đổi lịch không được tự phản hồi")
    void shouldPreventSelfResponseOnRescheduleProposal() {
      UUID proposalId = UUID.randomUUID();
      TutoringSession session = createMockSession(studentUserId, SessionStatus.SCHEDULED);
      SessionRescheduleProposal proposal = SessionRescheduleProposal.create(
          proposalId, sessionId, tutorUserId,
          fixedNow.plusSeconds(86400), fixedNow.plusSeconds(93600),
          "Đổi giờ", fixedNow.plusSeconds(86400), fixedNow
      );

      when(proposalRepository.findById(proposalId)).thenReturn(Optional.of(proposal));
      when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

      // Tutor who created proposal tries to respond
      ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
          rescheduleSessionService.respondToReschedule(proposalId, tutorActor, true, null));
      assertTrue(ex.getMessage().contains("Bạn không thể tự phản hồi đề xuất do chính mình tạo ra"));
    }

    @Test
    @DisplayName("Outcome Defense: Học sinh hoặc Gia sư khác cố ghi nhận kết quả buổi học")
    void shouldRejectStudentOrOtherTutorRecordingOutcome() {
      TutoringSession session = createMockSession(tutorUserId, SessionStatus.SCHEDULED);
      when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

      // Student tries -> 403
      assertThrows(ForbiddenOperationException.class, () ->
          recordSessionOutcomeService.recordOutcome(sessionId, studentActor, SessionStatus.COMPLETED, null));

      // Other tutor tries -> 403
      assertThrows(ForbiddenOperationException.class, () ->
          recordSessionOutcomeService.recordOutcome(sessionId, otherTutorActor, SessionStatus.COMPLETED, null));
    }

    @Test
    @DisplayName("Cancel Defense: Học sinh lạ hoặc Gia sư lạ cố hủy buổi học")
    void shouldRejectStrangerCancellingSession() {
      TutoringSession session = createMockSession(tutorUserId, SessionStatus.SCHEDULED);
      when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

      // Stranger student
      assertThrows(ForbiddenOperationException.class, () ->
          cancelSessionService.cancelSession(sessionId, otherStudentActor, CancelReason.PERSONAL_REASON, ""));

      // Stranger tutor
      assertThrows(ForbiddenOperationException.class, () ->
          cancelSessionService.cancelSession(sessionId, otherTutorActor, CancelReason.SCHEDULE_CONFLICT, ""));
    }
  }

  // =========================================================================
  // Test Controller Advice simulating GlobalExceptionHandler for MockMvc
  // =========================================================================

  @RestControllerAdvice
  static class TestSecurityExceptionHandlerAdvice {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EntityNotFoundException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ApiError.of(ErrorCode.RESOURCE_NOT_FOUND.name(), ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenOperationException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(ApiError.of(ErrorCode.FORBIDDEN.name(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<ApiError> handleInvalidOperation(InvalidOperationException ex) {
      HttpStatus status = ex.getErrorCode() == ErrorCode.UNAUTHORIZED
          ? HttpStatus.UNAUTHORIZED
          : HttpStatus.BAD_REQUEST;
      return ResponseEntity.status(status)
          .body(ApiError.of(ex.getErrorCode() != null ? ex.getErrorCode().name() : "BAD_REQUEST", ex.getMessage()));
    }
  }
}

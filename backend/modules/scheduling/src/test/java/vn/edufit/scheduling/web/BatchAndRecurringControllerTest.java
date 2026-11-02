package vn.edufit.scheduling.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import vn.edufit.scheduling.application.service.SimpleCurrentUser;
import vn.edufit.scheduling.domain.model.RecurringRescheduleProposal;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.web.request.BatchProposeSessionRequest;
import vn.edufit.scheduling.web.request.BatchProposeSessionRequest.BatchProposeItemRequest;
import vn.edufit.scheduling.web.request.BatchRespondSessionRequest;
import vn.edufit.scheduling.web.request.ProposeRecurringScheduleRequest;
import vn.edufit.scheduling.web.request.RespondRecurringScheduleRequest;
import vn.edufit.scheduling.web.response.RecurringProposalResponse;
import vn.edufit.scheduling.web.response.RecurringScheduleResultResponse;
import vn.edufit.scheduling.web.response.SessionResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.response.ApiResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Controller: Batch & UC4.8 Recurring Endpoints")
class BatchAndRecurringControllerTest {

  @Mock private ProposeSessionService proposeSessionService;
  @Mock private BatchProposeSessionService batchProposeSessionService;
  @Mock private RespondToProposalService respondToProposalService;
  @Mock private BatchRespondSessionService batchRespondSessionService;
  @Mock private RescheduleSessionService rescheduleSessionService;
  @Mock private RescheduleClassScheduleService rescheduleClassScheduleService;
  @Mock private CancelSessionService cancelSessionService;
  @Mock private RecordSessionOutcomeService recordSessionOutcomeService;
  @Mock private GetCalendarService getCalendarService;
  @Mock private GetSessionDetailService getSessionDetailService;
  @Mock private ObjectProvider<CurrentUser> currentUserProvider;

  private SessionController controller;

  private final UUID currentUserId = UUID.randomUUID();
  private final UUID classId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-11-01T08:00:00Z");

  private CurrentUser testUser;

  @BeforeEach
  void setUp() {
    controller = new SessionController(
        proposeSessionService,
        batchProposeSessionService,
        respondToProposalService,
        batchRespondSessionService,
        rescheduleSessionService,
        rescheduleClassScheduleService,
        cancelSessionService,
        recordSessionOutcomeService,
        getCalendarService,
        getSessionDetailService,
        currentUserProvider
    );

    testUser = SimpleCurrentUser.of(currentUserId, "TUTOR");
  }

  @Test
  @DisplayName("POST /sessions/propose-batch -> HTTP 201 CREATED")
  void shouldProposeBatch() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    TutoringSession session = TutoringSession.createProposed(
        UUID.randomUUID(), classId, tutorId, studentId,
        now.plusSeconds(3600), now.plusSeconds(7200),
        SessionMode.ONLINE, null, null, null, currentUserId,
        now.plusSeconds(86400), now
    );
    when(batchProposeSessionService.proposeBatch(any(), any(), any(), any(), any())).thenReturn(List.of(session));

    BatchProposeSessionRequest req = new BatchProposeSessionRequest(
        classId,
        tutorId,
        studentId,
        List.of(new BatchProposeItemRequest(now.plusSeconds(3600), now.plusSeconds(7200), SessionMode.ONLINE, null, null, null))
    );

    ResponseEntity<ApiResponse<List<SessionResponse>>> response = controller.proposeBatch(req);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().data().size());
  }

  @Test
  @DisplayName("POST /sessions/respond-batch -> HTTP 200 OK")
  void shouldRespondBatch() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    TutoringSession session = TutoringSession.createScheduled(
        UUID.randomUUID(), classId, tutorId, studentId,
        now.plusSeconds(3600), now.plusSeconds(7200),
        SessionMode.ONLINE, null, null, null, UUID.randomUUID(), currentUserId, now
    );
    when(batchRespondSessionService.respondBatch(any(), anyBoolean(), any(), any())).thenReturn(List.of(session));

    BatchRespondSessionRequest req = new BatchRespondSessionRequest(
        List.of(session.getSessionId()),
        true,
        null
    );

    ResponseEntity<ApiResponse<List<SessionResponse>>> response = controller.respondBatch(req);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().data().size());
  }

  @Test
  @DisplayName("POST /recurring/propose -> HTTP 201 CREATED")
  void shouldProposeRecurringSchedule() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    LocalDate from = LocalDate.of(2026, 11, 2);
    LocalDate to = from.plusDays(28);

    RecurringRescheduleProposal proposal = RecurringRescheduleProposal.create(
        UUID.randomUUID(), classId, tutorId, studentId, currentUserId,
        from, to, List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE,
        null, "Đổi lịch", now.plusSeconds(86400), now
    );
    when(rescheduleClassScheduleService.proposeRecurringSchedule(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(proposal);

    ProposeRecurringScheduleRequest req = new ProposeRecurringScheduleRequest(
        classId, from, to, List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE, null, "Đổi lịch"
    );

    ResponseEntity<ApiResponse<RecurringProposalResponse>> response = controller.proposeRecurringSchedule(req);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(proposal.getProposalId(), response.getBody().data().proposalId());
  }

  @Test
  @DisplayName("POST /recurring/respond -> HTTP 200 OK")
  void shouldRespondRecurringSchedule() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    UUID proposalId = UUID.randomUUID();
    RecurringRescheduleProposal proposal = RecurringRescheduleProposal.create(
        proposalId, classId, tutorId, studentId, UUID.randomUUID(),
        LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 30),
        List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE,
        null, null, now.plusSeconds(86400), now
    );
    proposal.accept(currentUserId, now);
    RescheduleClassScheduleService.RecurringScheduleResult result =
        new RescheduleClassScheduleService.RecurringScheduleResult(proposal, 2, Collections.emptyList());
    when(rescheduleClassScheduleService.respondRecurringSchedule(any(), anyBoolean(), any(), any())).thenReturn(result);

    RespondRecurringScheduleRequest req = new RespondRecurringScheduleRequest(proposalId, true, null);

    ResponseEntity<ApiResponse<RecurringScheduleResultResponse>> response = controller.respondRecurringSchedule(req);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(2, response.getBody().data().cancelledSessionCount());
  }

  @Test
  @DisplayName("GET /recurring/proposals/{proposalId} -> HTTP 200 OK")
  void shouldGetRecurringProposal() {
    UUID proposalId = UUID.randomUUID();
    LocalDate from = LocalDate.of(2026, 11, 2);
    LocalDate to = from.plusDays(28);

    RecurringRescheduleProposal proposal = RecurringRescheduleProposal.create(
        proposalId, classId, tutorId, studentId, currentUserId,
        from, to, List.of("MONDAY 18:00-20:00"), SessionMode.ONLINE,
        null, "Đổi lịch", now.plusSeconds(86400), now
    );
    when(rescheduleClassScheduleService.findProposalById(proposalId)).thenReturn(Optional.of(proposal));

    ResponseEntity<ApiResponse<RecurringProposalResponse>> response = controller.getRecurringProposal(proposalId);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(proposalId, response.getBody().data().proposalId());
  }
}

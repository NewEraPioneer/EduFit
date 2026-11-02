package vn.edufit.scheduling.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
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
import vn.edufit.scheduling.domain.model.CancelReason;
import vn.edufit.scheduling.domain.model.ProposalStatus;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionRescheduleProposal;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.web.request.CancelSessionRequest;
import vn.edufit.scheduling.web.request.ProposeSessionRequest;
import vn.edufit.scheduling.web.request.RecordOutcomeRequest;
import vn.edufit.scheduling.web.request.RescheduleSessionRequest;
import vn.edufit.scheduling.web.request.RespondProposalRequest;
import vn.edufit.scheduling.web.request.RespondRescheduleRequest;
import vn.edufit.scheduling.web.response.CalendarItemResponse;
import vn.edufit.scheduling.web.response.RescheduleProposalResponse;
import vn.edufit.scheduling.web.response.SessionResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.response.ApiResponse;

/**
 * Kiểm thử đơn vị cho {@link SessionController}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Controller: SessionController REST Endpoints")
class SessionControllerTest {

  @Mock
  private ProposeSessionService proposeSessionService;

  @Mock
  private BatchProposeSessionService batchProposeSessionService;

  @Mock
  private RespondToProposalService respondToProposalService;

  @Mock
  private BatchRespondSessionService batchRespondSessionService;

  @Mock
  private RescheduleSessionService rescheduleSessionService;

  @Mock
  private RescheduleClassScheduleService rescheduleClassScheduleService;

  @Mock
  private CancelSessionService cancelSessionService;

  @Mock
  private RecordSessionOutcomeService recordSessionOutcomeService;

  @Mock
  private GetCalendarService getCalendarService;

  @Mock
  private GetSessionDetailService getSessionDetailService;

  @Mock
  private ObjectProvider<CurrentUser> currentUserProvider;

  private SessionController controller;

  private final UUID currentUserId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private final UUID classId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final Instant fixedNow = Instant.parse("2026-10-25T08:00:00Z");

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

    testUser = SimpleCurrentUser.of(currentUserId, "STUDENT", "TUTOR");
  }

  @Test
  @DisplayName("POST /sessions/propose -> HTTP 201 CREATED khi đề xuất thành công")
  void shouldProposeSessionSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    Instant startAt = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);
    ProposeSessionRequest req = new ProposeSessionRequest(
        classId, tutorId, studentId, startAt, endAt,
        SessionMode.ONLINE, "meet.google.com/abc", "Note", "Xin chào"
    );

    TutoringSession proposedSession = TutoringSession.createProposed(
        sessionId, classId, tutorId, studentId, startAt, endAt,
        SessionMode.ONLINE, "meet.google.com/abc", "Note", "Xin chào",
        currentUserId, startAt, fixedNow
    );

    when(proposeSessionService.proposeSession(
        eq(classId), eq(tutorId), eq(studentId), eq(startAt), eq(endAt),
        eq(SessionMode.ONLINE), eq("meet.google.com/abc"), eq("Note"), eq("Xin chào"),
        eq(testUser)
    )).thenReturn(proposedSession);

    ResponseEntity<ApiResponse<SessionResponse>> response = controller.proposeSession(req);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(sessionId, response.getBody().data().sessionId());
    assertEquals("PROPOSED", response.getBody().data().status());
  }

  @Test
  @DisplayName("POST /sessions/{id}/respond -> HTTP 200 OK khi phản hồi đề xuất")
  void shouldRespondToProposalSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    RespondProposalRequest req = new RespondProposalRequest(true, null);

    Instant startAt = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);
    TutoringSession scheduledSession = new TutoringSession(
        sessionId, classId, tutorId, studentId, startAt, endAt,
        SessionMode.ONLINE, null, null, null,
        SessionStatus.SCHEDULED, studentId, currentUserId, fixedNow,
        null, startAt, null, null, null, null, false, null, null, 1, fixedNow
    );

    when(respondToProposalService.respondToProposal(sessionId, testUser, true, null))
        .thenReturn(scheduledSession);

    ResponseEntity<ApiResponse<SessionResponse>> response = controller.respondToProposal(sessionId, req);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("SCHEDULED", response.getBody().data().status());
  }

  @Test
  @DisplayName("POST /sessions/{id}/reschedule -> HTTP 201 CREATED khi tạo đề xuất đổi lịch")
  void shouldProposeRescheduleSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    Instant newStartAt = fixedNow.plus(3, ChronoUnit.DAYS);
    Instant newEndAt = newStartAt.plus(90, ChronoUnit.MINUTES);
    RescheduleSessionRequest req = new RescheduleSessionRequest(newStartAt, newEndAt, "Dời sang thứ 5");

    SessionRescheduleProposal proposal = SessionRescheduleProposal.create(
        UUID.randomUUID(), sessionId, currentUserId, newStartAt, newEndAt,
        "Dời sang thứ 5", newStartAt, fixedNow
    );

    when(rescheduleSessionService.proposeReschedule(sessionId, testUser, newStartAt, newEndAt, "Dời sang thứ 5"))
        .thenReturn(proposal);

    ResponseEntity<ApiResponse<RescheduleProposalResponse>> response = controller.proposeReschedule(sessionId, req);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(ProposalStatus.PENDING.name(), response.getBody().data().status());
  }

  @Test
  @DisplayName("POST /sessions/reschedule-proposals/{proposalId}/respond -> HTTP 200 OK khi phản hồi đổi lịch")
  void shouldRespondToRescheduleSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    UUID proposalId = UUID.randomUUID();
    RespondRescheduleRequest req = new RespondRescheduleRequest(true, "Đồng ý đổi giờ");

    Instant newStartAt = fixedNow.plus(3, ChronoUnit.DAYS);
    Instant newEndAt = newStartAt.plus(90, ChronoUnit.MINUTES);
    SessionRescheduleProposal proposal = SessionRescheduleProposal.create(
        proposalId, sessionId, UUID.randomUUID(), newStartAt, newEndAt,
        "Lý do", newStartAt, fixedNow
    );
    proposal.accept(currentUserId, fixedNow);

    when(rescheduleSessionService.respondToReschedule(proposalId, testUser, true, "Đồng ý đổi giờ"))
        .thenReturn(proposal);

    ResponseEntity<ApiResponse<RescheduleProposalResponse>> response = controller.respondToReschedule(proposalId, req);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(ProposalStatus.ACCEPTED.name(), response.getBody().data().status());
  }

  @Test
  @DisplayName("GET /sessions/{id}/reschedule-proposal -> HTTP 200 OK khi tìm thấy đề xuất đổi lịch")
  void shouldGetRescheduleProposalSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    UUID proposalId = UUID.randomUUID();
    Instant newStartAt = fixedNow.plus(3, ChronoUnit.DAYS);
    Instant newEndAt = newStartAt.plus(90, ChronoUnit.MINUTES);
    SessionRescheduleProposal proposal = SessionRescheduleProposal.create(
        proposalId, sessionId, UUID.randomUUID(), newStartAt, newEndAt,
        "Lý do", newStartAt, fixedNow
    );

    when(rescheduleSessionService.findActiveProposalBySessionId(sessionId, testUser))
        .thenReturn(java.util.Optional.of(proposal));

    ResponseEntity<ApiResponse<RescheduleProposalResponse>> response = controller.getRescheduleProposal(sessionId);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(proposalId, response.getBody().data().proposalId());
    assertEquals(sessionId, response.getBody().data().sessionId());
  }

  @Test
  @DisplayName("GET /sessions/{id}/reschedule-proposal -> ném EntityNotFoundException khi không có đề xuất PENDING")
  void shouldThrowEntityNotFoundWhenNoRescheduleProposal() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    when(rescheduleSessionService.findActiveProposalBySessionId(sessionId, testUser))
        .thenReturn(java.util.Optional.empty());

    assertThrows(EntityNotFoundException.class, () -> controller.getRescheduleProposal(sessionId));
  }

  @Test
  @DisplayName("POST /sessions/{id}/cancel -> HTTP 200 OK khi hủy buổi học")
  void shouldCancelSessionSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    CancelSessionRequest req = new CancelSessionRequest(CancelReason.PERSONAL_REASON, "Bận việc đột xuất");

    Instant startAt = fixedNow.plus(1, ChronoUnit.DAYS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);
    TutoringSession cancelledSession = new TutoringSession(
        sessionId, classId, tutorId, studentId, startAt, endAt,
        SessionMode.ONLINE, null, null, null,
        SessionStatus.CANCELLED, studentId, tutorId, fixedNow,
        null, startAt, currentUserId, fixedNow, CancelReason.PERSONAL_REASON,
        "Bận việc đột xuất", false, null, null, 2, fixedNow
    );

    when(cancelSessionService.cancelSession(sessionId, testUser, CancelReason.PERSONAL_REASON, "Bận việc đột xuất"))
        .thenReturn(cancelledSession);

    ResponseEntity<ApiResponse<SessionResponse>> response = controller.cancelSession(sessionId, req);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("CANCELLED", response.getBody().data().status());
  }

  @Test
  @DisplayName("POST /sessions/{id}/outcome -> HTTP 200 OK khi ghi nhận kết quả")
  void shouldRecordOutcomeSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    RecordOutcomeRequest req = new RecordOutcomeRequest(SessionStatus.COMPLETED, "Học sinh hiểu bài tốt");

    Instant startAt = fixedNow.minus(2, ChronoUnit.HOURS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);
    TutoringSession completedSession = new TutoringSession(
        sessionId, classId, tutorId, studentId, startAt, endAt,
        SessionMode.ONLINE, null, null, null,
        SessionStatus.COMPLETED, studentId, tutorId, fixedNow,
        null, startAt, null, null, null, null, false, currentUserId, fixedNow, 2, fixedNow
    );

    when(recordSessionOutcomeService.recordOutcome(sessionId, testUser, SessionStatus.COMPLETED, "Học sinh hiểu bài tốt"))
        .thenReturn(completedSession);

    ResponseEntity<ApiResponse<SessionResponse>> response = controller.recordOutcome(sessionId, req);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("COMPLETED", response.getBody().data().status());
  }

  @Test
  @DisplayName("GET /sessions/{id} -> HTTP 200 OK khi tìm thấy buổi học qua GetSessionDetailService")
  void shouldGetSessionDetailSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    Instant startAt = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);
    TutoringSession session = TutoringSession.createProposed(
        sessionId, classId, tutorId, studentId, startAt, endAt,
        SessionMode.ONLINE, null, null, null, studentId, startAt, fixedNow
    );

    when(getSessionDetailService.getSessionDetail(sessionId, testUser)).thenReturn(session);

    ResponseEntity<ApiResponse<SessionResponse>> response = controller.getSessionDetail(sessionId);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(sessionId, response.getBody().data().sessionId());
  }

  @Test
  @DisplayName("GET /sessions/{id} -> Ném EntityNotFoundException khi không tìm thấy")
  void shouldThrowEntityNotFoundWhenSessionMissing() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);
    when(getSessionDetailService.getSessionDetail(sessionId, testUser))
        .thenThrow(new EntityNotFoundException("Không tìm thấy buổi học với ID: " + sessionId));

    assertThrows(EntityNotFoundException.class, () -> controller.getSessionDetail(sessionId));
  }

  @Test
  @DisplayName("GET /calendar -> HTTP 200 OK trả về danh sách CalendarItemResponse")
  void shouldGetCalendarSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    Instant from = fixedNow.minus(7, ChronoUnit.DAYS);
    Instant to = fixedNow.plus(7, ChronoUnit.DAYS);

    TutoringSession s1 = TutoringSession.createProposed(
        sessionId, classId, tutorId, studentId, fixedNow, fixedNow.plus(1, ChronoUnit.HOURS),
        SessionMode.ONLINE, null, null, null, studentId, fixedNow, fixedNow
    );

    when(getCalendarService.getCalendarSessions(testUser, from, to, null)).thenReturn(List.of(s1));

    ResponseEntity<ApiResponse<List<CalendarItemResponse>>> response = controller.getCalendar(from, to, null);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().data().size());
  }

  @Test
  @DisplayName("GET /calendar?studentId=... -> HTTP 200 OK khi Phụ huynh tra cứu lịch học của con")
  void shouldGetCalendarSuccessfullyWithStudentId() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    Instant from = fixedNow.minus(7, ChronoUnit.DAYS);
    Instant to = fixedNow.plus(7, ChronoUnit.DAYS);

    TutoringSession s1 = TutoringSession.createProposed(
        sessionId, classId, tutorId, studentId, fixedNow, fixedNow.plus(1, ChronoUnit.HOURS),
        SessionMode.ONLINE, null, null, null, studentId, fixedNow, fixedNow
    );

    when(getCalendarService.getCalendarSessions(testUser, from, to, studentId)).thenReturn(List.of(s1));

    ResponseEntity<ApiResponse<List<CalendarItemResponse>>> response = controller.getCalendar(from, to, studentId);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().data().size());
  }

  @Test
  @DisplayName("Ném Unauthorized khi không có phiên người dùng đăng nhập")
  void shouldThrowUnauthorizedWhenNoCurrentUser() {
    when(currentUserProvider.getIfAvailable()).thenReturn(null);

    Instant startAt = fixedNow.plus(2, ChronoUnit.HOURS);
    Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);
    ProposeSessionRequest req = new ProposeSessionRequest(
        classId, tutorId, studentId, startAt, endAt,
        SessionMode.ONLINE, null, null, null
    );

    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> controller.proposeSession(req));
    assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
  }
}

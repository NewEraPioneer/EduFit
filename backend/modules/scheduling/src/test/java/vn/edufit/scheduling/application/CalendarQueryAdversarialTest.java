package vn.edufit.scheduling.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
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
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.scheduling.web.SessionController;
import vn.edufit.scheduling.web.response.CalendarItemResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.response.ApiResponse;

/**
 * Adversarial Empirical Test Suite for Calendar Querying & Profile Resolution.
 *
 * <p>Validates:
 * <ul>
 *   <li>Tutor, Student, Parent, and Admin calendar query resolution</li>
 *   <li>Unauthorized actor cross-tenant snooping rejection</li>
 *   <li>Parent studentId mandate and confirmation check</li>
 *   <li>Graceful resolution when role claims are omitted</li>
 *   <li>End-to-end SessionController calendar endpoint handling</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Adversarial Empirical Challenge: Calendar Querying & Security Resolution")
class CalendarQueryAdversarialTest {

  @Mock private ProfileFacade profileFacade;
  @Mock private ConnectionFacade connectionFacade;
  @Mock private TutoringSessionDomainRepository sessionRepository;
  @Mock private ObjectProvider<CurrentUser> currentUserProvider;

  // Unused service mocks needed to construct SessionController
  @Mock private ProposeSessionService proposeSessionService;
  @Mock private BatchProposeSessionService batchProposeSessionService;
  @Mock private RespondToProposalService respondToProposalService;
  @Mock private BatchRespondSessionService batchRespondSessionService;
  @Mock private RescheduleSessionService rescheduleSessionService;
  @Mock private RescheduleClassScheduleService rescheduleClassScheduleService;
  @Mock private CancelSessionService cancelSessionService;
  @Mock private RecordSessionOutcomeService recordSessionOutcomeService;
  @Mock private GetSessionDetailService getSessionDetailService;

  private SchedulingSecurityResolver securityResolver;
  private GetCalendarService calendarService;
  private SessionController controller;

  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID tutorProfileId = UUID.randomUUID();
  private final UUID studentUserId = UUID.randomUUID();
  private final UUID studentProfileId = UUID.randomUUID();
  private final UUID otherStudentUserId = UUID.randomUUID();
  private final UUID otherStudentProfileId = UUID.randomUUID();
  private final UUID parentUserId = UUID.randomUUID();
  private final UUID adminUserId = UUID.randomUUID();

  private final Instant from = Instant.parse("2026-11-01T00:00:00Z");
  private final Instant to = Instant.parse("2026-11-30T23:59:59Z");

  private CurrentUser tutorActor;
  private CurrentUser studentActor;
  private CurrentUser parentActor;
  private CurrentUser adminActor;

  @BeforeEach
  void setUp() {
    securityResolver = new SchedulingSecurityResolver(profileFacade, connectionFacade);
    calendarService = new GetCalendarService(sessionRepository, securityResolver);
    controller = new SessionController(
        proposeSessionService,
        batchProposeSessionService,
        respondToProposalService,
        batchRespondSessionService,
        rescheduleSessionService,
        rescheduleClassScheduleService,
        cancelSessionService,
        recordSessionOutcomeService,
        calendarService,
        getSessionDetailService,
        currentUserProvider
    );

    tutorActor = SimpleCurrentUser.of(tutorUserId, "TUTOR");
    studentActor = SimpleCurrentUser.of(studentUserId, "STUDENT");
    parentActor = SimpleCurrentUser.of(parentUserId, "PARENT");
    adminActor = SimpleCurrentUser.of(adminUserId, "ADMIN");
  }

  private TutoringSession createDummySession(UUID tutorId, UUID studentId) {
    return new TutoringSession(
        UUID.randomUUID(), UUID.randomUUID(), tutorId, studentId,
        from.plusSeconds(3600), from.plusSeconds(7200), SessionMode.ONLINE,
        "https://meet.google.com/abc", "Online Room", null, SessionStatus.SCHEDULED,
        studentId, tutorId, from, null, from.minusSeconds(7200),
        null, null, null, null, false, null, null, 1, from
    );
  }

  // =========================================================================
  // 1. RESOLVER & SERVICE LAYER ADVERSARIAL TESTS
  // =========================================================================
  @Nested
  @DisplayName("1. Identity Resolution & Service Security")
  class SecurityResolverAndServiceTests {

    @Test
    @DisplayName("Tutor without studentId -> resolves tutorProfileId and queries calendar")
    void tutorResolvesTutorProfileId() {
      TutorSummaryDto tutorDto = new TutorSummaryDto(tutorProfileId, tutorUserId, "Thầy Nam", null, null, null, null, false, null, null, null, null, "VERIFIED", from, null, 0);
      when(profileFacade.findTutorByUserId(tutorUserId)).thenReturn(Optional.of(tutorDto));

      TutoringSession session = createDummySession(tutorProfileId, studentProfileId);
      when(sessionRepository.findCalendarSessions(tutorProfileId, from, to)).thenReturn(List.of(session));

      List<TutoringSession> result = calendarService.getCalendarSessions(tutorActor, from, to, null);

      assertEquals(1, result.size());
      verify(sessionRepository).findCalendarSessions(tutorProfileId, from, to);
    }

    @Test
    @DisplayName("Student without studentId -> resolves studentProfileId and queries calendar")
    void studentResolvesStudentProfileId() {
      StudentSummaryDto studentDto = new StudentSummaryDto(studentProfileId, studentUserId, 1, null, null, true);
      when(profileFacade.findStudentByUserId(studentUserId)).thenReturn(Optional.of(studentDto));

      TutoringSession session = createDummySession(tutorProfileId, studentProfileId);
      when(sessionRepository.findCalendarSessions(studentProfileId, from, to)).thenReturn(List.of(session));

      List<TutoringSession> result = calendarService.getCalendarSessions(studentActor, from, to, null);

      assertEquals(1, result.size());
      verify(sessionRepository).findCalendarSessions(studentProfileId, from, to);
    }

    @Test
    @DisplayName("Student providing their own studentId -> success")
    void studentProvidingOwnStudentIdSucceeds() {
      StudentSummaryDto studentDto = new StudentSummaryDto(studentProfileId, studentUserId, 1, null, null, true);
      when(profileFacade.findStudentByUserId(studentUserId)).thenReturn(Optional.of(studentDto));

      TutoringSession session = createDummySession(tutorProfileId, studentProfileId);
      when(sessionRepository.findCalendarSessions(studentProfileId, from, to)).thenReturn(List.of(session));

      List<TutoringSession> result = calendarService.getCalendarSessions(studentActor, from, to, studentProfileId);

      assertEquals(1, result.size());
      verify(sessionRepository).findCalendarSessions(studentProfileId, from, to);
    }

    @Test
    @DisplayName("Student attempting to query another student's calendar -> Forbidden")
    void studentQueryingAnotherStudentThrowsForbidden() {
      StudentSummaryDto studentDto = new StudentSummaryDto(studentProfileId, studentUserId, 1, null, null, true);
      when(profileFacade.findStudentByUserId(studentUserId)).thenReturn(Optional.of(studentDto));
      when(connectionFacade.hasConfirmedParentLink(studentUserId, otherStudentProfileId)).thenReturn(false);

      assertThrows(ForbiddenOperationException.class, () ->
          calendarService.getCalendarSessions(studentActor, from, to, otherStudentProfileId)
      );
    }

    @Test
    @DisplayName("Parent with confirmed link -> queries student's calendar")
    void parentWithConfirmedLinkQueriesCalendar() {
      when(connectionFacade.hasConfirmedParentLink(parentUserId, studentProfileId)).thenReturn(true);

      TutoringSession session = createDummySession(tutorProfileId, studentProfileId);
      when(sessionRepository.findCalendarSessions(studentProfileId, from, to)).thenReturn(List.of(session));

      List<TutoringSession> result = calendarService.getCalendarSessions(parentActor, from, to, studentProfileId);

      assertEquals(1, result.size());
      verify(sessionRepository).findCalendarSessions(studentProfileId, from, to);
    }

    @Test
    @DisplayName("Parent with unconfirmed link -> Forbidden")
    void parentWithoutConfirmedLinkThrowsForbidden() {
      when(connectionFacade.hasConfirmedParentLink(parentUserId, studentProfileId)).thenReturn(false);
      when(profileFacade.findStudentByUserId(parentUserId)).thenReturn(Optional.empty());

      assertThrows(ForbiddenOperationException.class, () ->
          calendarService.getCalendarSessions(parentActor, from, to, studentProfileId)
      );
    }

    @Test
    @DisplayName("Parent omitting studentId -> throws InvalidOperationException (VALIDATION_FAILED)")
    void parentOmittingStudentIdThrowsValidationFailed() {
      InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
          calendarService.getCalendarSessions(parentActor, from, to, null)
      );
      assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Tutor providing a studentId -> Forbidden (tutor cannot view student's full calendar)")
    void tutorProvidingStudentIdThrowsForbidden() {
      when(profileFacade.findStudentByUserId(tutorUserId)).thenReturn(Optional.empty());
      when(connectionFacade.hasConfirmedParentLink(tutorUserId, studentProfileId)).thenReturn(false);

      assertThrows(ForbiddenOperationException.class, () ->
          calendarService.getCalendarSessions(tutorActor, from, to, studentProfileId)
      );
    }

    @Test
    @DisplayName("Admin providing studentId -> bypasses checks and queries student calendar")
    void adminProvidingStudentIdSucceeds() {
      TutoringSession session = createDummySession(tutorProfileId, studentProfileId);
      when(sessionRepository.findCalendarSessions(studentProfileId, from, to)).thenReturn(List.of(session));

      List<TutoringSession> result = calendarService.getCalendarSessions(adminActor, from, to, studentProfileId);

      assertEquals(1, result.size());
      verify(sessionRepository).findCalendarSessions(studentProfileId, from, to);
    }

    @Test
    @DisplayName("Actor without role claim but has Student profile -> gracefully resolves")
    void actorWithoutRoleClaimResolvesStudent() {
      CurrentUser unassignedActor = SimpleCurrentUser.of(studentUserId);
      StudentSummaryDto studentDto = new StudentSummaryDto(studentProfileId, studentUserId, 1, null, null, true);
      when(profileFacade.findStudentByUserId(studentUserId)).thenReturn(Optional.of(studentDto));

      TutoringSession session = createDummySession(tutorProfileId, studentProfileId);
      when(sessionRepository.findCalendarSessions(studentProfileId, from, to)).thenReturn(List.of(session));

      List<TutoringSession> result = calendarService.getCalendarSessions(unassignedActor, from, to, null);

      assertEquals(1, result.size());
      verify(sessionRepository).findCalendarSessions(studentProfileId, from, to);
    }

    @Test
    @DisplayName("Actor without role claim but has Tutor profile -> gracefully resolves")
    void actorWithoutRoleClaimResolvesTutor() {
      CurrentUser unassignedActor = SimpleCurrentUser.of(tutorUserId);
      when(profileFacade.findStudentByUserId(tutorUserId)).thenReturn(Optional.empty());
      TutorSummaryDto tutorDto = new TutorSummaryDto(tutorProfileId, tutorUserId, "Thầy Nam", null, null, null, null, false, null, null, null, null, "VERIFIED", from, null, 0);
      when(profileFacade.findTutorByUserId(tutorUserId)).thenReturn(Optional.of(tutorDto));

      TutoringSession session = createDummySession(tutorProfileId, studentProfileId);
      when(sessionRepository.findCalendarSessions(tutorProfileId, from, to)).thenReturn(List.of(session));

      List<TutoringSession> result = calendarService.getCalendarSessions(unassignedActor, from, to, null);

      assertEquals(1, result.size());
      verify(sessionRepository).findCalendarSessions(tutorProfileId, from, to);
    }

    @Test
    @DisplayName("Actor without role and without profile -> Forbidden")
    void actorWithoutRoleAndProfileThrowsForbidden() {
      UUID strangerId = UUID.randomUUID();
      CurrentUser stranger = SimpleCurrentUser.of(strangerId);
      when(profileFacade.findStudentByUserId(strangerId)).thenReturn(Optional.empty());
      when(profileFacade.findTutorByUserId(strangerId)).thenReturn(Optional.empty());

      assertThrows(ForbiddenOperationException.class, () ->
          calendarService.getCalendarSessions(stranger, from, to, null)
      );
    }
  }

  // =========================================================================
  // 2. CONTROLLER WEB LAYER INTEGRATION
  // =========================================================================
  @Nested
  @DisplayName("2. SessionController GET /calendar Web Integration")
  class ControllerWebTests {

    @Test
    @DisplayName("GET /calendar -> returns HTTP 200 with mapped CalendarItemResponse")
    void controllerGetCalendarSuccess() {
      when(currentUserProvider.getIfAvailable()).thenReturn(tutorActor);
      TutorSummaryDto tutorDto = new TutorSummaryDto(tutorProfileId, tutorUserId, "Thầy Nam", null, null, null, null, false, null, null, null, null, "VERIFIED", from, null, 0);
      when(profileFacade.findTutorByUserId(tutorUserId)).thenReturn(Optional.of(tutorDto));

      TutoringSession session = createDummySession(tutorProfileId, studentProfileId);
      when(sessionRepository.findCalendarSessions(tutorProfileId, from, to)).thenReturn(List.of(session));

      ResponseEntity<ApiResponse<List<CalendarItemResponse>>> response = controller.getCalendar(from, to, null);

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      assertEquals(1, response.getBody().data().size());
      CalendarItemResponse item = response.getBody().data().get(0);
      assertEquals(session.getSessionId(), item.sessionId());
      assertEquals(tutorProfileId, item.tutorId());
      assertEquals(studentProfileId, item.studentId());
      assertEquals("ONLINE", item.mode());
      assertEquals("SCHEDULED", item.status());
    }

    @Test
    @DisplayName("GET /calendar when unauthenticated -> throws UNAUTHORIZED")
    void controllerGetCalendarUnauthenticatedThrows() {
      when(currentUserProvider.getIfAvailable()).thenReturn(null);

      InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
          controller.getCalendar(from, to, null)
      );
      assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }
  }
}

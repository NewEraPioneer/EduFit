package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.connection.application.dto.TransitionResult;
import vn.edufit.connection.application.policy.ConnectionRequestInput;
import vn.edufit.connection.application.port.ConnectionLock;
import vn.edufit.connection.application.service.ConnectionQueryService;
import vn.edufit.connection.application.service.ConnectionRequestService;
import vn.edufit.connection.application.service.InvitationQuotaService;
import vn.edufit.connection.application.service.LinkInvitationService;
import vn.edufit.connection.application.service.ParentStudentLinkService;
import vn.edufit.connection.infra.persistence.entity.ConnectionRequest;
import vn.edufit.connection.infra.persistence.entity.LinkInvitation;
import vn.edufit.connection.infra.persistence.entity.ParentStudentLink;
import vn.edufit.connection.infra.persistence.entity.TutoringClass;
import vn.edufit.connection.infra.persistence.repository.ConnectionRequestRepository;
import vn.edufit.connection.infra.persistence.repository.LinkInvitationRepository;
import vn.edufit.connection.infra.persistence.repository.ParentStudentLinkRepository;
import vn.edufit.connection.infra.persistence.repository.TutoringClassRepository;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.shared.audit.AuditSink;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;
import vn.edufit.shared.notification.NotificationSink;

/**
 * DDT Matrix tests verifying complete permission, state transition, and boundary matrices
 * defined in docs/in-development-testing-specification.md Section 7.2.
 */
class ConnectionMatrixDdtTest {
  private static final Instant T0 = Instant.parse("2026-10-14T00:00:00Z");

  private final LinkInvitationRepository invitations = mock(LinkInvitationRepository.class);
  private final ParentStudentLinkRepository links = mock(ParentStudentLinkRepository.class);
  private final ConnectionRequestRepository requests = mock(ConnectionRequestRepository.class);
  private final TutoringClassRepository classes = mock(TutoringClassRepository.class);
  private final IamFacade iam = mock(IamFacade.class);
  private final ProfileFacade profile = mock(ProfileFacade.class);
  private final ConnectionLock locks = mock(ConnectionLock.class);
  private final InvitationQuotaService quota = mock(InvitationQuotaService.class);
  private final ConnectionFacade facade = mock(ConnectionFacade.class);
  private final NotificationSink notifications = mock(NotificationSink.class);
  private final AuditSink audit = mock(AuditSink.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

  private UUID parentUser;
  private UUID studentUser;
  private UUID studentId;
  private UUID tutorUser;
  private UUID tutorId;
  private UUID needId;
  private UUID invitationId;
  private UUID requestId;

  @BeforeEach
  void setUp() {
    parentUser = UUID.randomUUID();
    studentUser = UUID.randomUUID();
    studentId = UUID.randomUUID();
    tutorUser = UUID.randomUUID();
    tutorId = UUID.randomUUID();
    needId = UUID.randomUUID();
    invitationId = UUID.randomUUID();
    requestId = UUID.randomUUID();

    when(iam.findUserSummaryById(parentUser)).thenReturn(Optional.of(
        new UserSummaryView(parentUser, "parent@test.invalid", "Parent Name", AccountRole.PARENT, AccountStatus.ACTIVE)
    ));
    when(iam.findUserSummaryById(studentUser)).thenReturn(Optional.of(
        new UserSummaryView(studentUser, "student@test.invalid", "Student Name", AccountRole.STUDENT, AccountStatus.ACTIVE)
    ));
    when(iam.findUserSummaryById(tutorUser)).thenReturn(Optional.of(
        new UserSummaryView(tutorUser, "tutor@test.invalid", "Tutor Name", AccountRole.TUTOR, AccountStatus.ACTIVE)
    ));

    when(profile.findStudentById(studentId)).thenReturn(Optional.of(
        new StudentSummaryDto(studentId, studentUser, 4, null, null, true)
    ));
    when(profile.findStudentByUserId(studentUser)).thenReturn(Optional.of(
        new StudentSummaryDto(studentId, studentUser, 4, null, null, true)
    ));
    when(profile.getStudentIdByUserId(studentUser)).thenReturn(studentId);

    when(profile.findTutorById(tutorId)).thenReturn(Optional.of(
        new TutorSummaryDto(tutorId, tutorUser, "Tutor Name", null, null, null, null, false, null, null, null, null, "VERIFIED", T0, null, 0)
    ));
    when(profile.getTutorIdByUserId(tutorUser)).thenReturn(tutorId);
    when(profile.findTutorOfferings(tutorId)).thenReturn(ConnectionTestFixtures.offerings(1, 4));

    when(profile.findLearningNeed(needId)).thenReturn(Optional.of(
        ConnectionTestFixtures.need(needId, studentId, studentUser, 1, 4, "ACTIVE")
    ));

    when(quota.consume(any())).thenReturn(new InvitationQuotaService.Consumption(true, T0));

    when(links.save(any())).thenAnswer(inv -> {
      ParentStudentLink l = inv.getArgument(0);
      ReflectionTestUtils.setField(l, "id", UUID.randomUUID());
      return l;
    });

    when(classes.save(any())).thenAnswer(inv -> {
      TutoringClass c = inv.getArgument(0);
      ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
      return c;
    });

    when(requests.save(any())).thenAnswer(inv -> {
      ConnectionRequest r = inv.getArgument(0);
      ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
      return r;
    });
  }

  private CurrentUser actor(UUID userId, String role) {
    CurrentUser actor = mock(CurrentUser.class);
    when(actor.getUserId()).thenReturn(userId);
    when(actor.hasRole(anyString())).thenAnswer(inv -> role.equals(inv.getArgument(0)));
    return actor;
  }

  @ParameterizedTest(name = "{0}: status={1}, hours={2}, role={3} -> allowed={4}, error={5}")
  @CsvFileSource(resources = "/testdata/link-invitation-rules.csv", numLinesToSkip = 1)
  void verifyLinkInvitationMatrixFromCsv(
      String testCaseId,
      String invitationStatus,
      long hoursElapsed,
      String targetUserRole,
      boolean isAllowedToConfirm,
      String expectedErrorCode
  ) {
    Instant testTime = T0.plus(Duration.ofHours(hoursElapsed));
    Clock fixedClock = Clock.fixed(testTime, ZoneOffset.UTC);
    LinkInvitationService service = new LinkInvitationService(invitations, links, iam, profile, locks, quota, events, ConnectionTestDefaults.expiryMock(), fixedClock);

    LinkInvitation invitation = new LinkInvitation(parentUser, studentUser, T0);
    ReflectionTestUtils.setField(invitation, "id", invitationId);

    // Prepare non-pending state if specified
    switch (invitationStatus) {
      case "ACCEPTED" -> invitation.accept(T0.plusSeconds(3600));
      case "DECLINED" -> invitation.decline(T0.plusSeconds(3600));
      case "WITHDRAWN" -> invitation.withdraw(T0.plusSeconds(3600));
      case "EXPIRED" -> invitation.expire(invitation.getExpiresAt());
    }

    when(invitations.findById(invitationId)).thenReturn(Optional.of(invitation));
    when(invitations.lockById(invitationId)).thenReturn(Optional.of(invitation));

    UUID actorId;
    if ("STUDENT".equals(targetUserRole)) {
      actorId = studentUser;
    } else if ("PARENT".equals(targetUserRole)) {
      actorId = parentUser;
    } else {
      actorId = tutorUser;
    }
    CurrentUser actor = actor(actorId, targetUserRole);

    if ("FORBIDDEN_ACCESS".equals(expectedErrorCode)) {
      assertThrows(ForbiddenOperationException.class, () -> service.respondInvitation(actor, invitationId, true));
    } else if (isAllowedToConfirm) {
      var result = service.respondInvitation(actor, invitationId, true);
      assertInstanceOf(TransitionResult.Success.class, result);
      assertEquals("ACCEPTED", invitation.getStatus());
    } else {
      // Must reject via TransitionResult.Conflict or exception
      try {
        var result = service.respondInvitation(actor, invitationId, true);
        assertInstanceOf(TransitionResult.Conflict.class, result);
      } catch (ResourceConflictException | InvalidOperationException e) {
        // Controlled rejection is expected
        assertNotNull(e.getMessage());
      }
    }
  }

  @ParameterizedTest(name = "{0}: status={1}, hours={2}, role={3}, action={4} -> allowed={5}")
  @CsvFileSource(resources = "/testdata/connection-request-matrix.csv", numLinesToSkip = 1)
  void verifyConnectionRequestMatrixFromCsv(
      String testCaseId,
      String requestStatus,
      long hoursElapsed,
      String actorRole,
      String action,
      boolean isAllowed,
      String expectedErrorCode
  ) {
    Instant testTime = T0.plus(Duration.ofHours(hoursElapsed));
    Clock fixedClock = Clock.fixed(testTime, ZoneOffset.UTC);
    ConnectionRequestService service = new ConnectionRequestService(requests, classes, profile, iam, locks, notifications, facade, new ConnectionRequestInput("legacy-compatible"), events, ConnectionTestDefaults.expiryMock(), new vn.edufit.connection.application.policy.SlotConflictDetector(), ConnectionTestDefaults.noScheduleConflicts(), ConnectionTestDefaults.noScheduledSessions(), fixedClock);

    ConnectionRequest request = new ConnectionRequest(
        studentId, tutorId, needId, studentUser, List.of("Tue 19:00"), null, T0
    );
    ReflectionTestUtils.setField(request, "id", requestId);

    switch (requestStatus) {
      case "ACCEPTED" -> request.accept(T0.plusSeconds(3600));
      case "REJECTED" -> request.reject(null, T0.plusSeconds(3600));
      case "CANCELLED" -> request.cancel(T0.plusSeconds(3600));
      case "EXPIRED" -> request.expire(request.getExpiresAt());
    }

    when(requests.findById(requestId)).thenReturn(Optional.of(request));
    when(requests.lockById(requestId)).thenReturn(Optional.of(request));
    when(facade.hasConfirmedParentLink(parentUser, studentId)).thenReturn(true);

    UUID actorId;
    switch (actorRole) {
      case "TUTOR" -> actorId = tutorUser;
      case "PARENT" -> actorId = parentUser;
      default -> actorId = studentUser;
    }
    CurrentUser actor = actor(actorId, actorRole);

    if ("FORBIDDEN_ACCESS".equals(expectedErrorCode)) {
      if ("CANCEL".equals(action)) {
        assertThrows(ForbiddenOperationException.class, () -> service.cancelRequest(actor, requestId));
      } else {
        assertThrows(ForbiddenOperationException.class, () -> service.respondRequest(actor, requestId, "ACCEPT".equals(action), null));
      }
    } else if (isAllowed) {
      if ("CANCEL".equals(action)) {
        var result = service.cancelRequest(actor, requestId);
        assertInstanceOf(TransitionResult.Success.class, result);
        assertEquals("CANCELLED", request.getStatus());
      } else {
        var result = service.respondRequest(actor, requestId, "ACCEPT".equals(action), null);
        assertInstanceOf(TransitionResult.Success.class, result);
        assertEquals("ACCEPT".equals(action) ? "ACCEPTED" : "REJECTED", request.getStatus());
      }
    } else {
      if ("CANCEL".equals(action)) {
        var result = service.cancelRequest(actor, requestId);
        assertInstanceOf(TransitionResult.Conflict.class, result);
      } else {
        var result = service.respondRequest(actor, requestId, "ACCEPT".equals(action), null);
        assertInstanceOf(TransitionResult.Conflict.class, result);
      }
    }
  }

  @Test
  void accountIdVersusProfileIdDistinctionIsStrictlyEnforced() {
    // studentUser != studentId, tutorUser != tutorId
    assertNotEquals(studentUser, studentId);
    assertNotEquals(tutorUser, tutorId);

    // Profile lookup using studentUser instead of studentId must yield empty
    when(profile.findStudentById(studentUser)).thenReturn(Optional.empty());

    CurrentUser studentActor = actor(studentUser, "STUDENT");
    ConnectionRequestService service = new ConnectionRequestService(requests, classes, profile, iam, locks, notifications, facade, new ConnectionRequestInput("legacy-compatible"), events, ConnectionTestDefaults.expiryMock(), new vn.edufit.connection.application.policy.SlotConflictDetector(), ConnectionTestDefaults.noScheduleConflicts(), ConnectionTestDefaults.noScheduledSessions(), Clock.fixed(T0, ZoneOffset.UTC));

    // If goal had wrong student profile mapping, request creation is rejected
    UUID fakeGoal = UUID.randomUUID();
    when(profile.findLearningNeed(fakeGoal)).thenReturn(Optional.of(
        ConnectionTestFixtures.need(fakeGoal, studentUser /* WRONG: account ID instead of studentId */, studentUser, 1, 4, "ACTIVE")
    ));

    assertThrows(ForbiddenOperationException.class,
        () -> service.request(studentActor, fakeGoal, tutorId, List.of("Tue 19:00"), null));
  }

  @Test
  void parentWithMultipleStudentsMaintainsIndependentConsentAndAccess() {
    UUID student2User = UUID.randomUUID();
    UUID student2Id = UUID.randomUUID();
    UUID goal2 = UUID.randomUUID();

    when(iam.findUserSummaryById(student2User)).thenReturn(Optional.of(
        new UserSummaryView(student2User, "student2@test.invalid", "Student 2", AccountRole.STUDENT, AccountStatus.ACTIVE)
    ));
    when(profile.findStudentById(student2Id)).thenReturn(Optional.of(
        new StudentSummaryDto(student2Id, student2User, 4, null, null, true)
    ));
    when(profile.findLearningNeed(goal2)).thenReturn(Optional.of(
        ConnectionTestFixtures.need(goal2, student2Id, student2User, 1, 4, "ACTIVE")
    ));

    // Parent is linked to both Student 1 and Student 2
    when(facade.hasConfirmedParentLink(parentUser, studentId)).thenReturn(true);
    when(facade.hasConfirmedParentLink(parentUser, student2Id)).thenReturn(true);

    CurrentUser parentActor = actor(parentUser, "PARENT");
    ConnectionRequestService reqService = new ConnectionRequestService(requests, classes, profile, iam, locks, notifications, facade, new ConnectionRequestInput("legacy-compatible"), events, ConnectionTestDefaults.expiryMock(), new vn.edufit.connection.application.policy.SlotConflictDetector(), ConnectionTestDefaults.noScheduleConflicts(), ConnectionTestDefaults.noScheduledSessions(), Clock.fixed(T0, ZoneOffset.UTC));

    // 1. Parent can create request for Student 1
    var res1 = reqService.request(parentActor, needId, tutorId, List.of("Tue 19:00"), null);
    assertInstanceOf(TransitionResult.Success.class, res1);

    // 2. Parent can create request for Student 2
    var res2 = reqService.request(parentActor, goal2, tutorId, List.of("Tue 19:00"), null);
    assertInstanceOf(TransitionResult.Success.class, res2);

    // 3. If parent's link with Student 1 is revoked, Student 2 is completely unaffected
    when(facade.hasConfirmedParentLink(parentUser, studentId)).thenReturn(false);
    when(facade.hasConfirmedParentLink(parentUser, student2Id)).thenReturn(true);

    // Parent cannot create request for Student 1 anymore
    assertThrows(ForbiddenOperationException.class,
        () -> reqService.request(parentActor, needId, tutorId, List.of("Tue 19:00"), null));

    // But Parent can STILL create request for Student 2
    var res2After = reqService.request(parentActor, goal2, tutorId, List.of("Tue 19:00"), null);
    assertInstanceOf(TransitionResult.Success.class, res2After);
  }

  @Test
  void cooldownBoundaryAtExactSevenDaysNanoseconds() {
    LinkInvitation declined = new LinkInvitation(parentUser, studentUser, T0.minusSeconds(8 * 86400));
    // Declined exactly 7 days ago
    Instant declineTime = T0.minusSeconds(7 * 86400);
    declined.decline(declineTime);

    // At declineTime + 7 days - 1 nanosecond -> still cooling down
    Instant justBefore = declineTime.plus(Duration.ofDays(7)).minusNanos(1);
    assertTrue(justBefore.isBefore(declined.getReinviteAfter()));

    // At declineTime + 7 days -> cooldown satisfied
    Instant exact = declineTime.plus(Duration.ofDays(7));
    assertFalse(exact.isBefore(declined.getReinviteAfter()));
  }
}

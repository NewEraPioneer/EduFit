package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.connection.application.service.ConnectionRequestService;
import vn.edufit.connection.application.service.LinkInvitationService;
import vn.edufit.connection.application.service.ParentStudentLinkService;
import vn.edufit.connection.application.port.ConnectionLock;
import vn.edufit.connection.application.service.InvitationQuotaService;
import vn.edufit.connection.application.service.ConnectionFacadeImpl;
import vn.edufit.connection.infra.persistence.entity.*;
import vn.edufit.connection.infra.persistence.repository.*;
import vn.edufit.iam.api.*;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.*;
import vn.edufit.shared.audit.AuditSink;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.*;

@ExtendWith(MockitoExtension.class)
class ConnectionUseCaseTest {
  @Mock LinkInvitationRepository invitations;
  @Mock ParentStudentLinkRepository links;
  @Mock ConnectionRequestRepository requests;
  @Mock TutoringClassRepository classes;
  @Mock IamFacade iam;
  @Mock ProfileFacade profile;
  @Mock ConnectionLock lock;
  @Mock AuditSink audit;
  @Mock org.springframework.context.ApplicationEventPublisher events;
  @Mock ConnectionInviteQuotaRepository quotas;
  ConnectionRequestService service;
  LinkInvitationService invitationService;
  ParentStudentLinkService linkService;

  @BeforeEach void setup() {
    // Expiry service thật trên cùng repository mock: test đi đúng đường hết hạn của production (kèm event)
    var expiry = new vn.edufit.connection.application.service.ConnectionExpiryService(invitations, requests, lock, profile, events, Clock.systemUTC());
    service = new ConnectionRequestService(requests, classes, profile, iam, lock, new ConnectionFacadeImpl(links, classes, requests, new vn.edufit.connection.application.policy.SlotConflictDetector()), new vn.edufit.connection.application.policy.ConnectionRequestInput("legacy-compatible"), events, expiry, new vn.edufit.connection.application.policy.SlotConflictDetector(), ConnectionTestDefaults.noScheduleConflicts(), ConnectionTestDefaults.noScheduledSessions(), Clock.systemUTC());
    invitationService = new LinkInvitationService(invitations, links, iam, profile, lock, new InvitationQuotaService(quotas, lock, Clock.systemUTC()), events, expiry, Clock.systemUTC());
    linkService = new ParentStudentLinkService(links, profile, lock, audit, events, iam, Clock.systemUTC());
  }

  private CurrentUser actor(UUID id, String role) {
    CurrentUser user = mock(CurrentUser.class);
    lenient().when(user.getUserId()).thenReturn(id);
    when(user.hasRole(anyString())).thenAnswer(i -> role.equals(i.getArgument(0)));
    lenient().when(iam.findUserSummaryById(id)).thenReturn(Optional.of(new UserSummaryView(id,
        "actor@test.invalid", "Fixture", AccountRole.valueOf(role), AccountStatus.ACTIVE)));
    return user;
  }

  @Test void unknownEmailThrowsNotFoundAndDoesNotWrite() {
    CurrentUser parent = actor(UUID.randomUUID(), "PARENT");
    when(iam.findUserSummaryByEmail("missing@example.com")).thenReturn(Optional.empty());
    assertThrows(EntityNotFoundException.class, () -> invitationService.invite(parent, "missing@example.com"));
    verify(invitations, never()).save(any());
  }

  @Test void invitationCanOnlyBeAcceptedByRecipient() {
    UUID id = UUID.randomUUID(), student = UUID.randomUUID();
    LinkInvitation invitation = new LinkInvitation(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    when(invitations.findById(id)).thenReturn(Optional.of(invitation));
    assertThrows(ForbiddenOperationException.class,
        () -> invitationService.respondInvitation(actor(student, "STUDENT"), id, true));
    verify(links, never()).save(any());
  }

  @Test void expiredInvitationCannotCreateLink() {
    UUID parent = UUID.randomUUID(), student = UUID.randomUUID(), id = UUID.randomUUID();
    LinkInvitation invitation = new LinkInvitation(parent, student, Instant.now().minusSeconds(15 * 86400));
    when(invitations.findById(id)).thenReturn(Optional.of(invitation));
    when(invitations.lockById(id)).thenReturn(Optional.of(invitation));
    assertInstanceOf(vn.edufit.connection.application.dto.TransitionResult.Conflict.class,
        invitationService.respondInvitation(actor(student, "STUDENT"), id, true));
    assertEquals("EXPIRED", invitation.getStatus());
    verify(events).publishEvent(any(vn.edufit.connection.api.event.LinkInvitationChangedEvent.class));
    verify(links, never()).save(any());
  }

  @Test void revokedLinkImmediatelyLosesParentAccess() {
    UUID student = UUID.randomUUID(), parent = UUID.randomUUID(), id = UUID.randomUUID();
    ParentStudentLink link = new ParentStudentLink(parent, student, Instant.now());
    when(links.findById(id)).thenReturn(Optional.of(link));
    when(links.lockById(id)).thenReturn(Optional.of(link));
    CurrentUser admin = actor(UUID.randomUUID(), "ADMIN");
    assertThrows(InvalidOperationException.class, () -> linkService.revokeLink(admin, id, " "));
    when(profile.findStudentById(student)).thenReturn(Optional.of(new StudentSummaryDto(student, UUID.randomUUID(), 1, null, null, true)));
    linkService.revokeLink(admin, id, "violation");
    assertEquals("REVOKED", link.getStatus());
    verify(audit).record(argThat((vn.edufit.shared.audit.AuditRecord record) -> record.actorUserId().equals(admin.getUserId()) && record.targetId().equals(id) && "violation".equals(record.reason())));
    when(links.existsByParentUserIdAndStudentIdAndStatus(parent, student, "CONFIRMED")).thenReturn(false);
    assertFalse(new ConnectionFacadeImpl(links, classes, requests, new vn.edufit.connection.application.policy.SlotConflictDetector()).hasConfirmedParentLink(parent, student));
  }

  @Test void unlinkedParentCannotSendTutorRequest() {
    UUID parent = UUID.randomUUID(), student = UUID.randomUUID(), goal = UUID.randomUUID();
    when(profile.findLearningNeed(goal)).thenReturn(Optional.of(ConnectionTestFixtures.need(goal, student, UUID.randomUUID(), 1, 1, "ACTIVE")));
    assertThrows(ForbiddenOperationException.class, () -> service.request(actor(parent, "PARENT"),
        goal, UUID.randomUUID(), List.of("Mon 18:00"), null));
    verify(requests, never()).save(any());
  }

  @Test void tutorCannotAcceptOtherTutorsRequest() {
    UUID tutorUser = UUID.randomUUID(), id = UUID.randomUUID();
    ConnectionRequest request = new ConnectionRequest(UUID.randomUUID(), UUID.randomUUID(),
        UUID.randomUUID(), UUID.randomUUID(), List.of("Tue 19:00"), null, Instant.now());
    when(requests.findById(id)).thenReturn(Optional.of(request));
    when(profile.getTutorIdByUserId(tutorUser)).thenReturn(UUID.randomUUID());
    assertThrows(ForbiddenOperationException.class,
        () -> service.respondRequest(actor(tutorUser, "TUTOR"), id, true, null));
    verify(classes, never()).save(any());
  }

  @Test void inviteQuotaCountsEvenUnknownEmails() {
    UUID id = UUID.randomUUID();
    ConnectionInviteQuota quota = new ConnectionInviteQuota(id, Instant.now());
    for (int i = 0; i < 10; i++) assertTrue(quota.consume(Instant.now()));
    when(quotas.findById(id)).thenReturn(Optional.of(quota));
    assertFalse(invitationService.invite(actor(id, "PARENT"), "nobody@example.com"));
    verify(iam, never()).findUserSummaryByEmail(anyString());
  }

  @Test void tutorAcceptCreatesOneActiveClass() {
    UUID tutorUser = UUID.randomUUID(), tutorId = UUID.randomUUID(), studentId = UUID.randomUUID();
    UUID needId = UUID.randomUUID(), id = UUID.randomUUID();
    ConnectionRequest request = new ConnectionRequest(studentId, tutorId, needId,
        UUID.randomUUID(), List.of("Tue 19:00"), null, Instant.now());
    when(requests.findById(id)).thenReturn(Optional.of(request));
    when(requests.lockById(id)).thenReturn(Optional.of(request));
    when(profile.getTutorIdByUserId(tutorUser)).thenReturn(tutorId);
    when(profile.findTutorById(tutorId)).thenReturn(Optional.of(new TutorSummaryDto(tutorId, tutorUser, "Tutor", null, null, null, null, false, null, null, null, null, "VERIFIED", Instant.now(), null, 0)));
    when(profile.findLearningNeed(needId)).thenReturn(Optional.of(ConnectionTestFixtures.need(needId, studentId, UUID.randomUUID(), 1, 2, "ACTIVE")));
    when(profile.findTutorOfferings(tutorId)).thenReturn(ConnectionTestFixtures.offerings(1, 2));
    UUID classId = UUID.randomUUID();
    when(profile.findStudentById(studentId)).thenReturn(Optional.of(new StudentSummaryDto(studentId, UUID.randomUUID(), 4, null, null, true)));
    when(classes.save(any())).thenAnswer(i -> {
      var row = (TutoringClass) i.getArgument(0);
      org.springframework.test.util.ReflectionTestUtils.setField(row, "id", classId);
      return row;
    });
    assertNotNull(service.respondRequest(actor(tutorUser, "TUTOR"), id, true, null));
    assertEquals("ACCEPTED", request.getStatus());
    verify(classes, times(1)).save(any());
    verify(events).publishEvent(isA(vn.edufit.connection.api.event.ConnectionRequestChangedEvent.class));
    verify(events).publishEvent(isA(vn.edufit.connection.api.event.TutoringClassCreatedEvent.class));
  }

  @Test void acceptingInvitationLocksStudentBeforePairAndRow() {
    UUID parent = UUID.randomUUID(), studentUser = UUID.randomUUID(), student = UUID.randomUUID(), id = UUID.randomUUID();
    var invitation = new LinkInvitation(parent, studentUser, Instant.now());
    org.springframework.test.util.ReflectionTestUtils.setField(invitation, "id", id);
    when(invitations.findById(id)).thenReturn(Optional.of(invitation));
    when(invitations.lockById(id)).thenReturn(Optional.of(invitation));
    when(profile.findStudentByUserId(studentUser)).thenReturn(Optional.of(new StudentSummaryDto(student, studentUser, 1, null, null, true)));
    when(iam.findUserSummaryById(parent)).thenReturn(Optional.of(new UserSummaryView(parent, "parent@example.test", "Parent", AccountRole.PARENT, AccountStatus.ACTIVE)));
    when(links.save(any())).thenAnswer(i -> { var row = (ParentStudentLink) i.getArgument(0); org.springframework.test.util.ReflectionTestUtils.setField(row, "id", UUID.randomUUID()); return row; });
    assertNotNull(invitationService.respondInvitation(actor(studentUser, "STUDENT"), id, true));
    var order = inOrder(lock, invitations, links);
    order.verify(lock).lockStudent(student);
    order.verify(lock).lockInvitationPair(parent, studentUser);
    order.verify(invitations).lockById(id);
    order.verify(links).save(any());
  }

  @Test void endClassFailsWhenReasonIsBlank() {
    UUID classId = UUID.randomUUID(), actorId = UUID.randomUUID();
    CurrentUser actor = actor(actorId, "TUTOR");
    assertThrows(InvalidOperationException.class, () -> service.endClass(actor, classId, "   "));
    assertThrows(InvalidOperationException.class, () -> service.endClass(actor, classId, null));
  }

  @Test void endClassFailsWhenReasonExceeds300Chars() {
    UUID classId = UUID.randomUUID(), actorId = UUID.randomUUID();
    CurrentUser actor = actor(actorId, "TUTOR");
    assertThrows(InvalidOperationException.class, () -> service.endClass(actor, classId, "a".repeat(301)));
  }

  @Test void endClassFailsWhenClassNotActive() {
    UUID classId = UUID.randomUUID(), actorId = UUID.randomUUID(), tutorId = UUID.randomUUID(), studentId = UUID.randomUUID();
    CurrentUser actor = actor(actorId, "TUTOR");
    ConnectionRequest request = new ConnectionRequest(studentId, tutorId, UUID.randomUUID(), UUID.randomUUID(), List.of("Mon 08:00"), null, Instant.now());
    TutoringClass tutoringClass = new TutoringClass(request, 200000L, Instant.now());
    tutoringClass.endClass("Đã kết thúc trước đó", Instant.now());
    when(classes.findById(classId)).thenReturn(Optional.of(tutoringClass));

    assertThrows(InvalidOperationException.class, () -> service.endClass(actor, classId, "Kết thúc lớp"));
  }

  @Test void endClassFailsWhenActorNotParticipant() {
    UUID classId = UUID.randomUUID(), actorId = UUID.randomUUID(), tutorId = UUID.randomUUID(), studentId = UUID.randomUUID();
    CurrentUser actor = actor(actorId, "TUTOR");
    ConnectionRequest request = new ConnectionRequest(studentId, tutorId, UUID.randomUUID(), UUID.randomUUID(), List.of("Mon 08:00"), null, Instant.now());
    TutoringClass tutoringClass = new TutoringClass(request, 200000L, Instant.now());
    when(classes.findById(classId)).thenReturn(Optional.of(tutoringClass));
    when(profile.findTutorById(tutorId)).thenReturn(Optional.of(new TutorSummaryDto(tutorId, UUID.randomUUID(), "Tutor", null, null, null, null, false, null, null, null, null, "VERIFIED", Instant.now(), null, 0)));

    assertThrows(ForbiddenOperationException.class, () -> service.endClass(actor, classId, "Kết thúc lớp"));
  }

  @Test void endClassSucceedsAndPublishesEvent() {
    UUID classId = UUID.randomUUID(), actorId = UUID.randomUUID(), tutorId = UUID.randomUUID(), studentId = UUID.randomUUID();
    CurrentUser actor = actor(actorId, "TUTOR");
    ConnectionRequest request = new ConnectionRequest(studentId, tutorId, UUID.randomUUID(), UUID.randomUUID(), List.of("Mon 08:00"), null, Instant.now());
    TutoringClass tutoringClass = new TutoringClass(request, 200000L, Instant.now());
    org.springframework.test.util.ReflectionTestUtils.setField(tutoringClass, "id", classId);
    when(classes.findById(classId)).thenReturn(Optional.of(tutoringClass));
    when(profile.findTutorById(tutorId)).thenReturn(Optional.of(new TutorSummaryDto(tutorId, actorId, "Tutor", null, null, null, null, false, null, null, null, null, "VERIFIED", Instant.now(), null, 0)));

    var result = service.endClass(actor, classId, "Học sinh đã hoàn thành khóa học");
    assertNotNull(result);
    assertEquals("ENDED", tutoringClass.getStatus());
    assertEquals("Học sinh đã hoàn thành khóa học", tutoringClass.getEndReason());
    verify(classes).flush();
    verify(events).publishEvent(isA(vn.edufit.connection.api.event.TutoringClassEndedEvent.class));
  }

  @Test void endClassFailsWhenSessionsAreStillScheduled() {
    UUID classId = UUID.randomUUID(), actorId = UUID.randomUUID(), tutorId = UUID.randomUUID(), studentId = UUID.randomUUID();
    CurrentUser actor = actor(actorId, "TUTOR");
    ConnectionRequest request = new ConnectionRequest(studentId, tutorId, UUID.randomUUID(), UUID.randomUUID(), List.of("Mon 08:00"), null, Instant.now());
    TutoringClass tutoringClass = new TutoringClass(request, 200000L, Instant.now());
    org.springframework.test.util.ReflectionTestUtils.setField(tutoringClass, "id", classId);
    when(classes.findById(classId)).thenReturn(Optional.of(tutoringClass));
    when(profile.findTutorById(tutorId)).thenReturn(Optional.of(new TutorSummaryDto(tutorId, actorId, "Tutor", null, null, null, null, false, null, null, null, null, "VERIFIED", Instant.now(), null, 0)));

    vn.edufit.connection.api.ClassScheduleStatusProvider mockProvider = mock(vn.edufit.connection.api.ClassScheduleStatusProvider.class);
    when(mockProvider.getScheduleInfoBatch(List.of(classId))).thenReturn(
        java.util.Map.of(classId, new vn.edufit.connection.api.ClassScheduleStatusProvider.ClassScheduleInfo("SCHEDULED", 2, 5, 7))
    );
    ConnectionRequestService customService = new ConnectionRequestService(requests, classes, profile, iam, lock, new ConnectionFacadeImpl(links, classes, requests, new vn.edufit.connection.application.policy.SlotConflictDetector()), new vn.edufit.connection.application.policy.ConnectionRequestInput("legacy-compatible"), events, ConnectionTestDefaults.expiryMock(), new vn.edufit.connection.application.policy.SlotConflictDetector(), ConnectionTestDefaults.noScheduleConflicts(), mockProvider, Clock.systemUTC());

    var ex = assertThrows(InvalidOperationException.class, () -> customService.endClass(actor, classId, "Kết thúc lớp"));
    assertTrue(ex.getMessage().contains("vẫn còn buổi học đã lên lịch"));
  }
}

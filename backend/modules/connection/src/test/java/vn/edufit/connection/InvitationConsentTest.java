package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edufit.connection.application.dto.TransitionResult;
import vn.edufit.connection.application.port.ConnectionLock;
import vn.edufit.connection.application.service.*;
import vn.edufit.connection.infra.persistence.entity.*;
import vn.edufit.connection.infra.persistence.repository.*;
import vn.edufit.iam.api.*;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.*;
import vn.edufit.shared.notification.NotificationSink;

class InvitationConsentTest {
  private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
  private final UUID parent = UUID.randomUUID(), studentUser = UUID.randomUUID(), student = UUID.randomUUID(), id = UUID.randomUUID();
  private final LinkInvitationRepository invitations = mock(LinkInvitationRepository.class);
  private final ParentStudentLinkRepository links = mock(ParentStudentLinkRepository.class);
  private final IamFacade iam = mock(IamFacade.class);
  private final ProfileFacade profile = mock(ProfileFacade.class);
  private final ConnectionLock locks = mock(ConnectionLock.class);
  private final InvitationQuotaService quota = mock(InvitationQuotaService.class);
  private final NotificationSink notifications = mock(NotificationSink.class);
  private final org.springframework.context.ApplicationEventPublisher events = mock(org.springframework.context.ApplicationEventPublisher.class);
  private final LinkInvitationService service = new LinkInvitationService(invitations, links, iam, profile, locks, quota, events, ConnectionTestDefaults.expiryMock(), Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach void accounts() {
    when(iam.findUserSummaryById(parent)).thenReturn(Optional.of(summary(parent, AccountRole.PARENT, AccountStatus.ACTIVE)));
    when(iam.findUserSummaryById(studentUser)).thenReturn(Optional.of(summary(studentUser, AccountRole.STUDENT, AccountStatus.ACTIVE)));
    when(profile.findStudentByUserId(studentUser)).thenReturn(Optional.of(new StudentSummaryDto(student, studentUser, 4, null, false)));
    when(quota.consume(any())).thenReturn(new InvitationQuotaService.Consumption(true, NOW));
    when(invitations.save(any())).thenAnswer(call -> { var row = (LinkInvitation) call.getArgument(0); ReflectionTestUtils.setField(row, "id", UUID.randomUUID()); return row; });
    when(links.save(any())).thenAnswer(call -> { var row = (ParentStudentLink) call.getArgument(0); ReflectionTestUtils.setField(row, "id", UUID.randomUUID()); return row; });
  }
  private UserSummaryView summary(UUID user, AccountRole role, AccountStatus status) {
    return new UserSummaryView(user, "private@test.invalid", "Private fixture", role, status);
  }
  private CurrentUser actor(UUID user, String role) {
    var actor = mock(CurrentUser.class); when(actor.getUserId()).thenReturn(user);
    when(actor.hasRole(anyString())).thenAnswer(call -> role.equals(call.getArgument(0))); return actor;
  }
  private LinkInvitation pending(boolean fromParent) {
    var row = new LinkInvitation(fromParent ? parent : studentUser, fromParent ? studentUser : parent, NOW.minusSeconds(1));
    ReflectionTestUtils.setField(row, "id", id);
    when(invitations.findById(id)).thenReturn(Optional.of(row));
    when(invitations.lockById(id)).thenReturn(Optional.of(row)); return row;
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void bothDirectionsCreateLinkOnlyAfterRecipientAccepts(boolean fromParent) {
    var row = pending(fromParent); UUID recipient = fromParent ? studentUser : parent;
    assertEquals("PENDING", row.getStatus()); verifyNoInteractions(links);
    var result = service.respondInvitation(actor(recipient, fromParent ? "STUDENT" : "PARENT"), id, true);
    assertInstanceOf(TransitionResult.Success.class, result); assertEquals("ACCEPTED", row.getStatus());
    verify(links).save(any(ParentStudentLink.class));
  }
  @Test void inactiveRecipientCannotAcceptOrCreateLink() {
    var row = pending(true);
    when(iam.findUserSummaryById(studentUser)).thenReturn(Optional.of(summary(studentUser, AccountRole.STUDENT, AccountStatus.DEACTIVATED)));
    assertThrows(ForbiddenOperationException.class, () -> service.respondInvitation(actor(studentUser, "STUDENT"), id, true));
    assertEquals("PENDING", row.getStatus()); verify(links, never()).save(any()); verifyNoInteractions(notifications);
  }
  @Test void inactiveInviterCannotBeAccepted() {
    var row = pending(true);
    when(iam.findUserSummaryById(parent)).thenReturn(Optional.of(summary(parent, AccountRole.PARENT, AccountStatus.DEACTIVATED)));
    assertThrows(InvalidOperationException.class, () -> service.respondInvitation(actor(studentUser, "STUDENT"), id, true));
    assertEquals("PENDING", row.getStatus()); verify(links, never()).save(any());
  }
  @Test void existingConfirmedLinkClosesInvitationAndReturnsConflict() {
    var row = pending(true);
    when(links.existsByParentUserIdAndStudentIdAndStatus(parent, student, "CONFIRMED")).thenReturn(true);
    var result = service.respondInvitation(actor(studentUser, "STUDENT"), id, true);
    assertInstanceOf(TransitionResult.Conflict.class, result); assertEquals("WITHDRAWN", row.getStatus());
    assertEquals(NOW, row.getRespondedAt()); verify(links, never()).save(any()); verifyNoInteractions(notifications);
    var capture = org.mockito.ArgumentCaptor.forClass(vn.edufit.connection.api.event.LinkInvitationChangedEvent.class);
    verify(events).publishEvent(capture.capture());
    assertEquals(vn.edufit.connection.api.event.LinkInvitationChangedEvent.Cause.SYSTEM_ALREADY_LINKED, capture.getValue().cause());
    assertNull(capture.getValue().actorUserId());
  }
  @ParameterizedTest @ValueSource(strings = {"unknown", "inactive", "same-role", "self", "pending", "cooldown", "confirmed"})
  void ineligibleTargetThrowsExplicitDomainExceptionAndDoesNotWrite(String condition) {
    when(iam.findUserSummaryByEmail("target@test.invalid")).thenReturn(Optional.of(summary(studentUser, AccountRole.STUDENT, AccountStatus.ACTIVE)));
    switch (condition) {
      case "unknown" -> when(iam.findUserSummaryByEmail("target@test.invalid")).thenReturn(Optional.empty());
      case "inactive" -> when(iam.findUserSummaryByEmail("target@test.invalid")).thenReturn(Optional.of(summary(studentUser, AccountRole.STUDENT, AccountStatus.DEACTIVATED)));
      case "same-role" -> when(iam.findUserSummaryByEmail("target@test.invalid")).thenReturn(Optional.of(summary(studentUser, AccountRole.PARENT, AccountStatus.ACTIVE)));
      case "self" -> when(iam.findUserSummaryByEmail("target@test.invalid")).thenReturn(Optional.of(summary(parent, AccountRole.STUDENT, AccountStatus.ACTIVE)));
      case "pending" -> when(invitations.findPendingPair(parent, studentUser)).thenReturn(List.of(new LinkInvitation(parent, studentUser, NOW)));
      case "cooldown" -> {
        var declined = new LinkInvitation(parent, studentUser, NOW.minusSeconds(1)); declined.decline(NOW);
        when(invitations.findFirstByInviterUserIdAndInvitedUserIdAndStatusOrderByReinviteAfterDesc(parent, studentUser, "DECLINED")).thenReturn(Optional.of(declined));
      }
      case "confirmed" -> when(links.existsByParentUserIdAndStudentIdAndStatus(parent, student, "CONFIRMED")).thenReturn(true);
    }
    Class<? extends Exception> expected = switch (condition) {
      case "unknown" -> EntityNotFoundException.class;
      case "inactive", "same-role", "self" -> InvalidOperationException.class;
      case "pending", "cooldown", "confirmed" -> ResourceConflictException.class;
      default -> throw new IllegalArgumentException();
    };
    assertThrows(expected, () -> service.invite(actor(parent, "PARENT"), "target@test.invalid"));
    verify(invitations, never()).save(any()); verifyNoInteractions(notifications);
    verify(invitations, never()).findPair(any(), any());
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void bothDirectionsInviteActiveAccountWithoutGrantingConsent(boolean fromParent) {
    UUID inviter = fromParent ? parent : studentUser, invited = fromParent ? studentUser : parent;
    when(iam.findUserSummaryByEmail("target@test.invalid")).thenReturn(Optional.of(summary(invited,
        fromParent ? AccountRole.STUDENT : AccountRole.PARENT, AccountStatus.ACTIVE)));
    assertTrue(service.invite(actor(inviter, fromParent ? "PARENT" : "STUDENT"), "target@test.invalid"));
    var capture = org.mockito.ArgumentCaptor.forClass(LinkInvitation.class); verify(invitations).save(capture.capture());
    assertEquals("PENDING", capture.getValue().getStatus()); assertEquals(inviter, capture.getValue().getInviterUserId());
    assertEquals(invited, capture.getValue().getInvitedUserId()); verify(links, never()).save(any());
  }

  @ParameterizedTest @ValueSource(longs = {-1, 0, 1})
  void latestDirectionalCooldownEndsAtExactBoundary(long offset) {
    var declined = new LinkInvitation(parent, studentUser, NOW.minusSeconds(8 * 86400));
    declined.decline(NOW.minusSeconds(7 * 86400).plusNanos(offset));
    when(iam.findUserSummaryByEmail("target@test.invalid")).thenReturn(Optional.of(summary(studentUser, AccountRole.STUDENT, AccountStatus.ACTIVE)));
    when(invitations.findFirstByInviterUserIdAndInvitedUserIdAndStatusOrderByReinviteAfterDesc(parent, studentUser, "DECLINED")).thenReturn(Optional.of(declined));
    if (offset > 0) {
      assertThrows(ResourceConflictException.class, () -> service.invite(actor(parent, "PARENT"), "target@test.invalid"));
      verify(invitations, never()).save(any());
    } else {
      assertTrue(service.invite(actor(parent, "PARENT"), "target@test.invalid"));
      verify(invitations, times(1)).save(any());
    }
    assertEquals(NOW.plusNanos(offset), vn.edufit.connection.application.dto.InvitationResult.from(declined).reinviteAfter());
  }

  @Test void inactiveActorCannotConsumeQuotaOrInvite() {
    when(iam.findUserSummaryById(parent)).thenReturn(Optional.of(summary(parent, AccountRole.PARENT, AccountStatus.DEACTIVATED)));
    assertThrows(ForbiddenOperationException.class, () -> service.invite(actor(parent, "PARENT"), "someone@test.invalid"));
    verifyNoInteractions(quota); verify(invitations, never()).save(any());
  }
}

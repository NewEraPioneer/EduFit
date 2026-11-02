package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import vn.edufit.connection.api.event.ConnectionRequestChangedEvent;
import vn.edufit.connection.api.event.LinkInvitationChangedEvent;
import vn.edufit.connection.application.port.ConnectionLock;
import vn.edufit.connection.application.service.ConnectionExpiryService;
import vn.edufit.connection.infra.persistence.entity.ConnectionRequest;
import vn.edufit.connection.infra.persistence.entity.LinkInvitation;
import vn.edufit.connection.infra.persistence.repository.ConnectionRequestRepository;
import vn.edufit.connection.infra.persistence.repository.LinkInvitationRepository;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;

@ExtendWith(MockitoExtension.class)
class ConnectionExpiryServiceTest {
  @Mock LinkInvitationRepository invitations;
  @Mock ConnectionRequestRepository requests;
  @Mock ConnectionLock lock;
  @Mock ProfileFacade profile;
  @Mock ApplicationEventPublisher events;
  @Mock Clock clock;

  ConnectionExpiryService expiryService;

  @BeforeEach
  void setUp() {
    expiryService = new ConnectionExpiryService(invitations, requests, lock, profile, events, clock);
  }

  @Test
  void nonExistentInvitationReturnsFalseWithoutLockOrEvent() {
    UUID id = UUID.randomUUID();
    when(invitations.findById(id)).thenReturn(Optional.empty());
    assertFalse(expiryService.expireInvitation(id));
    verifyNoInteractions(lock, events);
  }

  @Test
  void nonExistentRequestReturnsFalseWithoutLockOrEvent() {
    UUID id = UUID.randomUUID();
    when(requests.findById(id)).thenReturn(Optional.empty());
    assertFalse(expiryService.expireRequest(id));
    verifyNoInteractions(lock, events);
  }

  @ParameterizedTest
  @ValueSource(longs = {-1000, 0, 1000})
  void invitationExpiryBoundaryAtExactDeadline(long offsetMillis) {
    Instant createdAt = Instant.parse("2026-10-01T10:00:00Z");
    Instant deadline = createdAt.plus(Duration.ofDays(14));
    Instant now = deadline.plusMillis(offsetMillis);
    when(clock.instant()).thenReturn(now);

    UUID id = UUID.randomUUID(), inviter = UUID.randomUUID(), invited = UUID.randomUUID(), student = UUID.randomUUID();
    var invitation = new LinkInvitation(inviter, invited, createdAt);
    org.springframework.test.util.ReflectionTestUtils.setField(invitation, "id", id);
    when(invitations.findById(id)).thenReturn(Optional.of(invitation));

    if (offsetMillis < 0) {
      assertFalse(expiryService.expireInvitation(id));
      assertEquals("PENDING", invitation.getStatus());
      verifyNoInteractions(lock, events);
    } else {
      when(invitations.lockById(id)).thenReturn(Optional.of(invitation));
      when(profile.findStudentByUserId(inviter)).thenReturn(Optional.empty());
      when(profile.findStudentByUserId(invited)).thenReturn(Optional.of(new StudentSummaryDto(student, invited, 4, null, null, true)));

      assertTrue(expiryService.expireInvitation(id));
      assertEquals("EXPIRED", invitation.getStatus());
      verify(lock).lockInvitationPair(inviter, invited);
      verify(invitations).flush();

      var captor = ArgumentCaptor.forClass(LinkInvitationChangedEvent.class);
      verify(events).publishEvent(captor.capture());
      LinkInvitationChangedEvent event = captor.getValue();
      assertEquals(id, event.aggregateId());
      assertNull(event.actorUserId());
      assertEquals(student, event.studentId());
      assertEquals("PENDING", event.oldStatus());
      assertEquals("EXPIRED", event.newStatus());
      assertEquals(now, event.occurredAt());
      assertEquals(inviter, event.inviterUserId());
      assertEquals(invited, event.invitedUserId());
      assertEquals(LinkInvitationChangedEvent.Cause.SYSTEM_EXPIRY, event.cause());
    }
  }

  @Test
  void terminalInvitationIsNoOpAndDoesNotPublish() {
    Instant createdAt = Instant.parse("2026-10-01T10:00:00Z");
    Instant now = createdAt.plus(Duration.ofDays(15));
    when(clock.instant()).thenReturn(now);

    UUID id = UUID.randomUUID(), inviter = UUID.randomUUID(), invited = UUID.randomUUID();
    var invitation = new LinkInvitation(inviter, invited, createdAt);
    invitation.decline(createdAt.plus(Duration.ofDays(1)));
    when(invitations.findById(id)).thenReturn(Optional.of(invitation));

    assertFalse(expiryService.expireInvitation(id));
    assertEquals("DECLINED", invitation.getStatus());
    verifyNoInteractions(lock, events);
  }

  @ParameterizedTest
  @ValueSource(longs = {-1000, 0, 1000})
  void requestExpiryBoundaryAtExactDeadline(long offsetMillis) {
    Instant createdAt = Instant.parse("2026-10-01T10:00:00Z");
    Instant deadline = createdAt.plus(Duration.ofDays(7));
    Instant now = deadline.plusMillis(offsetMillis);
    when(clock.instant()).thenReturn(now);

    UUID id = UUID.randomUUID(), student = UUID.randomUUID(), tutor = UUID.randomUUID();
    UUID goal = UUID.randomUUID(), sender = UUID.randomUUID(), studentUser = UUID.randomUUID(), tutorUser = UUID.randomUUID();

    var request = new ConnectionRequest(student, tutor, goal, sender, List.of("Tue 19:00"), "msg", createdAt);
    when(requests.findById(id)).thenReturn(Optional.of(request));

    if (offsetMillis < 0) {
      assertFalse(expiryService.expireRequest(id));
      assertEquals("PENDING", request.getStatus());
      verifyNoInteractions(lock, events);
    } else {
      when(requests.lockById(id)).thenReturn(Optional.of(request));
      when(profile.findStudentById(student)).thenReturn(Optional.of(new StudentSummaryDto(student, studentUser, 4, null, null, true)));
      when(profile.findTutorById(tutor)).thenReturn(Optional.of(new TutorSummaryDto(tutor, tutorUser, "Tutor", null, null, null, null, false, null, null, null, null, "VERIFIED", createdAt, null, 0)));

      assertTrue(expiryService.expireRequest(id));
      assertEquals("EXPIRED", request.getStatus());
      verify(lock).lockStudent(student);
      verify(requests).flush();

      var captor = ArgumentCaptor.forClass(ConnectionRequestChangedEvent.class);
      verify(events).publishEvent(captor.capture());
      ConnectionRequestChangedEvent event = captor.getValue();
      assertEquals(id, event.aggregateId());
      assertNull(event.actorUserId());
      assertEquals(student, event.studentId());
      assertEquals("PENDING", event.oldStatus());
      assertEquals("EXPIRED", event.newStatus());
      assertEquals(now, event.occurredAt());
      assertEquals(sender, event.sentByUserId());
      assertEquals(studentUser, event.studentUserId());
      assertEquals(tutorUser, event.tutorUserId());
      assertNull(event.classId());
    }
  }

  @Test
  void terminalRequestIsNoOpAndDoesNotPublish() {
    Instant createdAt = Instant.parse("2026-10-01T10:00:00Z");
    Instant now = createdAt.plus(Duration.ofDays(10));
    when(clock.instant()).thenReturn(now);

    UUID id = UUID.randomUUID(), student = UUID.randomUUID(), tutor = UUID.randomUUID();
    var request = new ConnectionRequest(student, tutor, UUID.randomUUID(), UUID.randomUUID(), List.of("Tue 19:00"), null, createdAt);
    request.cancel(createdAt.plus(Duration.ofDays(1)));
    when(requests.findById(id)).thenReturn(Optional.of(request));

    assertFalse(expiryService.expireRequest(id));
    assertEquals("CANCELLED", request.getStatus());
    verifyNoInteractions(lock, events);
  }

  @Test
  void candidateQueriesDelegateToRepositoriesWithBoundedLimit() {
    Instant now = Instant.parse("2026-10-14T10:00:00Z");
    UUID id1 = UUID.randomUUID(), id2 = UUID.randomUUID();

    when(invitations.findCandidateExpiredIds(now, PageRequest.of(0, 100))).thenReturn(List.of(id1));
    when(requests.findCandidateExpiredIds(now, PageRequest.of(0, 50))).thenReturn(List.of(id2));

    List<UUID> invCandidates = expiryService.findExpiredInvitationCandidateIds(now, 100);
    List<UUID> reqCandidates = expiryService.findExpiredRequestCandidateIds(now, 50);

    assertEquals(List.of(id1), invCandidates);
    assertEquals(List.of(id2), reqCandidates);
    verify(invitations).findCandidateExpiredIds(now, PageRequest.of(0, 100));
    verify(requests).findCandidateExpiredIds(now, PageRequest.of(0, 50));
  }
}

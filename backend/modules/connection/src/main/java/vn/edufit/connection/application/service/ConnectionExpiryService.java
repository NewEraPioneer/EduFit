package vn.edufit.connection.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.event.ConnectionRequestChangedEvent;
import vn.edufit.connection.api.event.LinkInvitationChangedEvent;
import vn.edufit.connection.application.policy.ConnectionTransitions;
import vn.edufit.connection.application.port.ConnectionLock;
import vn.edufit.connection.infra.persistence.entity.ConnectionRequest;
import vn.edufit.connection.infra.persistence.entity.LinkInvitation;
import vn.edufit.connection.infra.persistence.repository.ConnectionRequestRepository;
import vn.edufit.connection.infra.persistence.repository.LinkInvitationRepository;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;

@Service
public class ConnectionExpiryService {
  private final LinkInvitationRepository invitations;
  private final ConnectionRequestRepository requests;
  private final ConnectionLock lock;
  private final ProfileFacade profile;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public ConnectionExpiryService(LinkInvitationRepository invitations, ConnectionRequestRepository requests,
      ConnectionLock lock, ProfileFacade profile, ApplicationEventPublisher events, Clock clock) {
    this.invitations = invitations;
    this.requests = requests;
    this.lock = lock;
    this.profile = profile;
    this.events = events;
    this.clock = clock;
  }

  public List<UUID> findExpiredInvitationCandidateIds(Instant now, int limit) {
    return invitations.findCandidateExpiredIds(now, PageRequest.of(0, limit));
  }

  public List<UUID> findExpiredRequestCandidateIds(Instant now, int limit) {
    return requests.findCandidateExpiredIds(now, PageRequest.of(0, limit));
  }

  @Transactional
  public boolean expireInvitation(UUID invitationId) {
    return expireInvitation(invitationId, clock.instant());
  }

  @Transactional
  public boolean expireInvitation(UUID invitationId, Instant now) {
    LinkInvitation initial = invitations.findById(invitationId).orElse(null);
    if (initial == null) return false;
    if (!"PENDING".equals(initial.getStatus()) || !ConnectionTransitions.isDue(initial.getExpiresAt(), now)) {
      return false;
    }
    lock.lockInvitationPair(initial.getInviterUserId(), initial.getInvitedUserId());
    LinkInvitation invitation = invitations.lockById(invitationId).orElse(null);
    if (invitation == null) return false;
    if (!"PENDING".equals(invitation.getStatus())) return false;
    if (!ConnectionTransitions.isDue(invitation.getExpiresAt(), now)) return false;

    invitation.expire(now);
    invitations.flush();

    UUID studentId = resolveStudentId(invitation.getInviterUserId(), invitation.getInvitedUserId());
    UUID aggregateId = invitation.getId() != null ? invitation.getId() : invitationId;
    events.publishEvent(new LinkInvitationChangedEvent(
        UUID.randomUUID(), aggregateId, null, studentId,
        "PENDING", "EXPIRED", now,
        invitation.getInviterUserId(), invitation.getInvitedUserId(), null,
        LinkInvitationChangedEvent.Cause.SYSTEM_EXPIRY
    ));
    return true;
  }

  @Transactional
  public boolean expireRequest(UUID requestId) {
    return expireRequest(requestId, clock.instant());
  }

  @Transactional
  public boolean expireRequest(UUID requestId, Instant now) {
    ConnectionRequest initial = requests.findById(requestId).orElse(null);
    if (initial == null) return false;
    if (!"PENDING".equals(initial.getStatus()) || !ConnectionTransitions.isDue(initial.getExpiresAt(), now)) {
      return false;
    }
    lock.lockStudent(initial.getStudentId());
    ConnectionRequest request = requests.lockById(requestId).orElse(null);
    if (request == null) return false;
    if (!"PENDING".equals(request.getStatus())) return false;
    if (!ConnectionTransitions.isDue(request.getExpiresAt(), now)) return false;

    request.expire(now);
    requests.flush();

    UUID studentUserId = profile.findStudentById(request.getStudentId())
        .map(StudentSummaryDto::userId)
        .orElse(null);
    UUID tutorUserId = profile.findTutorById(request.getTutorId())
        .map(TutorSummaryDto::userId)
        .orElse(null);

    if (studentUserId != null && tutorUserId != null) {
      events.publishEvent(new ConnectionRequestChangedEvent(
          UUID.randomUUID(), requestId, null, request.getStudentId(),
          "PENDING", "EXPIRED", now,
          request.getSentByUserId(), studentUserId, tutorUserId, null
      ));
    }
    return true;
  }

  private UUID resolveStudentId(UUID a, UUID b) {
    return profile.findStudentByUserId(a)
        .map(StudentSummaryDto::studentId)
        .orElseGet(() -> profile.findStudentByUserId(b)
            .map(StudentSummaryDto::studentId)
            .orElse(null));
  }
}

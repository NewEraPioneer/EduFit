package vn.edufit.connection.application.dto;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.connection.infra.persistence.entity.LinkInvitation;

public record InvitationResult(UUID invitationId, UUID inviterUserId, UUID invitedUserId,
    String status, Instant createdAt, Instant expiresAt, Instant reinviteAfter,
    String inviterName, String inviterRole) {

  public InvitationResult(UUID invitationId, UUID inviterUserId, UUID invitedUserId, String status, Instant createdAt, Instant expiresAt) {
    this(invitationId, inviterUserId, invitedUserId, status, createdAt, expiresAt, null, null, null);
  }

  public InvitationResult(UUID invitationId, UUID inviterUserId, UUID invitedUserId, String status, Instant createdAt, Instant expiresAt, Instant reinviteAfter) {
    this(invitationId, inviterUserId, invitedUserId, status, createdAt, expiresAt, reinviteAfter, null, null);
  }

  public static InvitationResult from(LinkInvitation r) {
    return new InvitationResult(r.getId(), r.getInviterUserId(), r.getInvitedUserId(), r.getStatus(), r.getCreatedAt(), r.getExpiresAt(), r.getReinviteAfter(), null, null);
  }

  public static InvitationResult from(LinkInvitation r, String inviterName, String inviterRole) {
    return new InvitationResult(r.getId(), r.getInviterUserId(), r.getInvitedUserId(), r.getStatus(), r.getCreatedAt(), r.getExpiresAt(), r.getReinviteAfter(), inviterName, inviterRole);
  }
}

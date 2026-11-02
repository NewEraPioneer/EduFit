package vn.edufit.connection.web.response;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import vn.edufit.connection.application.dto.InvitationResult;

public record InvitationResponse(
    UUID invitationId,
    UUID inviterUserId,
    UUID invitedUserId,
    String status,
    Instant createdAt,
    Instant expiresAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) Instant reinviteAfter,
    @JsonInclude(JsonInclude.Include.NON_NULL) String inviterName,
    @JsonInclude(JsonInclude.Include.NON_NULL) String inviterRole) {

  public InvitationResponse(UUID invitationId, UUID inviterUserId, UUID invitedUserId, String status, Instant createdAt, Instant expiresAt) {
    this(invitationId, inviterUserId, invitedUserId, status, createdAt, expiresAt, null, null, null);
  }

  public InvitationResponse(UUID invitationId, UUID inviterUserId, UUID invitedUserId, String status, Instant createdAt, Instant expiresAt, Instant reinviteAfter) {
    this(invitationId, inviterUserId, invitedUserId, status, createdAt, expiresAt, reinviteAfter, null, null);
  }

  public static InvitationResponse of(InvitationResult r) {
    return new InvitationResponse(r.invitationId(), r.inviterUserId(), r.invitedUserId(), r.status(), r.createdAt(), r.expiresAt(), r.reinviteAfter(), r.inviterName(), r.inviterRole());
  }
}

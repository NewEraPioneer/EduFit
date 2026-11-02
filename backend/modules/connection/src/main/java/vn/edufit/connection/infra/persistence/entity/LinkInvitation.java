package vn.edufit.connection.infra.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import vn.edufit.connection.application.policy.ConnectionTransitions;
import java.util.UUID;

@Entity
@Table(name = "link_invitation")
public class LinkInvitation {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "invitation_id") private UUID id;
  @Column(name = "inviter_user_id", nullable = false) private UUID inviterUserId;
  @Column(name = "invited_user_id", nullable = false) private UUID invitedUserId;
  @Column(name = "status", nullable = false) private String status;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "responded_at") private Instant respondedAt;
  @Column(name = "reinvite_after") private Instant reinviteAfter;
  @Version @Column(name = "version", nullable = false) private Long version;

  protected LinkInvitation() {}

  public LinkInvitation(UUID inviter, UUID invited, Instant now) {
    inviterUserId = inviter;
    invitedUserId = invited;
    status = "PENDING";
    createdAt = now;
    expiresAt = now.plus(ConnectionTransitions.INVITATION_TTL);
  }

  public void expire(Instant now) {
    if ("PENDING".equals(status) && ConnectionTransitions.isDue(expiresAt, now)) status = "EXPIRED";
  }

  public void accept(Instant now) {
    ConnectionTransitions.requirePending(status, expiresAt, now);
    status = "ACCEPTED"; respondedAt = now; }
  public void decline(Instant now) {
    ConnectionTransitions.requirePending(status, expiresAt, now);
    status = "DECLINED";
    respondedAt = now;
    reinviteAfter = now.plus(ConnectionTransitions.DECLINE_COOLDOWN);
  }
  public void withdraw(Instant now) {
    ConnectionTransitions.requirePending(status, expiresAt, now);
    status = "WITHDRAWN"; respondedAt = now; }

  public UUID getId() { return id; }
  public UUID getInviterUserId() { return inviterUserId; }
  public UUID getInvitedUserId() { return invitedUserId; }
  public String getStatus() { return status; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getReinviteAfter() { return reinviteAfter; }
  public Instant getRespondedAt() { return respondedAt; }
}

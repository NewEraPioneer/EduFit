package vn.edufit.connection.infra.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import vn.edufit.connection.application.policy.ConnectionTransitions;
import java.util.UUID;

@Entity
@Table(name = "connection_invite_quota")
public class ConnectionInviteQuota {
  @Id @Column(name = "user_id") private UUID userId;
  @Column(name = "window_started_at", nullable = false) private Instant windowStartedAt;
  @Column(name = "attempts", nullable = false) private int attempts;

  protected ConnectionInviteQuota() {}
  public ConnectionInviteQuota(UUID userId, Instant now) {
    this.userId = userId;
    windowStartedAt = now;
    attempts = 0;
  }

  public boolean consume(Instant now) {
    if (!windowStartedAt.plus(ConnectionTransitions.QUOTA_WINDOW).isAfter(now)) {
      windowStartedAt = now;
      attempts = 0;
    }
    if (attempts >= ConnectionTransitions.INVITATIONS_PER_WINDOW) return false;
    attempts++;
    return true;
  }
}

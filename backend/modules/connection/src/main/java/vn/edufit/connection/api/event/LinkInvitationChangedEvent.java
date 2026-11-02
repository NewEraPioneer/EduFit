package vn.edufit.connection.api.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Public transition snapshot. System actors and creation oldStatus may be null.
 * Contains IDs only; no email, message or private audit reason. In-memory delivery is not a durable outbox.
 */
public record LinkInvitationChangedEvent(UUID eventId, UUID aggregateId, UUID actorUserId, UUID studentId,
    String oldStatus, String newStatus, Instant occurredAt, UUID inviterUserId, UUID invitedUserId, UUID linkId, Cause cause) {
  public LinkInvitationChangedEvent {
    EventContract.requireEnvelope(eventId, aggregateId, oldStatus, newStatus, occurredAt);
    Objects.requireNonNull(inviterUserId, "inviterUserId");
    Objects.requireNonNull(invitedUserId, "invitedUserId");
    Objects.requireNonNull(cause, "cause");
  }
  /** Closed machine-readable causes; never free-text/private audit reasons. */
  public enum Cause { USER_ACTION, SYSTEM_EXPIRY, SYSTEM_ALREADY_LINKED }
}

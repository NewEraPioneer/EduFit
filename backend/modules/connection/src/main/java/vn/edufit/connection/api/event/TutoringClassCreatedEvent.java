package vn.edufit.connection.api.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Public transition snapshot. System actors and creation oldStatus may be null.
 * Contains IDs only; no email, message or private audit reason. In-memory delivery is not a durable outbox.
 */
public record TutoringClassCreatedEvent(UUID eventId, UUID aggregateId, UUID actorUserId, UUID studentId,
    String oldStatus, String newStatus, Instant occurredAt, UUID requestId, UUID tutorId, UUID needId) {
  public TutoringClassCreatedEvent {
    EventContract.requireEnvelope(eventId, aggregateId, oldStatus, newStatus, occurredAt);
    Objects.requireNonNull(studentId, "studentId");
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(tutorId, "tutorId");
    Objects.requireNonNull(needId, "needId");
  }
}

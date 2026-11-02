package vn.edufit.connection.api.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Public transition snapshot. System actors and creation oldStatus may be null.
 * Contains IDs only; no email, message or private audit reason. In-memory delivery is not a durable outbox.
 */
public record ConnectionRequestChangedEvent(UUID eventId, UUID aggregateId, UUID actorUserId, UUID studentId,
    String oldStatus, String newStatus, Instant occurredAt, UUID sentByUserId, UUID studentUserId, UUID tutorUserId, UUID classId) {
  public ConnectionRequestChangedEvent {
    EventContract.requireEnvelope(eventId, aggregateId, oldStatus, newStatus, occurredAt);
    Objects.requireNonNull(studentId, "studentId");
    Objects.requireNonNull(sentByUserId, "sentByUserId");
    Objects.requireNonNull(studentUserId, "studentUserId");
    Objects.requireNonNull(tutorUserId, "tutorUserId");
  }
}

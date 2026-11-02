package vn.edufit.connection.api.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Public transition snapshot. System actors and creation oldStatus may be null.
 * Contains IDs only; no email, message or private audit reason. In-memory delivery is not a durable outbox.
 */
public record ParentStudentLinkChangedEvent(UUID eventId, UUID aggregateId, UUID actorUserId, UUID studentId,
    String oldStatus, String newStatus, Instant occurredAt, UUID parentUserId, UUID studentUserId) {
  public ParentStudentLinkChangedEvent {
    EventContract.requireEnvelope(eventId, aggregateId, oldStatus, newStatus, occurredAt);
    Objects.requireNonNull(studentId, "studentId");
    Objects.requireNonNull(parentUserId, "parentUserId");
    Objects.requireNonNull(studentUserId, "studentUserId");
  }
}

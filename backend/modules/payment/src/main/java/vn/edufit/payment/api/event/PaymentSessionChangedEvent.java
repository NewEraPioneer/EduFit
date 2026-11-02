package vn.edufit.payment.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/** Raised once per persisted financial transition, in the money transaction. */
public record PaymentSessionChangedEvent(UUID eventId, Instant occurredAt, UUID sessionId,
    UUID payerId, UUID tutorUserId, UUID studentId, String state, Instant reviewDeadline)
    implements BaseDomainEvent {
  @Override public UUID getEventId() { return eventId; }
  @Override public Instant getOccurredAt() { return occurredAt; }
}

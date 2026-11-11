package vn.edufit.payment.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Raised once per persisted financial transition, in the money transaction. {@code sessionStatus} is the charge
 * status at that moment (state is the transition, or CASE_MESSAGE); {@code actorUserId} is who caused it, if a person did.
 */
public record PaymentSessionChangedEvent(UUID eventId, Instant occurredAt, UUID sessionId,
    UUID payerId, UUID tutorUserId, UUID studentId, String state, Instant reviewDeadline,
    String sessionStatus, UUID actorUserId)
    implements BaseDomainEvent {
  @Override public UUID getEventId() { return eventId; }
  @Override public Instant getOccurredAt() { return occurredAt; }
}

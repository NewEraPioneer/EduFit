package vn.edufit.scheduling.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

public record SessionRescheduleClosedEvent(UUID eventId, Instant occurredAt, UUID sessionId,
    UUID studentId, UUID proposedBy, String status) implements BaseDomainEvent {
  public UUID getEventId() { return eventId; }
  public Instant getOccurredAt() { return occurredAt; }
}

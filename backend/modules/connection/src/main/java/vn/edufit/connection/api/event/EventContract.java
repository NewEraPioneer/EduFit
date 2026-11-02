package vn.edufit.connection.api.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

final class EventContract {
  private EventContract() {}
  static void requireEnvelope(UUID eventId, UUID aggregateId, String oldStatus, String newStatus, Instant occurredAt) {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(aggregateId, "aggregateId");
    Objects.requireNonNull(newStatus, "newStatus");
    Objects.requireNonNull(occurredAt, "occurredAt");
    if (newStatus.isBlank() || newStatus.equals(oldStatus)) throw new IllegalArgumentException("A changed status is required");
  }
}

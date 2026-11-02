package vn.edufit.shared.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable audit input. Metadata is a historical snapshot, not a live profile lookup. */
public record AuditRecord(UUID actorUserId, String action, String targetType, UUID targetId,
    String reason, Instant occurredAt, Map<String, String> metadata) {
  public AuditRecord {
    Objects.requireNonNull(actorUserId);
    Objects.requireNonNull(action);
    Objects.requireNonNull(targetType);
    Objects.requireNonNull(targetId);
    Objects.requireNonNull(occurredAt);
    metadata = Map.copyOf(Objects.requireNonNull(metadata));
  }
}

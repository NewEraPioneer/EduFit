package vn.edufit.shared.audit;

import java.util.UUID;

public interface AuditSink {
  void record(UUID actorUserId, String action, String targetType, UUID targetId, String reason);

  /** Compatibility for existing sinks; persistent adapters override to retain the snapshot. */
  default void record(AuditRecord record) {
    record(record.actorUserId(), record.action(), record.targetType(), record.targetId(), record.reason());
  }
}

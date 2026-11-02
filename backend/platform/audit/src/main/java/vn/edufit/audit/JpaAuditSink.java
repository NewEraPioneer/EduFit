package vn.edufit.audit;

import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.edufit.shared.audit.AuditRecord;
import vn.edufit.shared.audit.AuditSink;

@Service
class JpaAuditSink implements AuditSink {

  private final AuditLogRepository repository;

  JpaAuditSink(AuditLogRepository repository) {
    this.repository = repository;
  }

  @Override
  public void record(UUID actorUserId, String action, String targetType, UUID targetId, String reason) {
    repository.save(new AuditLog(actorUserId, action, targetType, targetId, reason));
  }

  @Override
  public void record(AuditRecord record) {
    repository.save(new AuditLog(record));
  }
}

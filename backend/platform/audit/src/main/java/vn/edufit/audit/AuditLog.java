package vn.edufit.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edufit.shared.audit.AuditRecord;

@Entity
@Table(name = "audit_log")
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "audit_id")
  private UUID id;

  @Column(name = "actor_user_id", nullable = false)
  private UUID actorUserId;

  @Column(name = "action", nullable = false, length = 60)
  private String action;

  @Column(name = "target_type", nullable = false, length = 40)
  private String targetType;

  @Column(name = "target_id", nullable = false)
  private UUID targetId;

  @Column(name = "reason")
  private String reason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
  private Map<String, String> metadata;

  protected AuditLog() {}

  public AuditLog(UUID actor, String action, String targetType, UUID targetId, String reason) {
    this(new AuditRecord(actor, action, targetType, targetId, reason, Instant.now(), Map.of()));
  }

  public AuditLog(AuditRecord record) {
    this.actorUserId = record.actorUserId();
    this.action = record.action();
    this.targetType = record.targetType();
    this.targetId = record.targetId();
    this.reason = record.reason();
    this.createdAt = record.occurredAt();
    this.metadata = record.metadata();
  }

  public UUID getId() {
    return id;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public String getAction() {
    return action;
  }

  public String getTargetType() {
    return targetType;
  }

  public UUID getTargetId() {
    return targetId;
  }

  public String getReason() {
    return reason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Map<String, String> getMetadata() {
    return metadata != null ? metadata : Map.of();
  }
}

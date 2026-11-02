package vn.edufit.connection.infra.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import vn.edufit.connection.application.policy.ConnectionTransitions;
import java.util.UUID;

@Entity
@Table(name = "parent_student_link")
public class ParentStudentLink {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "link_id") private UUID id;
  @Column(name = "parent_user_id", nullable = false) private UUID parentUserId;
  @Column(name = "student_id", nullable = false) private UUID studentId;
  @Column(name = "status", nullable = false) private String status;
  @Column(name = "confirmed_at", nullable = false) private Instant confirmedAt;
  @Column(name = "revoked_at") private Instant revokedAt;
  @Column(name = "revoked_by") private UUID revokedBy;
  @Column(name = "revoke_reason") private String revokeReason;
  @Version @Column(name = "version", nullable = false) private Long version;

  protected ParentStudentLink() {}
  public ParentStudentLink(UUID parentUserId, UUID studentId, Instant now) {
    this.parentUserId = parentUserId;
    this.studentId = studentId;
    status = "CONFIRMED";
    confirmedAt = now;
  }
  public void revoke(UUID actor, String reason, Instant now) {
    ConnectionTransitions.requireState(status, "CONFIRMED");
    status = "REVOKED";
    revokedBy = actor;
    revokeReason = reason;
    revokedAt = now;
  }
  public UUID getId() { return id; }
  public UUID getParentUserId() { return parentUserId; }
  public UUID getStudentId() { return studentId; }
  public String getStatus() { return status; }
  public Instant getConfirmedAt() { return confirmedAt; }
  public Instant getRevokedAt() { return revokedAt; }
  public UUID getRevokedBy() { return revokedBy; }
  public String getRevokeReason() { return revokeReason; }
}

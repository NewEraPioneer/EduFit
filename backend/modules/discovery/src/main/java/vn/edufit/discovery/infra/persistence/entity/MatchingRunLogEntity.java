package vn.edufit.discovery.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matching_run_log")
public class MatchingRunLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "run_id", updatable = false, nullable = false)
  private UUID runId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "student_id", nullable = false)
  private UUID studentId;

  @Column(name = "need_id", nullable = false)
  private UUID needId;

  @Column(name = "result_count", nullable = false)
  private int resultCount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected MatchingRunLogEntity() {
    // JPA required
  }

  public MatchingRunLogEntity(UUID userId, UUID studentId, UUID needId, int resultCount) {
    this.userId = userId;
    this.studentId = studentId;
    this.needId = needId;
    this.resultCount = resultCount;
  }

  public UUID getRunId() {
    return runId;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getStudentId() {
    return studentId;
  }

  public UUID getNeedId() {
    return needId;
  }

  public int getResultCount() {
    return resultCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

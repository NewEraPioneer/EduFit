package vn.edufit.progress.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * JPA Entity ánh xạ bảng {@code milestone_assessment} — ghi nối tiếp, không cập nhật.
 */
@Entity
@Immutable
@Table(name = "milestone_assessment")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MilestoneAssessmentJpaEntity {

  @Id
  @Column(name = "assessment_id", nullable = false, updatable = false)
  private UUID assessmentId;

  @Column(name = "milestone_id", nullable = false, updatable = false)
  private UUID milestoneId;

  @Column(name = "session_id", nullable = false, updatable = false)
  private UUID sessionId;

  @Column(name = "level", nullable = false, updatable = false)
  private short level;

  @Column(name = "correct_count", updatable = false)
  private Short correctCount;

  @Column(name = "total_count", updatable = false)
  private Short totalCount;

  @Column(name = "evidence_note", length = 200, updatable = false)
  private String evidenceNote;

  @Column(name = "assessed_by", nullable = false, updatable = false)
  private UUID assessedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}

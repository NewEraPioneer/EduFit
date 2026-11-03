package vn.edufit.progress.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA Entity ánh xạ bảng {@code milestone_criterion}.
 */
@Entity
@Table(name = "milestone_criterion")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MilestoneCriterionJpaEntity {

  @Id
  @Column(name = "criterion_id", nullable = false, updatable = false)
  private UUID criterionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "milestone_id", nullable = false)
  private MilestoneJpaEntity milestone;

  @Column(name = "content", nullable = false, length = 200)
  private String content;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "is_checked", nullable = false)
  private boolean checked;

  @Column(name = "checked_at")
  private Instant checkedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}

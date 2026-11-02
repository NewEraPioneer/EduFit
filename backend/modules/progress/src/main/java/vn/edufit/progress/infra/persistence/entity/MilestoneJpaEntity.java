package vn.edufit.progress.infra.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA Entity ánh xạ bảng {@code milestone}.
 */
@Entity
@Table(name = "milestone")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MilestoneJpaEntity {

  @Id
  @Column(name = "milestone_id", nullable = false, updatable = false)
  private UUID milestoneId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "plan_id", nullable = false)
  private LearningPlanJpaEntity plan;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "description")
  private String description;

  @Column(name = "target_date")
  private LocalDate targetDate;

  @Column(name = "weight", nullable = false)
  private short weight;

  @Column(name = "status", nullable = false, length = 30)
  private String status;

  @Column(name = "achieved_date")
  private LocalDate achievedDate;

  @Column(name = "is_archived", nullable = false)
  private boolean archived;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "suggested_sessions")
  private Short suggestedSessions;

  @Column(name = "topic_id")
  private UUID topicId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Builder.Default
  @OneToMany(mappedBy = "milestone", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sortOrder ASC")
  @BatchSize(size = 100)
  private List<MilestoneCriterionJpaEntity> criteria = new ArrayList<>();

  public void addCriterion(MilestoneCriterionJpaEntity criterion) {
    criteria.add(criterion);
    criterion.setMilestone(this);
  }
}

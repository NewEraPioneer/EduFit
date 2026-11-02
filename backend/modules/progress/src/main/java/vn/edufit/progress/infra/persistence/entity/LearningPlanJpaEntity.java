package vn.edufit.progress.infra.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA Entity ánh xạ bảng {@code learning_plan}.
 */
@Entity
@Table(name = "learning_plan")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LearningPlanJpaEntity {

  @Id
  @Column(name = "plan_id", nullable = false, updatable = false)
  private UUID planId;

  @Column(name = "class_id", nullable = false, unique = true)
  private UUID classId;

  @Column(name = "summary", length = 1000)
  private String summary;

  @Version
  @Column(name = "version", nullable = false)
  private int version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Builder.Default
  @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sortOrder ASC")
  private List<MilestoneJpaEntity> milestones = new ArrayList<>();

  public void addMilestone(MilestoneJpaEntity milestone) {
    milestones.add(milestone);
    milestone.setPlan(this);
  }
}

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
import org.hibernate.annotations.BatchSize;

/** JPA Entity ánh xạ bảng {@code plan_template}; cột mốc của mẫu lưu kèm (thay cả danh sách khi sửa). */
@Entity
@Table(name = "plan_template")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PlanTemplateJpaEntity {

  @Id
  @Column(name = "template_id", nullable = false, updatable = false)
  private UUID templateId;

  @Column(name = "owner_tutor_id", updatable = false)
  private UUID ownerTutorId;

  @Column(name = "subject_id", nullable = false)
  private Integer subjectId;

  @Column(name = "education_level_id", nullable = false)
  private Integer educationLevelId;

  @Column(name = "goal_tag_id")
  private Integer goalTagId;

  @Column(name = "title", nullable = false, length = 150)
  private String title;

  @Column(name = "description", length = 1000)
  private String description;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Version
  @Column(name = "version", nullable = false)
  private int version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sortOrder ASC")
  @BatchSize(size = 100)
  @Builder.Default
  private List<PlanTemplateMilestoneJpaEntity> milestones = new ArrayList<>();
}

package vn.edufit.progress.infra.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
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

/** JPA Entity ánh xạ bảng {@code plan_template_milestone}; tiêu chí đạt là danh sách có thứ tự. */
@Entity
@Table(name = "plan_template_milestone")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PlanTemplateMilestoneJpaEntity {

  @Id
  @Column(name = "template_milestone_id", nullable = false, updatable = false)
  private UUID templateMilestoneId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "template_id", nullable = false)
  private PlanTemplateJpaEntity template;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "description", length = 1000)
  private String description;

  @Column(name = "weight", nullable = false)
  private short weight;

  @Column(name = "suggested_sessions", nullable = false)
  private short suggestedSessions;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @ElementCollection
  @CollectionTable(name = "plan_template_criterion", joinColumns = @JoinColumn(name = "template_milestone_id"))
  @OrderColumn(name = "sort_order")
  @Column(name = "content", nullable = false, length = 200)
  @BatchSize(size = 100)
  @Builder.Default
  private List<String> criteria = new ArrayList<>();
}

package vn.edufit.profile.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mục tiêu học tập trong danh mục (4 nhóm chung hoặc chứng chỉ quốc tế).
 */
@Entity
@Table(name = "goal_tag")
public class GoalTag {

  @Id
  @Column(name = "goal_tag_id")
  private Integer goalTagId;

  @Column(name = "code")
  private String code;

  @Column(name = "name")
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "requires_textbook")
  private boolean requiresTextbook;

  @Column(name = "requires_exam_province")
  private boolean requiresExamProvince;

  @Column(name = "is_premium")
  private boolean premium;

  @Column(name = "sort_order")
  private Integer sortOrder;

  @Column(name = "is_active")
  private boolean active;

  protected GoalTag() {
    // JPA required
  }

  public Integer getGoalTagId() {
    return goalTagId;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public boolean requiresTextbook() {
    return requiresTextbook;
  }

  public boolean requiresExamProvince() {
    return requiresExamProvince;
  }

  public boolean isPremium() {
    return premium;
  }

  public Integer getSortOrder() {
    return sortOrder;
  }

  public boolean isActive() {
    return active;
  }
}

package vn.edufit.profile.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Luật cho phép một mục tiêu ở tổ hợp Môn × Nhánh × Khối lớp; trackId null khi môn không chia nhánh.
 */
@Entity
@Table(name = "goal_tag_rule")
public class GoalTagRule {

  @Id
  @Column(name = "rule_id")
  private Integer ruleId;

  @Column(name = "goal_tag_id")
  private Integer goalTagId;

  @Column(name = "subject_id")
  private Integer subjectId;

  @Column(name = "track_id")
  private Integer trackId;

  @Column(name = "level_id")
  private Integer levelId;

  protected GoalTagRule() {
    // JPA required
  }

  public Integer getRuleId() {
    return ruleId;
  }

  public Integer getGoalTagId() {
    return goalTagId;
  }

  public Integer getSubjectId() {
    return subjectId;
  }

  public Integer getTrackId() {
    return trackId;
  }

  public Integer getLevelId() {
    return levelId;
  }
}

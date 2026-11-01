package vn.edufit.profile.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * Mục tiêu gia sư nhận dạy trong một gói. {@code evidenceVerified} chỉ có ý nghĩa với mục tiêu premium:
 * admin đã duyệt bằng chứng chuyên môn gắn với gói này.
 */
@Embeddable
public class OfferingGoal {

  @Column(name = "goal_tag_id", nullable = false)
  private Integer goalTagId;

  @Column(name = "evidence_verified", nullable = false)
  private boolean evidenceVerified;

  protected OfferingGoal() {
    // JPA required
  }

  public OfferingGoal(Integer goalTagId, boolean evidenceVerified) {
    this.goalTagId = goalTagId;
    this.evidenceVerified = evidenceVerified;
  }

  public Integer getGoalTagId() {
    return goalTagId;
  }

  public boolean isEvidenceVerified() {
    return evidenceVerified;
  }

  void verifyEvidence() {
    this.evidenceVerified = true;
  }

  void revokeEvidence() {
    this.evidenceVerified = false;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof OfferingGoal goal && Objects.equals(goalTagId, goal.goalTagId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(goalTagId);
  }
}

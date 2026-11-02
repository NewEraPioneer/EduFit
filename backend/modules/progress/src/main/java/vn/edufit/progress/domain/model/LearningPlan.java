package vn.edufit.progress.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import vn.edufit.progress.domain.policy.ProgressCalculator;
import vn.edufit.progress.domain.policy.ProgressCalculator.MilestoneWeight;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Aggregate Root đại diện cho Kế hoạch học tập (Learning Plan).
 * Java thuần túy (POJO) - không phụ thuộc Spring Boot hay Hibernate.
 */
public class LearningPlan {

  private final UUID planId;
  private final UUID classId;
  private String summary;
  private int version;
  private final Instant createdAt;
  private Instant updatedAt;
  private final List<Milestone> milestones;

  public LearningPlan(
      UUID planId,
      UUID classId,
      String summary,
      int version,
      Instant createdAt,
      Instant updatedAt,
      List<Milestone> milestones
  ) {
    if (classId == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "classId không được để trống");
    }
    validateSummary(summary);
    this.planId = planId != null ? planId : UUID.randomUUID();
    this.classId = classId;
    this.summary = summary != null ? summary.trim() : null;
    this.version = version;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
    this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    this.milestones = milestones != null ? new ArrayList<>(milestones) : new ArrayList<>();
  }

  public static LearningPlan createNew(UUID classId, String summary, List<Milestone> initialMilestones) {
    return createNew(classId, summary, initialMilestones, Instant.now());
  }

  public static LearningPlan createNew(UUID classId, String summary, List<Milestone> initialMilestones, Instant now) {
    UUID id = UUID.randomUUID();
    Instant creationTime = now != null ? now : Instant.now();
    List<Milestone> attached = new ArrayList<>();
    if (initialMilestones != null) {
      for (Milestone m : initialMilestones) {
        m.setPlanId(id);
        attached.add(m);
      }
    }

    LearningPlan plan = new LearningPlan(id, classId, summary, 0, creationTime, creationTime, attached);
    plan.validateActiveMilestonesCount();
    return plan;
  }

  public void updateSummary(String newSummary) {
    updateSummary(newSummary, Instant.now());
  }

  public void updateSummary(String newSummary, Instant now) {
    validateSummary(newSummary);
    this.summary = newSummary != null ? newSummary.trim() : null;
    this.updatedAt = now != null ? now : Instant.now();
  }

  public void validateActiveMilestonesCount() {
    long activeCount = countActiveMilestones();
    if (activeCount < 1 || activeCount > 20) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Kế hoạch học tập phải có từ 1 đến 20 cột mốc đang hoạt động"
      );
    }
  }

  public long countActiveMilestones() {
    return this.milestones.stream().filter(m -> !m.isArchived()).count();
  }

  public List<Milestone> getActiveMilestones() {
    return this.milestones.stream().filter(m -> !m.isArchived()).toList();
  }

  /**
   * Tính toán phần trăm tiến độ bằng ProgressCalculator.
   *
   * @return % tiến độ (0–100), hoặc null nếu không có milestone active.
   */
  public Integer calculateProgressPercentage() {
    List<MilestoneWeight> weights = this.milestones.stream()
        .map(m -> new MilestoneWeight(
            m.getWeight(),
            m.getStatus() == MilestoneStatus.ACHIEVED,
            m.isArchived()
        ))
        .toList();
    return ProgressCalculator.calculate(weights);
  }

  private static void validateSummary(String summary) {
    if (summary != null && summary.trim().length() > 1000) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Tóm tắt kế hoạch học tập tối đa 1000 ký tự"
      );
    }
  }

  public UUID getPlanId() { return planId; }
  public UUID getClassId() { return classId; }
  public String getSummary() { return summary; }
  public int getVersion() { return version; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public List<Milestone> getMilestones() { return Collections.unmodifiableList(milestones); }
}

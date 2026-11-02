package vn.edufit.progress.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.edufit.progress.domain.policy.ProgressCalculator;
import vn.edufit.progress.domain.policy.RoadmapShapePolicy;
import vn.edufit.progress.domain.policy.ProgressCalculator.MilestoneWeight;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Aggregate Root đại diện cho Kế hoạch học tập (Learning Plan).
 * Java thuần túy (POJO) - không phụ thuộc Spring Boot hay Hibernate.
 */
public class LearningPlan {

  /** Múi giờ nghiệp vụ dùng để quy đổi thời điểm sang ngày. */
  public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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
    plan.validateShape();
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

  /**
   * Hình dạng chuẩn khi lập hoặc sửa lộ trình ({@link RoadmapShapePolicy}): 3–8 cột mốc đang hoạt động, cột mốc cuối
   * có hạn. Lộ trình cũ chỉ phải tuân thủ khi được sửa.
   */
  public void validateShape() {
    RoadmapShapePolicy.requireMilestoneCount(countActiveMilestones());
    RoadmapShapePolicy.requireFinalTargetDate(orderedActiveMilestones().stream().map(Milestone::getTargetDate).toList());
  }

  /** Ngày lập lộ trình theo giờ Việt Nam, làm mốc 0 khi nội suy hạn các mốc giữa. */
  public LocalDate startDate() {
    return createdAt.atZone(BUSINESS_ZONE).toLocalDate();
  }

  /** Hạn hiệu lực của từng cột mốc đang hoạt động: hạn gia sư đặt hoặc ngày nội suy (không có thì không có khóa). */
  public Map<UUID, RoadmapShapePolicy.ExpectedDate> expectedTargetDates() {
    List<Milestone> ordered = orderedActiveMilestones();
    List<RoadmapShapePolicy.ExpectedDate> expected =
        RoadmapShapePolicy.interpolate(startDate(), ordered.stream().map(Milestone::getTargetDate).toList());
    Map<UUID, RoadmapShapePolicy.ExpectedDate> result = new HashMap<>();
    for (int i = 0; i < ordered.size(); i++) {
      if (expected.get(i) != null) result.put(ordered.get(i).getMilestoneId(), expected.get(i));
    }
    return result;
  }

  public List<Milestone> orderedActiveMilestones() {
    return getActiveMilestones().stream().sorted(Comparator.comparingInt(Milestone::getSortOrder)).toList();
  }

  public long countActiveMilestones() {
    return this.milestones.stream().filter(m -> !m.isArchived()).count();
  }

  public List<Milestone> getActiveMilestones() {
    return this.milestones.stream().filter(m -> !m.isArchived()).toList();
  }

  /**
   * Tính phần trăm tiến độ có trọng số hiện tại (cột mốc đang học tính theo tiêu chí đã tick).
   *
   * @return % tiến độ (0–100), hoặc null nếu không có milestone active.
   */
  public Integer calculateProgressPercentage() {
    return ProgressCalculator.calculate(this.milestones.stream()
        .map(m -> toWeight(m, m.completionShare()))
        .toList());
  }

  /**
   * Dựng lại phần trăm tiến độ tại một thời điểm trong quá khứ từ {@code achievedDate} và thời điểm tick tiêu chí.
   * Danh sách cột mốc dùng là danh sách hiện tại; việc điều chỉnh lộ trình trong kỳ được báo riêng qua plan_history.
   */
  public Integer calculateProgressPercentageAt(Instant cutoff, LocalDate cutoffDate) {
    return ProgressCalculator.calculate(this.milestones.stream()
        .map(m -> toWeight(m, m.completionShareAt(cutoff, cutoffDate)))
        .toList());
  }

  /** Cột mốc đang được tập trung: cột mốc IN_PROGRESS đầu tiên, nếu không có thì cột mốc chưa đạt đầu tiên. */
  public Optional<Milestone> findFocusMilestone() {
    List<Milestone> ordered = orderedActiveMilestones();
    return ordered.stream()
        .filter(m -> m.getStatus() == MilestoneStatus.IN_PROGRESS)
        .findFirst()
        .or(() -> ordered.stream().filter(m -> m.getStatus() == MilestoneStatus.NOT_STARTED).findFirst());
  }

  public Optional<Milestone> findActiveMilestone(UUID milestoneId) {
    return this.milestones.stream()
        .filter(m -> !m.isArchived() && m.getMilestoneId().equals(milestoneId))
        .findFirst();
  }

  private static MilestoneWeight toWeight(Milestone m, Milestone.CompletionShare share) {
    return new MilestoneWeight(m.getWeight(), share.completed(), share.total(), m.isArchived());
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

package vn.edufit.progress.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Entity miền đại diện cho Cột mốc học tập (Milestone).
 * Java thuần túy (POJO) - không phụ thuộc Spring hay JPA.
 */
public class Milestone {

  private final UUID milestoneId;
  private UUID planId;
  private String title;
  private String description;
  private LocalDate targetDate;
  private int weight;
  private MilestoneStatus status;
  private LocalDate achievedDate;
  private boolean archived;
  private int sortOrder;
  private final Instant createdAt;

  public Milestone(
      UUID milestoneId,
      UUID planId,
      String title,
      String description,
      LocalDate targetDate,
      int weight,
      MilestoneStatus status,
      LocalDate achievedDate,
      boolean archived,
      int sortOrder,
      Instant createdAt
  ) {
    validateTitle(title);
    validateWeight(weight);
    this.milestoneId = milestoneId != null ? milestoneId : UUID.randomUUID();
    this.planId = planId;
    this.title = title.trim();
    this.description = description;
    this.targetDate = targetDate;
    this.weight = weight;
    this.status = status != null ? status : MilestoneStatus.NOT_STARTED;
    this.achievedDate = achievedDate;
    this.archived = archived;
    this.sortOrder = sortOrder;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
  }

  public static Milestone createNew(
      UUID planId,
      String title,
      String description,
      LocalDate targetDate,
      int weight,
      int sortOrder,
      LocalDate today
  ) {
    if (targetDate != null && targetDate.isBefore(today)) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Hạn hoàn thành cột mốc không được nằm trong quá khứ"
      );
    }
    return new Milestone(
        UUID.randomUUID(),
        planId,
        title,
        description,
        targetDate,
        weight,
        MilestoneStatus.NOT_STARTED,
        null,
        false,
        sortOrder,
        Instant.now()
    );
  }

  public void updateDetails(
      String title,
      String description,
      LocalDate newTargetDate,
      int weight,
      int sortOrder,
      LocalDate today
  ) {
    validateTitle(title);
    validateWeight(weight);

    // Nếu targetDate thay đổi và khác targetDate cũ thì không được nằm trong quá khứ
    if (!Objects.equals(this.targetDate, newTargetDate) && newTargetDate != null && newTargetDate.isBefore(today)) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Hạn hoàn thành cột mốc cập nhật không được nằm trong quá khứ"
      );
    }

    this.title = title.trim();
    this.description = description;
    this.targetDate = newTargetDate;
    this.weight = weight;
    this.sortOrder = sortOrder;
  }

  public void updateStatus(MilestoneStatus newStatus, LocalDate specifiedAchievedDate, LocalDate today) {
    if (newStatus == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Trạng thái cột mốc không được để trống");
    }

    if (newStatus == MilestoneStatus.ACHIEVED) {
      LocalDate finalAchievedDate = specifiedAchievedDate != null ? specifiedAchievedDate : today;
      if (finalAchievedDate.isAfter(today)) {
        throw new InvalidOperationException(
            ErrorCode.VALIDATION_FAILED,
            "Ngày đạt cột mốc không được nằm trong tương lai"
        );
      }
      this.achievedDate = finalAchievedDate;
    } else {
      // Khi rời khỏi ACHIEVED, xóa achievedDate
      this.achievedDate = null;
    }
    this.status = newStatus;
  }

  public void archive() {
    this.archived = true;
  }

  /**
   * Kiểm tra xem milestone này đã từng có tiến triển chưa (IN_PROGRESS hoặc ACHIEVED).
   * Dùng để quyết định: xóa thật nếu chưa từng tiến triển, chuyển isArchived=true nếu đã có tiến triển.
   */
  public boolean hasProgressed() {
    return this.status != MilestoneStatus.NOT_STARTED || this.achievedDate != null;
  }

  private static void validateTitle(String title) {
    if (title == null || title.isBlank()) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Tiêu đề cột mốc không được để trống");
    }
    if (title.trim().length() > 200) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Tiêu đề cột mốc tối đa 200 ký tự");
    }
  }

  private static void validateWeight(int weight) {
    if (weight < 1 || weight > 5) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Trọng số cột mốc phải từ 1 đến 5");
    }
  }

  public UUID getMilestoneId() { return milestoneId; }
  public UUID getPlanId() { return planId; }
  public void setPlanId(UUID planId) { this.planId = planId; }
  public String getTitle() { return title; }
  public String getDescription() { return description; }
  public LocalDate getTargetDate() { return targetDate; }
  public int getWeight() { return weight; }
  public MilestoneStatus getStatus() { return status; }
  public LocalDate getAchievedDate() { return achievedDate; }
  public boolean isArchived() { return archived; }
  public int getSortOrder() { return sortOrder; }
  public Instant getCreatedAt() { return createdAt; }
}

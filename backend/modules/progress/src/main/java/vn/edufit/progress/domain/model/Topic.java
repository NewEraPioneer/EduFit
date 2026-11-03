package vn.edufit.progress.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Chủ đề chuẩn theo môn × khối do admin quản lý (mục 3.16, Giai đoạn 3). Là trục của biểu đồ radar năng lực;
 * cột mốc gắn tùy chọn một chủ đề. Chủ đề đã có cột mốc tham chiếu chỉ được lưu trữ, không xóa.
 */
public class Topic {

  public static final int MAX_NAME_LENGTH = 120;

  private final UUID topicId;
  private final int subjectId;
  private final int educationLevelId;
  private String name;
  private int sortOrder;
  private boolean archived;
  private final UUID createdBy;
  private final int version;
  private final Instant createdAt;
  private Instant updatedAt;

  public Topic(
      UUID topicId,
      int subjectId,
      int educationLevelId,
      String name,
      int sortOrder,
      boolean archived,
      UUID createdBy,
      int version,
      Instant createdAt,
      Instant updatedAt
  ) {
    this.topicId = topicId != null ? topicId : UUID.randomUUID();
    this.subjectId = subjectId;
    this.educationLevelId = educationLevelId;
    this.name = validName(name);
    this.sortOrder = sortOrder;
    this.archived = archived;
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = updatedAt != null ? updatedAt : createdAt;
  }

  public static Topic createNew(int subjectId, int educationLevelId, String name, int sortOrder, UUID createdBy, Instant now) {
    return new Topic(UUID.randomUUID(), subjectId, educationLevelId, name, sortOrder, false, createdBy, 0, now, now);
  }

  public void revise(String newName, int newSortOrder, boolean newArchived, Instant now) {
    this.name = validName(newName);
    this.sortOrder = newSortOrder;
    this.archived = newArchived;
    this.updatedAt = now;
  }

  /** Chủ đề dùng được cho lớp học có môn và khối này. */
  public boolean belongsTo(Integer subject, Integer level) {
    return subject != null && level != null && subjectId == subject && educationLevelId == level;
  }

  private static String validName(String name) {
    if (name == null || name.isBlank()) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Tên chủ đề không được để trống");
    }
    if (name.trim().length() > MAX_NAME_LENGTH) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Tên chủ đề tối đa " + MAX_NAME_LENGTH + " ký tự");
    }
    return name.trim();
  }

  public UUID getTopicId() { return topicId; }
  public int getSubjectId() { return subjectId; }
  public int getEducationLevelId() { return educationLevelId; }
  public String getName() { return name; }
  public int getSortOrder() { return sortOrder; }
  public boolean isArchived() { return archived; }
  public UUID getCreatedBy() { return createdBy; }
  public int getVersion() { return version; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}

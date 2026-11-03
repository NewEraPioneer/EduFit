package vn.edufit.progress.domain.model;

/**
 * Chip việc về nhà (bắt buộc) trong phản hồi 10 giây sau buổi học.
 */
public enum HomeworkStatus {
  /** Có giao bài về nhà trong buổi này. */
  ASSIGNED,
  /** Không có bài về nhà. */
  NONE,
  /** Không giao thêm, bài đã giao từ trước vẫn đang làm. */
  PREVIOUSLY_ASSIGNED;

  public boolean hasHomework() {
    return this != NONE;
  }
}

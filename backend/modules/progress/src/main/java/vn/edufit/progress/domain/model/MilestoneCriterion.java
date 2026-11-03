package vn.edufit.progress.domain.model;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Một tiêu chí đạt (gạch đầu dòng) của cột mốc. Cột mốc chỉ được chốt ACHIEVED khi mọi tiêu chí đã tick.
 */
public class MilestoneCriterion {

  public static final int MAX_CONTENT_LENGTH = 200;

  private final UUID criterionId;
  private String content;
  private int sortOrder;
  private boolean checked;
  private Instant checkedAt;
  private final Instant createdAt;

  public MilestoneCriterion(
      UUID criterionId,
      String content,
      int sortOrder,
      boolean checked,
      Instant checkedAt,
      Instant createdAt
  ) {
    this.criterionId = criterionId != null ? criterionId : UUID.randomUUID();
    this.content = normalize(content);
    this.sortOrder = sortOrder;
    this.checked = checked;
    this.checkedAt = checked ? checkedAt : null;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
  }

  public static MilestoneCriterion createNew(String content, int sortOrder, Instant now) {
    return new MilestoneCriterion(UUID.randomUUID(), content, sortOrder, false, null, now);
  }

  void rename(String newContent, int newSortOrder) {
    this.content = normalize(newContent);
    this.sortOrder = newSortOrder;
  }

  void setChecked(boolean value, Instant now) {
    if (value == this.checked) return;
    this.checked = value;
    this.checkedAt = value ? now : null;
  }

  private static String normalize(String content) {
    if (content == null || content.isBlank()) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Nội dung tiêu chí đạt không được để trống");
    }
    String trimmed = content.trim();
    if (trimmed.length() > MAX_CONTENT_LENGTH) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED, "Nội dung tiêu chí đạt tối đa " + MAX_CONTENT_LENGTH + " ký tự");
    }
    return trimmed;
  }

  public UUID getCriterionId() { return criterionId; }
  public String getContent() { return content; }
  public int getSortOrder() { return sortOrder; }
  public boolean isChecked() { return checked; }
  public Instant getCheckedAt() { return checkedAt; }
  public Instant getCreatedAt() { return createdAt; }
}

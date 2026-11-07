package vn.edufit.progress.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import vn.edufit.progress.domain.policy.RoadmapShapePolicy;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Mẫu lộ trình theo môn × khối × mục tiêu (mục 3.4). Mẫu hệ thống ({@code ownerTutorId == null}) do admin quản lý;
 * mẫu riêng thuộc về một gia sư. Mẫu không có ngày: gia sư đặt hạn khi áp dụng vào lớp.
 */
public class PlanTemplate {

  public static final int MAX_TITLE_LENGTH = 150;
  public static final int MAX_DESCRIPTION_LENGTH = 1000;

  private final UUID templateId;
  private final UUID ownerTutorId;
  private Integer subjectId;
  private Integer educationLevelId;
  private Integer goalTagId;
  private String title;
  private String description;
  private List<TemplateMilestone> milestones;
  private final UUID createdBy;
  private final int version;
  private final Instant createdAt;
  private Instant updatedAt;

  /** Một cột mốc của mẫu: 2–6 buổi gợi ý, 1–4 tiêu chí đạt. */
  public record TemplateMilestone(
      String title, String description, int weight, int suggestedSessions, List<String> criteria) {

    public TemplateMilestone {
      if (title == null || title.isBlank() || title.trim().length() > 200) {
        throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Tiêu đề cột mốc của mẫu từ 1 đến 200 ký tự");
      }
      if (description != null && description.trim().length() > MAX_DESCRIPTION_LENGTH) {
        throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Mô tả cột mốc tối đa 1000 ký tự");
      }
      if (weight < 1 || weight > 5) {
        throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Trọng số cột mốc từ 1 đến 5");
      }
      RoadmapShapePolicy.requireSuggestedSessions(suggestedSessions);
      List<String> cleaned = criteria == null ? List.of()
          : criteria.stream().filter(c -> c != null && !c.isBlank()).map(String::trim).toList();
      if (cleaned.size() < Milestone.MIN_CRITERIA || cleaned.size() > Milestone.MAX_CRITERIA
          || cleaned.size() != (criteria == null ? 0 : criteria.size())) {
        throw new InvalidOperationException(
            ErrorCode.VALIDATION_FAILED, "Mỗi cột mốc của mẫu cần 1–4 tiêu chí đạt, không để trống");
      }
      if (cleaned.stream().anyMatch(c -> c.length() > 200)) {
        throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Nội dung tiêu chí đạt tối đa 200 ký tự");
      }
      title = title.trim();
      description = description != null && !description.isBlank() ? description.trim() : null;
      criteria = List.copyOf(cleaned);
    }
  }

  public PlanTemplate(
      UUID templateId,
      UUID ownerTutorId,
      Integer subjectId,
      Integer educationLevelId,
      Integer goalTagId,
      String title,
      String description,
      List<TemplateMilestone> milestones,
      UUID createdBy,
      int version,
      Instant createdAt,
      Instant updatedAt
  ) {
    if (createdBy == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Mẫu lộ trình thiếu người tạo");
    }
    this.templateId = templateId != null ? templateId : UUID.randomUUID();
    this.ownerTutorId = ownerTutorId;
    this.createdBy = createdBy;
    this.version = version;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
    applyContent(subjectId, educationLevelId, goalTagId, title, description, milestones);
    this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
  }

  /** Sửa toàn bộ nội dung mẫu (mẫu nhỏ nên thay cả danh sách cột mốc). */
  public void revise(
      Integer subjectId, Integer educationLevelId, Integer goalTagId,
      String title, String description, List<TemplateMilestone> milestones, Instant now) {
    applyContent(subjectId, educationLevelId, goalTagId, title, description, milestones);
    this.updatedAt = now;
  }

  private void applyContent(
      Integer subjectId, Integer educationLevelId, Integer goalTagId,
      String title, String description, List<TemplateMilestone> milestones) {
    if (subjectId == null || educationLevelId == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Mẫu lộ trình cần môn học và khối lớp");
    }
    if (title == null || title.isBlank() || title.trim().length() > MAX_TITLE_LENGTH) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED, "Tên mẫu lộ trình từ 1 đến " + MAX_TITLE_LENGTH + " ký tự");
    }
    if (description != null && description.trim().length() > MAX_DESCRIPTION_LENGTH) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Mô tả mẫu lộ trình tối đa 1000 ký tự");
    }
    List<TemplateMilestone> items = milestones != null ? List.copyOf(milestones) : List.of();
    RoadmapShapePolicy.requireMilestoneCount(items.size());
    this.subjectId = subjectId;
    this.educationLevelId = educationLevelId;
    this.goalTagId = goalTagId;
    this.title = title.trim();
    this.description = description != null && !description.isBlank() ? description.trim() : null;
    this.milestones = items;
  }

  public boolean isSystem() { return ownerTutorId == null; }

  /**
   * Mẫu phù hợp lớp có môn/khối/mục tiêu này: trùng môn và khối; mẫu không gắn mục tiêu dùng được cho mọi mục tiêu.
   */
  public boolean matches(Integer subjectId, Integer educationLevelId, Integer goalTagId) {
    return this.subjectId.equals(subjectId) && this.educationLevelId.equals(educationLevelId)
        && (this.goalTagId == null || this.goalTagId.equals(goalTagId));
  }

  public UUID getTemplateId() { return templateId; }
  public UUID getOwnerTutorId() { return ownerTutorId; }
  public Integer getSubjectId() { return subjectId; }
  public Integer getEducationLevelId() { return educationLevelId; }
  public Integer getGoalTagId() { return goalTagId; }
  public String getTitle() { return title; }
  public String getDescription() { return description; }
  public List<TemplateMilestone> getMilestones() { return Collections.unmodifiableList(milestones); }
  public UUID getCreatedBy() { return createdBy; }
  public int getVersion() { return version; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}

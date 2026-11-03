package vn.edufit.progress.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.edufit.progress.domain.model.PlanTemplate;

/** DTO của mẫu lộ trình. */
public final class PlanTemplateViews {

  private PlanTemplateViews() {}

  /**
   * @param system mẫu hệ thống (admin quản lý); {@code mine}: mẫu riêng của gia sư đang xem.
   */
  public record PlanTemplateView(
      UUID templateId,
      boolean system,
      boolean mine,
      Integer subjectId,
      Integer educationLevelId,
      Integer goalTagId,
      String title,
      String description,
      List<TemplateMilestoneView> milestones,
      int version,
      Instant updatedAt
  ) {
    public static PlanTemplateView from(PlanTemplate t, UUID viewerTutorId) {
      return new PlanTemplateView(
          t.getTemplateId(),
          t.isSystem(),
          !t.isSystem() && t.getOwnerTutorId().equals(viewerTutorId),
          t.getSubjectId(),
          t.getEducationLevelId(),
          t.getGoalTagId(),
          t.getTitle(),
          t.getDescription(),
          t.getMilestones().stream()
              .map(m -> new TemplateMilestoneView(m.title(), m.description(), m.weight(), m.suggestedSessions(), m.criteria()))
              .toList(),
          t.getVersion(),
          t.getUpdatedAt());
    }
  }

  public record TemplateMilestoneView(
      String title, String description, int weight, int suggestedSessions, List<String> criteria) {}

  /**
   * Nội dung mẫu khi tạo/sửa. Danh mục lấy từ {@code classId} (lưu lộ trình của lớp thành mẫu riêng) hoặc nhập trực
   * tiếp {@code subjectId}/{@code educationLevelId}/{@code goalTagId} (admin).
   */
  public record SavePlanTemplateCommand(
      UUID classId,
      Integer subjectId,
      Integer educationLevelId,
      Integer goalTagId,
      String title,
      String description,
      List<TemplateMilestoneView> milestones,
      Integer version
  ) {}
}

package vn.edufit.progress.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import vn.edufit.progress.application.dto.PlanTemplateViews.SavePlanTemplateCommand;
import vn.edufit.progress.application.dto.PlanTemplateViews.TemplateMilestoneView;
import vn.edufit.progress.domain.policy.RoadmapShapePolicy;

/**
 * Tạo/sửa mẫu lộ trình: POST/PUT /api/v1/progress/templates. Danh mục lấy từ {@code classId} (gia sư lưu lộ trình
 * của lớp thành mẫu riêng) hoặc nhập trực tiếp môn/khối/mục tiêu (admin). {@code version} bắt buộc khi sửa.
 */
public record SavePlanTemplateRequest(
    UUID classId,
    Integer subjectId,
    Integer educationLevelId,
    Integer goalTagId,

    @NotBlank(message = "Tên mẫu lộ trình không được để trống")
    @Size(max = 150, message = "Tên mẫu lộ trình tối đa 150 ký tự")
    String title,

    @Size(max = 1000, message = "Mô tả mẫu lộ trình tối đa 1000 ký tự")
    String description,

    @NotNull(message = "Mẫu lộ trình cần danh sách cột mốc")
    @Size(min = RoadmapShapePolicy.MIN_MILESTONES, max = RoadmapShapePolicy.MAX_MILESTONES,
        message = "Mẫu lộ trình phải có từ 3 đến 8 cột mốc")
    @Valid
    List<Item> milestones,

    Integer version
) {
  public record Item(
      @NotBlank(message = "Tiêu đề cột mốc không được để trống")
      @Size(max = 200, message = "Tiêu đề cột mốc tối đa 200 ký tự")
      String title,

      @Size(max = 1000, message = "Mô tả cột mốc tối đa 1000 ký tự")
      String description,

      @NotNull(message = "Cần trọng số cột mốc")
      @Min(value = 1, message = "Trọng số từ 1 đến 5")
      @Max(value = 5, message = "Trọng số từ 1 đến 5")
      Integer weight,

      @NotNull(message = "Cần số buổi gợi ý cho cột mốc")
      @Min(value = RoadmapShapePolicy.MIN_SESSIONS, message = "Mỗi cột mốc gợi ý từ 2 đến 6 buổi")
      @Max(value = RoadmapShapePolicy.MAX_SESSIONS, message = "Mỗi cột mốc gợi ý từ 2 đến 6 buổi")
      Integer suggestedSessions,

      @NotNull(message = "Mỗi cột mốc cần 1–4 tiêu chí đạt")
      @Size(min = 1, max = 4, message = "Mỗi cột mốc cần 1–4 tiêu chí đạt")
      List<String> criteria
  ) {}

  public SavePlanTemplateCommand toCommand() {
    return new SavePlanTemplateCommand(
        classId, subjectId, educationLevelId, goalTagId, title, description,
        milestones.stream()
            .map(m -> new TemplateMilestoneView(m.title(), m.description(), m.weight(), m.suggestedSessions(), m.criteria()))
            .toList(),
        version);
  }
}

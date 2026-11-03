package vn.edufit.progress.application.dto;

import java.util.List;
import java.util.UUID;

/**
 * Bản nháp lộ trình bằng AI (Giai đoạn 3). Không có bản nháp ({@code milestones} rỗng) thì {@code unavailableReason}
 * giải thích lý do và gợi ý dùng mẫu lộ trình. Bản nháp không có ngày — gia sư đặt hạn trước khi lưu.
 */
public record PlanDraftView(List<DraftMilestoneView> milestones, String unavailableReason, int remainingQuota) {

  /** {@code topicId} chỉ có khi AI chọn đúng tên một chủ đề chuẩn của môn × khối. */
  public record DraftMilestoneView(
      String title,
      String description,
      int weight,
      int suggestedSessions,
      List<String> criteria,
      UUID topicId
  ) {}

  public static PlanDraftView unavailable(String reason, int remainingQuota) {
    return new PlanDraftView(List.of(), reason, remainingQuota);
  }
}

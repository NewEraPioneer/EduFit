package vn.edufit.progress.application.dto;

import java.util.List;
import java.util.UUID;

/**
 * Cột mốc hiệu lực của một buổi học để hiển thị trên lịch.
 *
 * @param linkType {@code ACTUAL} nếu buổi đã được phản hồi (thực tế thắng dự kiến), ngược lại {@code PLANNED}.
 */
public record SessionMilestonesView(UUID sessionId, String linkType, List<MilestoneRef> milestones) {

  public record MilestoneRef(UUID milestoneId, String title) {}
}

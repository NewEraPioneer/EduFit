package vn.edufit.progress.domain.model;

import java.util.UUID;

/**
 * Liên kết buổi học ↔ cột mốc (mục 3.5).
 * {@code PLANNED}: gia sư gắn tùy chọn khi xếp lịch. {@code ACTUAL}: ghi khi phản hồi sau buổi, luôn thắng PLANNED.
 */
public record SessionMilestoneLink(UUID sessionId, UUID milestoneId, Type type) {

  public enum Type { PLANNED, ACTUAL }
}

package vn.edufit.scheduling.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO an toàn đại diện cho một buổi học trên Timeline (Dùng cho Progress Notes/Sessions Timeline).
 *
 * @param sessionId Mã buổi học.
 * @param classId   Mã lớp học.
 * @param startAt   Thời điểm bắt đầu buổi học (UTC).
 * @param endAt     Thời điểm kết thúc buổi học (UTC).
 * @param status    Trạng thái buổi học (SCHEDULED, COMPLETED, ABSENT, CANCELLED).
 */
public record SessionTimelineView(
    UUID sessionId,
    UUID classId,
    Instant startAt,
    Instant endAt,
    String status,
    Instant outcomeRecordedAt
) {
  public SessionTimelineView(UUID sessionId, UUID classId, Instant startAt, Instant endAt, String status) {
    this(sessionId, classId, startAt, endAt, status, null);
  }
}

package vn.edufit.scheduling.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO an toàn mô tả một buổi học dưới góc nhìn chuyên cần (dùng cho thẻ "Chuyên cần & buổi tới" của Progress).
 *
 * @param sessionId   Mã buổi học.
 * @param startAt     Thời điểm bắt đầu (UTC).
 * @param status      Trạng thái buổi học (SCHEDULED, COMPLETED, ABSENT, CANCELLED, ...).
 * @param cancelledBy user id của người hủy (null nếu không bị hủy).
 * @param lateCancel  Hủy trễ dưới 12 giờ trước giờ học (BR-46).
 */
public record SessionAttendanceView(
    UUID sessionId,
    Instant startAt,
    String status,
    UUID cancelledBy,
    boolean lateCancel
) {}

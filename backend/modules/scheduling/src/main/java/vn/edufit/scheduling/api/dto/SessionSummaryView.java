package vn.edufit.scheduling.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Public DTO chứa tóm tắt thông tin buổi học chia sẻ an toàn giữa các module trong cùng JVM (ADR-002).
 *
 * <p>Theo quy chuẩn kiến trúc:
 * <ul>
 *   <li>Sử dụng Java Record bất biến (Immutable).</li>
 *   <li>Không để lộ JPA Entity hay các trường nội bộ nhạy cảm ra ngoài ranh giới module.</li>
 * </ul>
 *
 * @param sessionId    Mã định danh duy nhất của buổi học.
 * @param classId      Mã lớp học (quan hệ 1-1 giữa Gia sư và Học sinh).
 * @param tutorId      Mã định danh hồ sơ Gia sư.
 * @param studentId    Mã định danh hồ sơ Học sinh.
 * @param startAt      Mốc thời gian bắt đầu buổi học (UTC).
 * @param endAt        Mốc thời gian kết thúc buổi học (UTC).
 * @param mode         Hình thức học: ONLINE hoặc OFFLINE.
 * @param status       Trạng thái hiện tại của buổi học.
 * @param isLateCancel Cờ đánh dấu có bị hủy trễ dưới 12h hay không (BR-46).
 * @param createdAt    Thời điểm đề xuất buổi học được tạo (UTC).
 */
public record SessionSummaryView(
    UUID sessionId,
    UUID classId,
    UUID tutorId,
    UUID studentId,
    Instant startAt,
    Instant endAt,
    String mode,
    String status,
    boolean isLateCancel,
    Instant createdAt,
    Instant outcomeRecordedAt
) {
  public SessionSummaryView(UUID sessionId, UUID classId, UUID tutorId, UUID studentId,
      Instant startAt, Instant endAt, String mode, String status, boolean isLateCancel, Instant createdAt) {
    this(sessionId, classId, tutorId, studentId, startAt, endAt, mode, status, isLateCancel, createdAt, null);
  }
}

package vn.edufit.scheduling.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Sự kiện miền phát sinh khi một buổi học đã kết thúc nhưng Gia sư chưa ghi nhận kết quả (FR-20).
 *
 * <p>Mục đích thiết kế & Liên kết yêu cầu chức năng (FR-20):
 * <ul>
 *   <li><b>FR-20 (Ghi nhận kết quả buổi học trong 24 giờ):</b> Gia sư có nghĩa vụ ghi nhận kết quả
 *       buổi học ({@code COMPLETED} hoặc {@code ABSENT}) trong vòng 24 giờ sau khi buổi học kết thúc.
 *       Nếu quá thời hạn hoặc sau khi kết thúc ca học mà trạng thái vẫn là {@code SCHEDULED},
 *       hệ thống sẽ gửi thông báo nhắc nhở Gia sư.</li>
 *   <li><b>Module lắng nghe:</b> {@code notification} (Gửi thông báo Push & Email nhắc nhở Gia sư).</li>
 * </ul>
 *
 * @param eventId Mã định danh duy nhất của sự kiện
 * @param occurredAt Thời điểm sự kiện phát sinh
 * @param sessionId Mã định danh buổi học cần ghi nhận kết quả
 * @param classId Mã lớp học
 * @param tutorId Mã Gia sư phụ trách
 * @param endAt Thời điểm buổi học đã kết thúc
 */
public record OverdueOutcomeReminderEvent(
    UUID eventId,
    Instant occurredAt,
    UUID sessionId,
    UUID classId,
    UUID tutorId,
    Instant endAt
) implements BaseDomainEvent {

  /**
   * Factory method tiện ích tạo nhanh một sự kiện nhắc nhở Gia sư ghi nhận kết quả buổi học.
   *
   * @param sessionId Mã buổi học
   * @param classId Mã lớp học
   * @param tutorId Mã Gia sư
   * @param endAt Thời điểm kết thúc ca học
   * @return Instance của {@link OverdueOutcomeReminderEvent}
   */
  public static OverdueOutcomeReminderEvent of(
      UUID sessionId,
      UUID classId,
      UUID tutorId,
      Instant endAt
  ) {
    return new OverdueOutcomeReminderEvent(
        UUID.randomUUID(),
        Instant.now(),
        sessionId,
        classId,
        tutorId,
        endAt
    );
  }

  @Override
  public UUID getEventId() {
    return eventId;
  }

  @Override
  public Instant getOccurredAt() {
    return occurredAt;
  }
}

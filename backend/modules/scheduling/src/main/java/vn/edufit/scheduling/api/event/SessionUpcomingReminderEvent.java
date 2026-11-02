package vn.edufit.scheduling.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Sự kiện miền phát sinh khi một buổi học sắp diễn ra (trước 24 giờ hoặc 2 giờ) bởi tác vụ ngầm.
 *
 * <p>Mục đích thiết kế & Liên kết yêu cầu chức năng (FR-31):
 * <ul>
 *   <li><b>FR-31 (Nhắc nhở buổi học):</b> Tự động gửi thông báo nhắc nhở lịch học cho cả Gia sư và
 *       Học sinh tại 2 mốc thời gian quan trọng: trước 24 giờ (để sắp xếp kế hoạch) và trước 2 giờ (để chuẩn bị thiết bị/đến điểm hẹn).</li>
 *   <li><b>Kiến trúc hướng sự kiện (EDA):</b> Module {@code scheduling} không trực tiếp phụ thuộc vào cơ chế
 *       gửi thông báo (Firebase, FCM, Email, SMS). Sự kiện này được publish nội bộ qua Spring ApplicationEventPublisher
 *       để module {@code notification} tiêu thụ bất đồng bộ.</li>
 * </ul>
 *
 * @param eventId Mã định danh duy nhất của sự kiện
 * @param occurredAt Thời điểm sự kiện phát sinh
 * @param sessionId Mã định danh buổi học sắp diễn ra
 * @param classId Mã lớp học
 * @param tutorId Mã Gia sư
 * @param studentId Mã Học sinh
 * @param startAt Thời điểm bắt đầu buổi học
 * @param hoursBefore Số giờ nhắc trước (24 hoặc 2)
 */
public record SessionUpcomingReminderEvent(
    UUID eventId,
    Instant occurredAt,
    UUID sessionId,
    UUID classId,
    UUID tutorId,
    UUID studentId,
    Instant startAt,
    int hoursBefore
) implements BaseDomainEvent {

  /**
   * Factory method tiện ích tạo nhanh một sự kiện nhắc nhở buổi học sắp tới.
   *
   * @param sessionId Mã buổi học
   * @param classId Mã lớp học
   * @param tutorId Mã gia sư
   * @param studentId Mã học sinh
   * @param startAt Thời điểm bắt đầu
   * @param hoursBefore Số giờ nhắc trước (24 hoặc 2)
   * @return Instance của {@link SessionUpcomingReminderEvent}
   */
  public static SessionUpcomingReminderEvent of(
      UUID sessionId,
      UUID classId,
      UUID tutorId,
      UUID studentId,
      Instant startAt,
      int hoursBefore
  ) {
    return new SessionUpcomingReminderEvent(
        UUID.randomUUID(),
        Instant.now(),
        sessionId,
        classId,
        tutorId,
        studentId,
        startAt,
        hoursBefore
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

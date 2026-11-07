package vn.edufit.scheduling.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Thay đổi lịch hàng loạt của một lớp, phát <b>một</b> sự kiện thay vì một sự kiện cho mỗi buổi để người dùng
 * nhận một thông báo cố định (không bị hàng chục thông báo đẩy các tin quan trọng khỏi danh sách).
 *
 * <p>{@code kind}: {@link #RECURRING_APPLIED} (áp dụng lịch định kỳ mới) hoặc {@link #CLASS_ENDED}
 * (lớp kết thúc nên các buổi tương lai bị hủy).
 */
public record ClassScheduleChangedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID classId,
    UUID tutorId,
    UUID studentId,
    UUID actorUserId,
    String kind,
    int cancelledCount,
    int generatedCount
) implements BaseDomainEvent {

  public static final String RECURRING_APPLIED = "RECURRING_APPLIED";
  public static final String CLASS_ENDED = "CLASS_ENDED";
  /** Chấp nhận hàng loạt đề xuất buổi học; {@code generatedCount} là số buổi đã chốt. */
  public static final String BATCH_ACCEPTED = "BATCH_ACCEPTED";
  /** Từ chối hàng loạt đề xuất buổi học; {@code cancelledCount} là số buổi bị từ chối. */
  public static final String BATCH_REJECTED = "BATCH_REJECTED";

  public static ClassScheduleChangedEvent of(UUID classId, UUID tutorId, UUID studentId, UUID actorUserId,
      String kind, int cancelledCount, int generatedCount) {
    return new ClassScheduleChangedEvent(UUID.randomUUID(), Instant.now(), classId, tutorId, studentId,
        actorUserId, kind, cancelledCount, generatedCount);
  }

  @Override
  public UUID getEventId() { return eventId; }

  @Override
  public Instant getOccurredAt() { return occurredAt; }
}

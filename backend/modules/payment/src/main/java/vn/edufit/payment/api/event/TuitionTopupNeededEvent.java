package vn.edufit.payment.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Buổi học chưa thu được học phí vì ví chưa đủ tiền. Gửi tới từng ví có thể trả (phụ huynh đã
 * liên kết, rồi học sinh). Hệ thống không hủy buổi học; khi ví đủ tiền, hệ thống tự thu.
 *
 * @param dueAt mốc thu học phí: 24 giờ trước giờ học.
 * @param stage {@link Stage#UPCOMING} khi còn trước mốc thu, {@link Stage#DUE} khi đã đến mốc thu
 *     mà buổi học chưa hoàn thành, {@link Stage#OVERDUE} khi buổi học đã hoàn thành.
 */
public record TuitionTopupNeededEvent(UUID eventId, Instant occurredAt, UUID userId, UUID studentId,
    UUID sessionId, long amount, long shortfall, Instant dueAt, Stage stage) implements BaseDomainEvent {

  public enum Stage { UPCOMING, DUE, OVERDUE }

  public static TuitionTopupNeededEvent of(UUID userId, UUID studentId, UUID sessionId, long amount,
      long shortfall, Instant dueAt, Stage stage) {
    return new TuitionTopupNeededEvent(UUID.randomUUID(), Instant.now(), userId, studentId, sessionId,
        amount, shortfall, dueAt, stage);
  }

  @Override public UUID getEventId() { return eventId; }
  @Override public Instant getOccurredAt() { return occurredAt; }
}

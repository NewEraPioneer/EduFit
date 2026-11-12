package vn.edufit.payment.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Đến mốc thu học phí (24 giờ trước giờ học) mà hệ thống chưa thu được. Báo cho gia sư để tự
 * quyết định dạy (học phí được thu sau) hoặc hủy buổi học.
 */
public record TuitionUnpaidForTutorEvent(UUID eventId, Instant occurredAt, UUID tutorUserId,
    UUID studentId, UUID sessionId, long amount, Instant startAt) implements BaseDomainEvent {

  public static TuitionUnpaidForTutorEvent of(UUID tutorUserId, UUID studentId, UUID sessionId,
      long amount, Instant startAt) {
    return new TuitionUnpaidForTutorEvent(UUID.randomUUID(), Instant.now(), tutorUserId, studentId,
        sessionId, amount, startAt);
  }

  @Override public UUID getEventId() { return eventId; }
  @Override public Instant getOccurredAt() { return occurredAt; }
}

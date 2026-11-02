package vn.edufit.scheduling.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Sự kiện miền phát sinh khi một buổi học bị hủy (trạng thái CANCELLED).
 *
 * <p>Module lắng nghe: {@code notification} (Thông báo buổi học bị hủy cho đối tác, cảnh báo nếu là hủy trễ).
 */
public record SessionCancelledEvent(
    UUID eventId,
    Instant occurredAt,
    UUID sessionId,
    UUID classId,
    UUID tutorId,
    UUID studentId,
    UUID cancelledBy,
    String cancelReason,
    String cancelComment,
    boolean isLateCancel
) implements BaseDomainEvent {

  public static SessionCancelledEvent of(
      UUID sessionId,
      UUID classId,
      UUID tutorId,
      UUID studentId,
      UUID cancelledBy,
      String cancelReason,
      String cancelComment,
      boolean isLateCancel
  ) {
    return new SessionCancelledEvent(
        UUID.randomUUID(),
        Instant.now(),
        sessionId,
        classId,
        tutorId,
        studentId,
        cancelledBy,
        cancelReason,
        cancelComment,
        isLateCancel
    );
  }

  @Override
  public UUID getEventId() { return eventId; }

  @Override
  public Instant getOccurredAt() { return occurredAt; }
}

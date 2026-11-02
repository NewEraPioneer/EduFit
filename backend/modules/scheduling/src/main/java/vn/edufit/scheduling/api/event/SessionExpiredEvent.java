package vn.edufit.scheduling.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Sự kiện miền phát sinh khi một đề xuất buổi học tự động hết hạn (trạng thái EXPIRED) bởi tác vụ ngầm.
 *
 * <p>Module lắng nghe: {@code notification} (Thông báo thu hồi đề xuất hết hạn cho cả hai bên).
 */
public record SessionExpiredEvent(
    UUID eventId,
    Instant occurredAt,
    UUID sessionId,
    UUID classId,
    UUID tutorId,
    UUID studentId
) implements BaseDomainEvent {

  public static SessionExpiredEvent of(
      UUID sessionId,
      UUID classId,
      UUID tutorId,
      UUID studentId
  ) {
    return new SessionExpiredEvent(
        UUID.randomUUID(),
        Instant.now(),
        sessionId,
        classId,
        tutorId,
        studentId
    );
  }

  @Override
  public UUID getEventId() { return eventId; }

  @Override
  public Instant getOccurredAt() { return occurredAt; }
}

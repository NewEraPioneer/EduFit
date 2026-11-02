package vn.edufit.scheduling.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Sự kiện miền phát sinh khi buổi học chính thức được chấp thuận và lên lịch (trạng thái SCHEDULED).
 *
 * <p>Module lắng nghe: {@code notification} (Gửi thông báo chốt lịch học thành công cho Học sinh & Phụ huynh).
 */
public record SessionScheduledEvent(
    UUID eventId,
    Instant occurredAt,
    UUID sessionId,
    UUID classId,
    UUID tutorId,
    UUID studentId,
    Instant startAt,
    Instant endAt
) implements BaseDomainEvent {

  public static SessionScheduledEvent of(
      UUID sessionId,
      UUID classId,
      UUID tutorId,
      UUID studentId,
      Instant startAt,
      Instant endAt
  ) {
    return new SessionScheduledEvent(
        UUID.randomUUID(),
        Instant.now(),
        sessionId,
        classId,
        tutorId,
        studentId,
        startAt,
        endAt
    );
  }

  @Override
  public UUID getEventId() { return eventId; }

  @Override
  public Instant getOccurredAt() { return occurredAt; }
}

package vn.edufit.scheduling.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Sự kiện miền phát sinh khi một buổi học mới được đề xuất (trạng thái PROPOSED).
 *
 * <p>Module lắng nghe: {@code notification} (Gửi thông báo và email cho đối tác để vào phản hồi).
 */
public record SessionProposedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID sessionId,
    UUID classId,
    UUID tutorId,
    UUID studentId,
    UUID proposedBy,
    Instant startAt,
    Instant endAt,
    Instant expiresAt
) implements BaseDomainEvent {

  public static SessionProposedEvent of(
      UUID sessionId,
      UUID classId,
      UUID tutorId,
      UUID studentId,
      UUID proposedBy,
      Instant startAt,
      Instant endAt,
      Instant expiresAt
  ) {
    return new SessionProposedEvent(
        UUID.randomUUID(),
        Instant.now(),
        sessionId,
        classId,
        tutorId,
        studentId,
        proposedBy,
        startAt,
        endAt,
        expiresAt
    );
  }

  @Override
  public UUID getEventId() { return eventId; }

  @Override
  public Instant getOccurredAt() { return occurredAt; }
}

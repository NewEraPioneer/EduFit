package vn.edufit.scheduling.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Sự kiện miền phát sinh khi đề xuất dời lịch được chấp thuận và thời gian buổi học được cập nhật thành công.
 *
 * <p>Module lắng nghe: {@code notification} (Gửi thông báo lịch học mới đã được thống nhất).
 */
public record SessionRescheduledEvent(
    UUID eventId,
    Instant occurredAt,
    UUID proposalId,
    UUID sessionId,
    UUID classId,
    UUID tutorId,
    UUID studentId,
    Instant newStartAt,
    Instant newEndAt
) implements BaseDomainEvent {

  public static SessionRescheduledEvent of(
      UUID proposalId,
      UUID sessionId,
      UUID classId,
      UUID tutorId,
      UUID studentId,
      Instant newStartAt,
      Instant newEndAt
  ) {
    return new SessionRescheduledEvent(
        UUID.randomUUID(),
        Instant.now(),
        proposalId,
        sessionId,
        classId,
        tutorId,
        studentId,
        newStartAt,
        newEndAt
    );
  }

  @Override
  public UUID getEventId() { return eventId; }

  @Override
  public Instant getOccurredAt() { return occurredAt; }
}

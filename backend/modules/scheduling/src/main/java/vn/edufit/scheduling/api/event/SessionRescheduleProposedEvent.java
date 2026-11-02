package vn.edufit.scheduling.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Sự kiện miền phát sinh khi một bên yêu cầu dời lịch buổi học (trạng thái PENDING).
 *
 * <p>Module lắng nghe: {@code notification} (Gửi thông báo đề xuất dời lịch cho đối tác).
 */
public record SessionRescheduleProposedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID proposalId,
    UUID sessionId,
    UUID classId,
    UUID tutorId,
    UUID studentId,
    UUID proposedBy,
    Instant newStartAt,
    Instant newEndAt,
    String reason,
    Instant expiresAt
) implements BaseDomainEvent {

  public static SessionRescheduleProposedEvent of(
      UUID proposalId,
      UUID sessionId,
      UUID classId,
      UUID tutorId,
      UUID studentId,
      UUID proposedBy,
      Instant newStartAt,
      Instant newEndAt,
      String reason,
      Instant expiresAt
  ) {
    return new SessionRescheduleProposedEvent(
        UUID.randomUUID(),
        Instant.now(),
        proposalId,
        sessionId,
        classId,
        tutorId,
        studentId,
        proposedBy,
        newStartAt,
        newEndAt,
        reason,
        expiresAt
    );
  }

  @Override
  public UUID getEventId() { return eventId; }

  @Override
  public Instant getOccurredAt() { return occurredAt; }
}

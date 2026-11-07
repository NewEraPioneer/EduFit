package vn.edufit.scheduling.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Sự kiện miền phát sinh khi đề xuất dời lịch bị từ chối.
 *
 * <p>Module lắng nghe: {@code notification} (Gửi thông báo đề xuất dời lịch bị từ chối cho người đề xuất).
 */
public record SessionRescheduleRejectedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID proposalId,
    UUID sessionId,
    UUID classId,
    UUID tutorId,
    UUID studentId,
    UUID proposedBy,
    UUID rejectedBy,
    String reason
) implements BaseDomainEvent {

  public static SessionRescheduleRejectedEvent of(
      UUID proposalId,
      UUID sessionId,
      UUID classId,
      UUID tutorId,
      UUID studentId,
      UUID proposedBy,
      UUID rejectedBy,
      String reason
  ) {
    return new SessionRescheduleRejectedEvent(
        UUID.randomUUID(),
        Instant.now(),
        proposalId,
        sessionId,
        classId,
        tutorId,
        studentId,
        proposedBy,
        rejectedBy,
        reason
    );
  }

  @Override
  public UUID getEventId() { return eventId; }

  @Override
  public Instant getOccurredAt() { return occurredAt; }
}

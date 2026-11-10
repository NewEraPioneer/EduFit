package vn.edufit.scheduling.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Sự kiện miền khi bên đối tác từ chối một đề xuất buổi học mới (PROPOSED → REJECTED).
 *
 * <p>Module lắng nghe: {@code notification} (báo người đề xuất và những người đã biết về đề xuất).
 */
public record SessionProposalRejectedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID sessionId,
    UUID classId,
    UUID tutorId,
    UUID studentId,
    UUID proposedBy,
    UUID rejectedBy,
    String reason
) implements BaseDomainEvent {

  public static SessionProposalRejectedEvent of(UUID sessionId, UUID classId, UUID tutorId, UUID studentId,
      UUID proposedBy, UUID rejectedBy, String reason) {
    return new SessionProposalRejectedEvent(UUID.randomUUID(), Instant.now(), sessionId, classId, tutorId,
        studentId, proposedBy, rejectedBy, reason);
  }

  @Override
  public UUID getEventId() { return eventId; }

  @Override
  public Instant getOccurredAt() { return occurredAt; }
}

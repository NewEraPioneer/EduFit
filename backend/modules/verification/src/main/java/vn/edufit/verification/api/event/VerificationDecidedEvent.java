package vn.edufit.verification.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Admin đã quyết định một hồ sơ xác minh gia sư. {@code decision}: {@link #APPROVED}, {@link #REJECTED}
 * hoặc {@link #REVOKED}. {@code tutorId} là id hồ sơ gia sư; {@code reason} có thể null khi duyệt.
 */
public record VerificationDecidedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID requestId,
    UUID tutorId,
    String decision,
    String reason
) implements BaseDomainEvent {

  public static final String APPROVED = "APPROVED";
  public static final String REJECTED = "REJECTED";
  public static final String REVOKED = "REVOKED";

  public static VerificationDecidedEvent of(UUID requestId, UUID tutorId, String decision, String reason) {
    return new VerificationDecidedEvent(UUID.randomUUID(), Instant.now(), requestId, tutorId, decision, reason);
  }

  @Override
  public UUID getEventId() { return eventId; }

  @Override
  public Instant getOccurredAt() { return occurredAt; }
}

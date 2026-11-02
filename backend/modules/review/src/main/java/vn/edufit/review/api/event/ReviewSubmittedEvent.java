package vn.edufit.review.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Sự kiện miền được phát ra khi học sinh/phụ huynh gửi đánh giá gia sư thành công (UC6.1, FR-28).
 */
public record ReviewSubmittedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID reviewId,
    UUID tutorId,
    UUID studentId,
    UUID reviewerUserId,
    String reviewerType,
    int rating
) implements BaseDomainEvent {

  public static ReviewSubmittedEvent of(
      UUID reviewId,
      UUID tutorId,
      UUID studentId,
      UUID reviewerUserId,
      String reviewerType,
      int rating
  ) {
    return new ReviewSubmittedEvent(
        UUID.randomUUID(),
        Instant.now(),
        reviewId,
        tutorId,
        studentId,
        reviewerUserId,
        reviewerType,
        rating
    );
  }

  @Override
  public UUID getEventId() {
    return eventId;
  }

  @Override
  public Instant getOccurredAt() {
    return occurredAt;
  }
}


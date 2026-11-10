package vn.edufit.progress.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/** Đến mốc nhắc gia sư hoàn tất phản hồi 10 giây của một buổi học. */
public record FeedbackReminderDueEvent(
    UUID eventId,
    Instant occurredAt,
    UUID sessionId,
    UUID classId,
    UUID studentId,
    UUID tutorId,
    long hoursSinceEnd,
    long hoursLeft
) implements BaseDomainEvent {

  public static FeedbackReminderDueEvent of(
      UUID sessionId, UUID classId, UUID studentId, UUID tutorId, long hoursSinceEnd, long hoursLeft) {
    return new FeedbackReminderDueEvent(
        UUID.randomUUID(), Instant.now(), sessionId, classId, studentId, tutorId, hoursSinceEnd, hoursLeft);
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

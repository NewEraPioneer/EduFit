package vn.edufit.progress.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/** Một cột mốc của lộ trình vừa được chốt đạt. */
public record MilestoneAchievedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID classId,
    UUID studentId,
    UUID milestoneId,
    String milestoneTitle
) implements BaseDomainEvent {

  public static MilestoneAchievedEvent of(UUID classId, UUID studentId, UUID milestoneId, String milestoneTitle) {
    return new MilestoneAchievedEvent(UUID.randomUUID(), Instant.now(), classId, studentId, milestoneId, milestoneTitle);
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

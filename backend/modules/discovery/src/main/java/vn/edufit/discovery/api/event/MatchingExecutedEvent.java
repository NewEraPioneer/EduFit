package vn.edufit.discovery.api.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Describes a completed matching run, including runs with zero results.
 * Consumers with external side effects must handle this event after transaction commit.
 * This is an in-process notification, not a durable outbox or delivery guarantee.
 */
public record MatchingExecutedEvent(
    UUID userId,
    UUID studentId,
    UUID needId,
    int resultCount,
    Instant executedAt
) {

  public MatchingExecutedEvent(UUID userId, UUID studentId, UUID needId, int resultCount) {
    this(userId, studentId, needId, resultCount, Instant.now());
  }
}

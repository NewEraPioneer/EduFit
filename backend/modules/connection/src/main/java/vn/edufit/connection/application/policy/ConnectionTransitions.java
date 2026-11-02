package vn.edufit.connection.application.policy;

import java.time.Duration;
import java.time.Instant;
import vn.edufit.shared.exception.ResourceConflictException;

/** Shared state/time rules; persistence retains the existing status values. */
public final class ConnectionTransitions {
  public static final Duration REQUEST_TTL = Duration.ofDays(7);
  public static final Duration INVITATION_TTL = Duration.ofDays(14);
  public static final Duration DECLINE_COOLDOWN = Duration.ofDays(7);
  public static final Duration QUOTA_WINDOW = Duration.ofHours(1);
  public static final int INVITATIONS_PER_WINDOW = 10;

  private ConnectionTransitions() {}

  public static void requireState(String actual, String expected) {
    if (!expected.equals(actual))
      throw new ResourceConflictException("Không còn ở trạng thái " + expected + ": " + actual);
  }

  public static void requirePending(String status, Instant expiresAt, Instant now) {
    requireState(status, "PENDING");
    if (isDue(expiresAt, now)) throw new ResourceConflictException("Đã hết hạn xử lý");
  }

  public static boolean isDue(Instant deadline, Instant now) { return !deadline.isAfter(now); }

  public static boolean cooldownActive(Instant reinviteAfter, Instant now) {
    return reinviteAfter != null && reinviteAfter.isAfter(now);
  }
}

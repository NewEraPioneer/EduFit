package vn.edufit.progress.domain.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Mốc thời gian của phản hồi 10 giây, tính từ lúc buổi học kết thúc (mục 3.6):
 * nhắc gia sư lúc +12h và +24h nếu còn "để sau"; ghi sau +48h thì đánh dấu "bổ sung muộn".
 */
public final class FeedbackTimingPolicy {

  public static final Duration LATE_AFTER = Duration.ofHours(48);

  private FeedbackTimingPolicy() {}

  public enum ReminderStage {
    H12(Duration.ofHours(12)),
    H24(Duration.ofHours(24));

    private final Duration after;

    ReminderStage(Duration after) { this.after = after; }

    public Duration after() { return after; }
  }

  public static boolean isLate(Instant sessionEndAt, Instant submittedAt) {
    return sessionEndAt != null && submittedAt.isAfter(sessionEndAt.plus(LATE_AFTER));
  }

  /** Khoảng thời điểm kết thúc buổi học còn có thể cần nhắc lúc {@code now}: (now − 48h, now − 12h]. */
  public static Instant reminderWindowStart(Instant now) {
    return now.minus(LATE_AFTER);
  }

  public static Instant reminderWindowEnd(Instant now) {
    return now.minus(ReminderStage.H12.after());
  }

  /**
   * Mốc nhắc cần gửi lúc {@code now}: mốc muộn nhất đã tới mà chưa gửi. Nếu job lỡ mốc +12h thì chỉ gửi mốc +24h,
   * không dồn hai lần nhắc. Từ +48h thôi nhắc (phản hồi chuyển sang "bổ sung muộn").
   */
  public static Optional<ReminderStage> dueStage(Instant sessionEndAt, Instant now, Set<ReminderStage> alreadySent) {
    if (sessionEndAt == null || !now.isBefore(sessionEndAt.plus(LATE_AFTER))) return Optional.empty();
    ReminderStage due = null;
    for (ReminderStage stage : ReminderStage.values()) {
      if (!now.isBefore(sessionEndAt.plus(stage.after()))) due = stage;
    }
    if (due == null || alreadySent.contains(due)) return Optional.empty();
    return Optional.of(due);
  }
}

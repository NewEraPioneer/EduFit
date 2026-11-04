package vn.edufit.iam;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Đồng hồ điều khiển được để kiểm thử thời gian chờ/hết hạn mà không cần sleep. */
final class MutableClock extends Clock {

  private Instant now;

  MutableClock(Instant start) {
    this.now = start;
  }

  void advance(Duration duration) {
    now = now.plus(duration);
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    return now;
  }
}

package vn.edufit.progress.domain.policy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Kỳ báo cáo 30 ngày kể từ ngày bắt đầu lớp (mục 3.13), khớp cửa sổ cuốn chiếu 30 ngày của dashboard.
 * Kỳ thứ {@code k} gồm các ngày {@code [start + 30(k-1), start + 30k - 1]}; báo cáo được lập từ ngày kế tiếp.
 */
public final class PeriodReportPolicy {

  public static final int PERIOD_DAYS = 30;
  /** Bản nháp tự gửi sau 48 giờ kể cả khi gia sư không viết thêm. */
  public static final Duration AUTO_SEND_AFTER = Duration.ofHours(48);

  private PeriodReportPolicy() {}

  /** @param end ngày cuối kỳ (bao gồm). */
  public record Period(int index, LocalDate start, LocalDate end) {
    public boolean contains(LocalDate date) {
      return date != null && !date.isBefore(start) && !date.isAfter(end);
    }
  }

  /**
   * Kỳ gần nhất đã kết thúc tính đến {@code today}. Chỉ lập báo cáo cho kỳ gần nhất, không bù các kỳ cũ để ảnh chụp
   * luôn khớp số liệu tại cuối kỳ.
   */
  public static Optional<Period> latestCompleted(LocalDate classStart, LocalDate today) {
    Objects.requireNonNull(classStart, "classStart");
    Objects.requireNonNull(today, "today");
    long index = ChronoUnit.DAYS.between(classStart, today) / PERIOD_DAYS;
    if (index < 1) return Optional.empty();
    LocalDate start = classStart.plusDays((index - 1) * PERIOD_DAYS);
    return Optional.of(new Period((int) index, start, start.plusDays(PERIOD_DAYS - 1)));
  }
}

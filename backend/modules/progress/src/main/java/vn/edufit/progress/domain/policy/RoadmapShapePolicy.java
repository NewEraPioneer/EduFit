package vn.edufit.progress.domain.policy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Hình dạng chuẩn của một lộ trình (mục 3.4), dùng chung cho lộ trình của lớp và mẫu lộ trình:
 * <ul>
 *   <li>{@value #MIN_MILESTONES}–{@value #MAX_MILESTONES} cột mốc đang hoạt động; độ mịn hơn nằm trong tiêu chí.</li>
 *   <li>Mỗi cột mốc khoảng {@value #MIN_SESSIONS}–{@value #MAX_SESSIONS} buổi (gợi ý của mẫu).</li>
 *   <li>Cột mốc cuối bắt buộc có hạn; các mốc giữa tùy chọn, hệ thống nội suy.</li>
 * </ul>
 */
public final class RoadmapShapePolicy {

  public static final int MIN_MILESTONES = 3;
  public static final int MAX_MILESTONES = 8;
  public static final int MIN_SESSIONS = 2;
  public static final int MAX_SESSIONS = 6;

  private RoadmapShapePolicy() {}

  public static void requireMilestoneCount(long activeCount) {
    if (activeCount < MIN_MILESTONES || activeCount > MAX_MILESTONES) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Lộ trình phải có từ " + MIN_MILESTONES + " đến " + MAX_MILESTONES + " cột mốc đang hoạt động");
    }
  }

  public static void requireSuggestedSessions(Integer sessions) {
    if (sessions == null || sessions < MIN_SESSIONS || sessions > MAX_SESSIONS) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Mỗi cột mốc gợi ý từ " + MIN_SESSIONS + " đến " + MAX_SESSIONS + " buổi học");
    }
  }

  /** @param orderedTargetDates hạn của các cột mốc đang hoạt động theo thứ tự lộ trình. */
  public static void requireFinalTargetDate(List<LocalDate> orderedTargetDates) {
    if (orderedTargetDates.isEmpty() || orderedTargetDates.getLast() == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Cột mốc cuối của lộ trình bắt buộc có hạn");
    }
  }

  /** Hạn hiệu lực của một cột mốc: hạn gia sư đặt, hoặc ngày nội suy. */
  public record ExpectedDate(LocalDate date, boolean interpolated) {}

  /**
   * Nội suy tuyến tính hạn của các mốc giữa còn trống, giữa hai mốc đã biết gần nhất (mốc đầu tính từ ngày lập lộ
   * trình). Mốc trống phía sau mốc có hạn cuối cùng giữ trống: không đoán hạn khi chưa có ngày kết thúc.
   *
   * @param startDate          ngày lập lộ trình, làm mốc 0.
   * @param orderedTargetDates hạn gia sư đặt theo thứ tự lộ trình (null = chưa đặt).
   * @return cùng số phần tử; phần tử null nếu không nội suy được.
   */
  public static List<ExpectedDate> interpolate(LocalDate startDate, List<LocalDate> orderedTargetDates) {
    int n = orderedTargetDates.size();
    List<ExpectedDate> result = new ArrayList<>(n);
    int prevIndex = -1;
    LocalDate prevDate = startDate;
    for (int i = 0; i < n; i++) result.add(null);
    for (int i = 0; i < n; i++) {
      LocalDate known = orderedTargetDates.get(i);
      if (known == null) continue;
      result.set(i, new ExpectedDate(known, false));
      int gap = i - prevIndex;
      long span = ChronoUnit.DAYS.between(prevDate, known);
      for (int k = prevIndex + 1; k < i; k++) {
        result.set(k, new ExpectedDate(prevDate.plusDays(span * (k - prevIndex) / gap), true));
      }
      prevIndex = i;
      prevDate = known;
    }
    return result;
  }
}

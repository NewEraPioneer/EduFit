package vn.edufit.progress.domain.policy;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Dự báo "còn X buổi cho Y cột mốc" (Giai đoạn 3).
 *
 * <ul>
 *   <li><b>Nhịp thực tế</b> ({@link Basis#PACE}): khi lớp đã đạt ít nhất một cột mốc có buổi học được chấm, mỗi cột mốc
 *       còn lại ước tính bằng số buổi trung bình đã dùng cho một cột mốc đạt (làm tròn lên).</li>
 *   <li><b>Số buổi gợi ý</b> ({@link Basis#SUGGESTED}): chưa đạt cột mốc nào thì dùng số buổi gợi ý (2–6) của từng cột
 *       mốc; thiếu gợi ý ở bất kỳ cột mốc còn lại nào thì không dự báo.</li>
 *   <li>Mỗi cột mốc còn lại trừ đi số buổi đã học cho nó, tối thiểu còn 1 buổi.</li>
 *   <li>Ngày xong dự kiến chỉ có khi lớp học đều: suy từ số buổi đã học trong 28 ngày gần nhất.</li>
 * </ul>
 */
public final class ForecastPolicy {

  /** Khoảng thời gian dùng để đo nhịp học (số buổi/tuần). */
  public static final int CADENCE_DAYS = 28;

  private ForecastPolicy() {}

  public enum Basis { PACE, SUGGESTED }

  /** @param sessionsSpent số buổi học đã được chấm cho cột mốc. */
  public record MilestoneEffort(boolean achieved, Integer suggestedSessions, int sessionsSpent) {}

  public record Forecast(Basis basis, int remainingSessions, int remainingMilestones, LocalDate estimatedFinishDate) {}

  /** Đúng một trong hai trường khác null. */
  public record Result(Forecast forecast, String unavailableReason) {
    static Result unavailable(String reason) {
      return new Result(null, reason);
    }
  }

  /**
   * @param milestones          cột mốc đang hoạt động theo thứ tự lộ trình.
   * @param recentCompletedCount số buổi đã học trong {@link #CADENCE_DAYS} ngày gần nhất.
   */
  public static Result evaluate(List<MilestoneEffort> milestones, int recentCompletedCount, LocalDate today) {
    Objects.requireNonNull(today, "today");
    List<MilestoneEffort> remaining = milestones.stream().filter(m -> !m.achieved()).toList();
    if (milestones.isEmpty()) return Result.unavailable("Lộ trình chưa có cột mốc");
    if (remaining.isEmpty()) return Result.unavailable("Đã đạt toàn bộ cột mốc của lộ trình");

    List<MilestoneEffort> achievedWithSessions =
        milestones.stream().filter(m -> m.achieved() && m.sessionsSpent() > 0).toList();
    Basis basis;
    int total = 0;
    if (!achievedWithSessions.isEmpty()) {
      basis = Basis.PACE;
      int spent = achievedWithSessions.stream().mapToInt(MilestoneEffort::sessionsSpent).sum();
      int pace = ceilDiv(spent, achievedWithSessions.size());
      for (MilestoneEffort m : remaining) total += Math.max(1, pace - m.sessionsSpent());
    } else {
      if (remaining.stream().anyMatch(m -> m.suggestedSessions() == null)) {
        return Result.unavailable("Chưa đủ dữ liệu dự báo: có cột mốc chưa có số buổi gợi ý");
      }
      basis = Basis.SUGGESTED;
      for (MilestoneEffort m : remaining) total += Math.max(1, m.suggestedSessions() - m.sessionsSpent());
    }

    LocalDate finish = recentCompletedCount > 0
        ? today.plusDays(ceilDiv(total * CADENCE_DAYS, recentCompletedCount))
        : null;
    return new Result(new Forecast(basis, total, remaining.size(), finish), null);
  }

  private static int ceilDiv(int a, int b) {
    return (a + b - 1) / b;
  }
}

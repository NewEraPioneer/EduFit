package vn.edufit.progress.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Công thức tính tiến độ học tập (Progress Percentage) thuần Java (POJO, không Spring, không JPA).
 *
 * <p>Công thức:
 * <pre>
 *   progressPercent = Σ weight(milestone ACHIEVED) / Σ weight(milestone NOT archived) × 100
 * </pre>
 *
 * <p>Quy tắc:
 * <ul>
 *   <li>Kết quả làm tròn số nguyên theo HALF_UP.</li>
 *   <li>Milestone archived không tham gia tử số hoặc mẫu số.</li>
 *   <li>Lớp chưa có learning plan trả {@code null}, không trả 0%.</li>
 *   <li>Toàn bộ unit của frontend đều phải lấy từ API này — không tự tính ở client.</li>
 * </ul>
 */
public class ProgressCalculator {

  private ProgressCalculator() {}

  /**
   * Tính tiến độ từ danh sách milestone.
   *
   * @param milestones Danh sách milestone của một learning plan.
   * @return Phần trăm tiến độ (0–100), hoặc {@code null} nếu không có milestone nào hoạt động.
   */
  public static Integer calculate(List<MilestoneWeight> milestones) {
    if (milestones == null || milestones.isEmpty()) return null;

    int totalWeight = 0;
    int achievedWeight = 0;

    for (MilestoneWeight m : milestones) {
      if (m.archived()) continue; // Archived không tham gia tính toán
      totalWeight += m.weight();
      if (m.achieved()) {
        achievedWeight += m.weight();
      }
    }

    if (totalWeight == 0) return null; // Không có milestone active → chưa có dữ liệu

    return BigDecimal.valueOf(achievedWeight)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(totalWeight), 0, RoundingMode.HALF_UP)
        .intValue();
  }

  /**
   * Input record cho ProgressCalculator — mang thông tin tối thiểu cần thiết.
   *
   * @param weight Trọng số (1–5).
   * @param achieved Milestone đã đạt trạng thái ACHIEVED.
   * @param archived Milestone đã bị archive (không tính vào mẫu số lẫn tử số).
   */
  public record MilestoneWeight(int weight, boolean achieved, boolean archived) {}
}

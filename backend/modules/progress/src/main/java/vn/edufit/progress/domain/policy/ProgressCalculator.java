package vn.edufit.progress.domain.policy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Công thức tính tiến độ học tập (Progress Percentage) thuần Java (POJO, không Spring, không JPA).
 *
 * <p>Công thức:
 * <pre>
 *   progressPercent = Σ(weight × completed/total) / Σ weight × 100   (chỉ milestone NOT archived)
 * </pre>
 *
 * <p>Quy tắc:
 * <ul>
 *   <li>Cột mốc ACHIEVED có tỉ lệ 1/1; cột mốc đang học có tỉ lệ = số tiêu chí đã tick / tổng tiêu chí;
 *       cột mốc chưa có tiêu chí có tỉ lệ 0 cho đến khi đạt.</li>
 *   <li>Kết quả làm tròn số nguyên theo HALF_UP.</li>
 *   <li>Milestone archived không tham gia tử số hoặc mẫu số.</li>
 *   <li>Không có milestone hoạt động trả {@code null}, không trả 0%.</li>
 *   <li>Frontend luôn lấy kết quả từ API — không tự tính ở client.</li>
 * </ul>
 */
public class ProgressCalculator {

  private static final MathContext PRECISION = MathContext.DECIMAL64;

  private ProgressCalculator() {}

  /**
   * Tính tiến độ từ danh sách milestone.
   *
   * @param milestones Danh sách milestone của một learning plan.
   * @return Phần trăm tiến độ (0–100), hoặc {@code null} nếu không có milestone nào hoạt động.
   */
  public static Integer calculate(List<MilestoneWeight> milestones) {
    if (milestones == null || milestones.isEmpty()) return null;

    BigDecimal totalWeight = BigDecimal.ZERO;
    BigDecimal earnedWeight = BigDecimal.ZERO;

    for (MilestoneWeight m : milestones) {
      if (m.archived()) continue;
      BigDecimal weight = BigDecimal.valueOf(m.weight());
      totalWeight = totalWeight.add(weight);
      earnedWeight = earnedWeight.add(
          weight.multiply(BigDecimal.valueOf(m.completedParts()))
              .divide(BigDecimal.valueOf(m.totalParts()), PRECISION)
      );
    }

    if (totalWeight.signum() == 0) return null;

    return earnedWeight
        .multiply(BigDecimal.valueOf(100))
        .divide(totalWeight, 0, RoundingMode.HALF_UP)
        .intValue();
  }

  /**
   * Input record cho ProgressCalculator — mang thông tin tối thiểu cần thiết.
   *
   * @param weight         Trọng số (1–5).
   * @param completedParts Tử số tỉ lệ hoàn thành (0..totalParts).
   * @param totalParts     Mẫu số tỉ lệ hoàn thành (≥ 1).
   * @param archived       Milestone đã bị archive (không tính vào mẫu số lẫn tử số).
   */
  public record MilestoneWeight(int weight, int completedParts, int totalParts, boolean archived) {
    public MilestoneWeight {
      if (totalParts < 1) {
        throw new IllegalArgumentException("totalParts phải ≥ 1");
      }
      if (completedParts < 0 || completedParts > totalParts) {
        throw new IllegalArgumentException("completedParts phải nằm trong [0, totalParts]");
      }
    }

    /** Cột mốc đã đạt (1/1) hoặc chưa đạt và chưa có tiến bộ một phần (0/1). */
    public static MilestoneWeight of(int weight, boolean achieved, boolean archived) {
      return new MilestoneWeight(weight, achieved ? 1 : 0, 1, archived);
    }
  }
}

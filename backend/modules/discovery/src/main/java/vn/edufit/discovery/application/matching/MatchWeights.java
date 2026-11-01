package vn.edufit.discovery.application.matching;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import vn.edufit.profile.api.fit.FitCriterion;

/**
 * Trọng số chấm điểm ghép đôi (cấu hình {@code edufit.discovery.matching.weights}, tổng 100).
 * Tiêu chí không áp dụng cho một nhu cầu được bỏ khỏi mẫu số, nên phần trọng số của nó
 * được chia lại cho các tiêu chí còn lại. Đây là điểm khởi đầu, sẽ hiệu chỉnh khi có dữ liệu thật.
 */
@ConfigurationProperties(prefix = "edufit.discovery.matching.weights")
public record MatchWeights(
    int level,
    int goal,
    int schedule,
    int textbook,
    int budget,
    int location,
    int rating) {

  public MatchWeights {
    int[] values = {level, goal, schedule, textbook, budget, location, rating};
    int sum = 0;
    for (int value : values) {
      if (value < 0) {
        throw new IllegalArgumentException("Matching weights must be non-negative.");
      }
      sum += value;
    }
    if (sum != 100) {
      throw new IllegalArgumentException("Matching weights must sum to 100.");
    }
  }

  public static MatchWeights standard() {
    return new MatchWeights(15, 20, 20, 10, 10, 10, 15);
  }

  public Map<FitCriterion, Integer> byCriterion() {
    var weights = new EnumMap<FitCriterion, Integer>(FitCriterion.class);
    weights.put(FitCriterion.LEVEL, level);
    weights.put(FitCriterion.GOAL, goal);
    weights.put(FitCriterion.SCHEDULE, schedule);
    weights.put(FitCriterion.TEXTBOOK, textbook);
    weights.put(FitCriterion.BUDGET, budget);
    weights.put(FitCriterion.LOCATION, location);
    return weights;
  }
}

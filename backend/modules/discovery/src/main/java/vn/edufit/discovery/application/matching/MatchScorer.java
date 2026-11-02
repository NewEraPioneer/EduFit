package vn.edufit.discovery.application.matching;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;
import vn.edufit.discovery.domain.model.EducationLevelOrder;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.profile.api.fit.FitCheck;
import vn.edufit.profile.api.fit.NeedFitEvaluator;

/**
 * Chấm điểm lọc mềm: gia sư chỉ bị loại khi không có gói dạy đúng môn; mọi tiêu chí khác
 * cộng điểm theo trọng số, tiêu chí không áp dụng được chia lại trọng số.
 */
public class MatchScorer {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private final EducationLevelOrder levelOrder;
  private final MatchWeights weights;

  public MatchScorer(EducationLevelOrder levelOrder, MatchWeights weights) {
    this.levelOrder = Objects.requireNonNull(levelOrder);
    this.weights = Objects.requireNonNull(weights);
  }

  public Optional<MatchScore> score(LearningNeedView need, TutorDiscoveryProfileDto tutor) {
    return NeedFitEvaluator.evaluate(need, tutor, levelOrder::areAdjacent).map(fit -> {
      var byCriterion = weights.byCriterion();
      BigDecimal rating = ratingFit(tutor.tutor());
      BigDecimal weighted = rating.multiply(BigDecimal.valueOf(weights.rating()));
      int applicableWeight = weights.rating();
      for (FitCheck check : fit.checks()) {
        int weight = byCriterion.get(check.criterion());
        weighted = weighted.add(BigDecimal.valueOf((long) check.score() * weight));
        applicableWeight += weight;
      }
      BigDecimal total = applicableWeight == 0 ? BigDecimal.ZERO
          : weighted.divide(BigDecimal.valueOf(applicableWeight), 2, RoundingMode.HALF_UP);
      int reviews = tutor.tutor().reviewCount() == null ? 0 : tutor.tutor().reviewCount();
      return new MatchScore(tutor.tutor().tutorId(), total, rating.setScale(2, RoundingMode.HALF_UP),
          fit.offering(), fit.checks(), reviews, tutor.registeredAt());
    });
  }

  /** Đánh giá trung bình nhân độ tin cậy theo số lượt; chưa có đánh giá tính 35 điểm. */
  static BigDecimal ratingFit(TutorSummaryDto tutor) {
    int reviews = tutor.reviewCount() == null ? 0 : tutor.reviewCount();
    if (reviews <= 0 || tutor.ratingAvg() == null) {
      return BigDecimal.valueOf(35);
    }
    BigDecimal confidence = new BigDecimal("0.5").add(
        BigDecimal.valueOf(Math.min(5, reviews)).multiply(new BigDecimal("0.1")));
    return tutor.ratingAvg().max(BigDecimal.ZERO).min(BigDecimal.valueOf(5))
        .multiply(BigDecimal.valueOf(20)).multiply(confidence).min(HUNDRED);
  }
}

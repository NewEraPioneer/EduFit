package vn.edufit.discovery.application.matching;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import vn.edufit.profile.api.dto.TutorOfferingDto;
import vn.edufit.profile.api.fit.FitCheck;
import vn.edufit.profile.api.fit.FitCriterion;

/**
 * Điểm ghép đôi (0–100) của một gia sư cho một nhu cầu: tổng có trọng số, điểm đánh giá
 * và từng tiêu chí đã so (✔/⚠) trên gói dạy phù hợp nhất.
 */
public record MatchScore(
    UUID tutorId,
    BigDecimal total,
    BigDecimal ratingFit,
    TutorOfferingDto offering,
    List<FitCheck> checks,
    int reviewCount,
    Instant registeredAt
) {
  public MatchScore {
    checks = List.copyOf(checks);
  }

  public int scheduleScore() {
    return checks.stream().filter(check -> check.criterion() == FitCriterion.SCHEDULE)
        .mapToInt(FitCheck::score).findFirst().orElse(0);
  }

  public long matchedCount() {
    return checks.stream().filter(FitCheck::matched).count();
  }

  public static Comparator<MatchScore> rankingOrder() {
    return Comparator.comparing(MatchScore::total).reversed()
        .thenComparing(MatchScore::matchedCount, Comparator.reverseOrder())
        .thenComparing(MatchScore::scheduleScore, Comparator.reverseOrder())
        .thenComparing(MatchScore::reviewCount, Comparator.reverseOrder())
        .thenComparing(MatchScore::registeredAt, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(MatchScore::tutorId);
  }
}

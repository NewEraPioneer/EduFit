package vn.edufit.discovery.web.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import vn.edufit.discovery.application.matching.MatchScore;

/** Độ khớp với nhu cầu học: điểm tổng, gói dạy được dùng để so và nhãn từng tiêu chí. */
public record FitResponse(
    BigDecimal matchScore,
    UUID offeringId,
    String offeringName,
    long pricePerSession,
    List<Item> checks
) {
  public record Item(String criterion, boolean matched, String label) {}

  public static FitResponse from(MatchScore score) {
    if (score == null) {
      return null;
    }
    var offering = score.offering();
    String name = offering.trackName() == null
        ? offering.subjectName() : offering.subjectName() + " – " + offering.trackName();
    return new FitResponse(score.total(), offering.offeringId(), name, offering.pricePerSession(),
        score.checks().stream()
            .map(check -> new Item(check.criterion().name(), check.matched(), check.label()))
            .toList());
  }
}

package vn.edufit.discovery.application.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.edufit.profile.api.fit.FitCheck;
import vn.edufit.profile.api.fit.FitCriterion;

class MatchScoreTest {

  @Test
  void rankingUsesTotalMatchedCountScheduleReviewsRegistrationAndIdInThatOrder() {
    var expected = List.of(
        score(9, "91", 0, 0, 0, null),
        score(8, "90", 2, 0, 0, null),
        score(6, "90", 1, 80, 0, null),
        score(5, "90", 1, 70, 10, null),
        score(2, "90", 1, 70, 5, Instant.EPOCH),
        score(3, "90", 1, 70, 5, Instant.EPOCH),
        score(7, "90", 1, 70, 5, Instant.EPOCH.plusSeconds(1)),
        score(1, "90", 1, 70, 5, null));
    for (int seed = 0; seed < 20; seed++) {
      var shuffled = new ArrayList<>(expected);
      Collections.shuffle(shuffled, new Random(seed));
      assertEquals(expected, shuffled.stream().sorted(MatchScore.rankingOrder()).toList());
    }
  }

  private MatchScore score(long id, String total, int matched, int schedule, int reviews, Instant registered) {
    var checks = new ArrayList<FitCheck>();
    checks.add(new FitCheck(FitCriterion.SCHEDULE, schedule, false, "Lịch"));
    for (int i = 0; i < matched; i++) {
      checks.add(new FitCheck(FitCriterion.GOAL, 100, true, "Mục tiêu"));
    }
    return new MatchScore(new UUID(0, id), new BigDecimal(total), BigDecimal.ZERO, null, checks, reviews, registered);
  }
}

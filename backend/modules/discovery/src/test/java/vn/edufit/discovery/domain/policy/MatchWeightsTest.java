package vn.edufit.discovery.domain.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.edufit.profile.api.fit.FitCriterion;

class MatchWeightsTest {

  @ParameterizedTest
  @CsvSource({"-5,25,20,10,10,10,30", "15,20,20,10,10,10,14", "15,20,20,10,10,10,16"})
  void rejectsNegativeWeightsOrSumOtherThanHundred(int level, int goal, int schedule, int textbook, int budget,
      int location, int rating) {
    assertThrows(IllegalArgumentException.class,
        () -> new MatchWeights(level, goal, schedule, textbook, budget, location, rating));
  }

  @Test
  void standardWeightsMatchAgreedStartingPoint() {
    var weights = MatchWeights.standard();
    assertEquals(new MatchWeights(15, 20, 20, 10, 10, 10, 15), weights);
    assertEquals(20, weights.byCriterion().get(FitCriterion.GOAL));
    assertEquals(10, weights.byCriterion().get(FitCriterion.LOCATION));
    assertDoesNotThrow(() -> new MatchWeights(100, 0, 0, 0, 0, 0, 0));
  }
}

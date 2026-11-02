package vn.edufit.discovery.application.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.edufit.discovery.DiscoveryTestData.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.edufit.discovery.domain.model.EducationLevelOrder;
import vn.edufit.profile.api.fit.FitCriterion;

class MatchScorerTest {

  private final MatchScorer scorer = new MatchScorer(
      new EducationLevelOrder(List.of(1, 2, LEVEL_8, LEVEL_9)), MatchWeights.standard());

  @Test
  @DisplayName("Khớp mọi tiêu chí với đánh giá 5/5 từ ≥5 lượt thì đạt 100")
  void perfectMatchScoresHundred() {
    var tutor = tutor(UUID.randomUUID(), "BOTH", HA_NOI, List.of(CAU_GIAY), List.of(slot(2, 18, 22)),
        BigDecimal.valueOf(5), 10, offering().build());
    var score = scorer.score(need().build(), tutor).orElseThrow();
    assertEquals(new BigDecimal("100.00"), score.total());
    assertEquals(6, score.checks().size());
  }

  @Test
  @DisplayName("Không dạy môn thì bị loại; lệch tiêu chí khác vẫn có mặt trong kết quả")
  void onlySubjectExcludes() {
    var otherSubject = tutor("ONLINE", null, List.of(), List.of(), offering().subject(SCIENCE, SCI_PHYSICS).build());
    assertTrue(scorer.score(need().build(), otherSubject).isEmpty());

    var everythingWrong = tutor("ONLINE", null, List.of(), List.of(slot(5, 8, 9)),
        offering().levels(1).goals(goal(GIFTED, true, false)).textbooks(CTST).price(900000).build());
    var score = scorer.score(need().build(), everythingWrong).orElseThrow();
    assertTrue(score.total().compareTo(BigDecimal.ZERO) > 0, "Đánh giá mặc định vẫn cho điểm, không loại gia sư");
    assertEquals(0, score.matchedCount());
  }

  @Test
  @DisplayName("Tiêu chí không áp dụng được chia lại trọng số (Luyện thi vào 10 không xét bộ sách)")
  void nonApplicableCriteriaRedistributeWeight() {
    var examNeed = need().goal(GRADE10_EXAM, "Luyện thi vào 10").textbook(null).examProvince(HA_NOI).mode("ONLINE")
        .location(null, null).build();
    var tutor = tutor(UUID.randomUUID(), "ONLINE", null, List.of(), List.of(slot(2, 18, 22)), BigDecimal.valueOf(5), 10,
        offering().goals(goal(GRADE10_EXAM, false, false)).examProvinces(HA_NOI).build());
    var score = scorer.score(examNeed, tutor).orElseThrow();
    assertTrue(score.checks().stream().noneMatch(check -> check.criterion() == FitCriterion.TEXTBOOK));
    assertEquals(new BigDecimal("100.00"), score.total());
  }

  @Test
  @DisplayName("Tổng điểm = trung bình có trọng số các tiêu chí áp dụng (lệch lớp liền kề, chưa có đánh giá)")
  void weightedAverageOfApplicableCriteria() {
    var tutor = tutor("BOTH", HA_NOI, List.of(CAU_GIAY), List.of(slot(2, 18, 22)), offering().levels(LEVEL_8).build());
    var score = scorer.score(need().build(), tutor).orElseThrow();
    // level 50*15 + goal 100*20 + schedule 100*20 + textbook 100*10 + budget 100*10 + location 100*10 + rating 35*15
    assertEquals(new BigDecimal("82.75"), score.total());
  }

  @ParameterizedTest
  @CsvSource({"5.0,10,100.00", "5.0,1,60.00", "4.0,3,64.00", ",0,35.00"})
  @DisplayName("Đánh giá dùng độ tin cậy theo số lượt; chưa có đánh giá tính 35")
  void ratingUsesReviewConfidence(BigDecimal rating, int reviews, String expected) {
    var tutor = tutor(UUID.randomUUID(), "BOTH", HA_NOI, List.of(CAU_GIAY), List.of(), rating, reviews, offering().build());
    assertEquals(new BigDecimal(expected), scorer.score(need().build(), tutor).orElseThrow().ratingFit());
  }
}

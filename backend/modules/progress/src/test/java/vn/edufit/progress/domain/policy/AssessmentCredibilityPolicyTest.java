package vn.edufit.progress.domain.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.progress.domain.model.IndependenceLevel;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneAssessment;
import vn.edufit.progress.domain.model.MilestoneCriterion;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.shared.exception.InvalidOperationException;

class AssessmentCredibilityPolicyTest {

  private final Instant now = Instant.parse("2026-11-02T10:00:00Z");

  private Milestone milestone(boolean... checked) {
    List<MilestoneCriterion> criteria = new java.util.ArrayList<>();
    for (int i = 0; i < checked.length; i++) {
      criteria.add(new MilestoneCriterion(UUID.randomUUID(), "Tiêu chí " + (i + 1), i, checked[i], checked[i] ? now : null, now));
    }
    return new Milestone(UUID.randomUUID(), UUID.randomUUID(), "Hệ phương trình", null, LocalDate.parse("2026-11-30"),
        1, MilestoneStatus.IN_PROGRESS, null, false, 0, now, criteria);
  }

  private MilestoneAssessment assessment(int level, Integer correct, Integer total, String evidence) {
    return new MilestoneAssessment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        IndependenceLevel.fromValue(level), correct, total, evidence, UUID.randomUUID(), now);
  }

  @Test
  @DisplayName("Tăng 1 mức hoặc giảm mức: không cần bằng chứng")
  void smallStepsNeedNoEvidence() {
    assertThatCode(() -> AssessmentCredibilityPolicy.check(
        IndependenceLevel.WITH_HINTS, assessment(3, null, null, null), milestone(false))).doesNotThrowAnyException();
    assertThatCode(() -> AssessmentCredibilityPolicy.check(
        IndependenceLevel.INDEPENDENT, assessment(1, null, null, null), milestone(false))).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Tăng từ 2 mức: bắt buộc đúng x/y hoặc câu bằng chứng; điểm xuất phát được miễn")
  void jumpRequiresEvidence() {
    assertThatThrownBy(() -> AssessmentCredibilityPolicy.check(
        IndependenceLevel.NOT_YET, assessment(3, null, null, null), milestone(false)))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("đúng x/y bài");
    assertThatCode(() -> AssessmentCredibilityPolicy.check(
        IndependenceLevel.NOT_YET, assessment(3, 4, 5, null), milestone(false))).doesNotThrowAnyException();
    assertThatCode(() -> AssessmentCredibilityPolicy.check(
        IndependenceLevel.NOT_YET, assessment(3, null, null, "Tự giải 3 bài liên tiếp"), milestone(false)))
        .doesNotThrowAnyException();
    assertThatCode(() -> AssessmentCredibilityPolicy.check(null, assessment(3, null, null, null), milestone(false)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Mức 4 chỉ khi tick đủ tiêu chí; cột mốc cũ không có tiêu chí được miễn")
  void masteryRequiresAllCriteria() {
    assertThatThrownBy(() -> AssessmentCredibilityPolicy.check(
        IndependenceLevel.INDEPENDENT, assessment(4, null, null, null), milestone(true, false)))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("mức 4");
    assertThatCode(() -> AssessmentCredibilityPolicy.check(
        IndependenceLevel.INDEPENDENT, assessment(4, null, null, null), milestone(true, true)))
        .doesNotThrowAnyException();
    assertThatCode(() -> AssessmentCredibilityPolicy.check(
        IndependenceLevel.INDEPENDENT, assessment(4, null, null, null), milestone())).doesNotThrowAnyException();
  }
}

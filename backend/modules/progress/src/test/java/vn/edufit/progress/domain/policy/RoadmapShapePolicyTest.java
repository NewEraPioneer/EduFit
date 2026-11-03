package vn.edufit.progress.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.policy.RoadmapShapePolicy.ExpectedDate;
import vn.edufit.shared.exception.InvalidOperationException;

class RoadmapShapePolicyTest {

  private static final LocalDate START = LocalDate.parse("2026-11-01");

  @Test
  @DisplayName("3–8 cột mốc; 2–6 buổi gợi ý; cột mốc cuối bắt buộc có hạn")
  void shapeRules() {
    assertThatThrownBy(() -> RoadmapShapePolicy.requireMilestoneCount(2)).isInstanceOf(InvalidOperationException.class);
    assertThatCode(() -> RoadmapShapePolicy.requireMilestoneCount(3)).doesNotThrowAnyException();
    assertThatCode(() -> RoadmapShapePolicy.requireMilestoneCount(8)).doesNotThrowAnyException();
    assertThatThrownBy(() -> RoadmapShapePolicy.requireMilestoneCount(9)).isInstanceOf(InvalidOperationException.class);

    assertThatThrownBy(() -> RoadmapShapePolicy.requireSuggestedSessions(1)).isInstanceOf(InvalidOperationException.class);
    assertThatThrownBy(() -> RoadmapShapePolicy.requireSuggestedSessions(7)).isInstanceOf(InvalidOperationException.class);
    assertThatCode(() -> RoadmapShapePolicy.requireSuggestedSessions(4)).doesNotThrowAnyException();

    assertThatThrownBy(() -> RoadmapShapePolicy.requireFinalTargetDate(Arrays.asList(START, null)))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("Cột mốc cuối");
    assertThatCode(() -> RoadmapShapePolicy.requireFinalTargetDate(Arrays.asList(null, null, START)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Nội suy tuyến tính các mốc giữa từ ngày lập lộ trình đến mốc có hạn kế tiếp")
  void interpolatesIntermediateDates() {
    List<ExpectedDate> expected = RoadmapShapePolicy.interpolate(START,
        Arrays.asList(null, null, LocalDate.parse("2026-12-01"), null, LocalDate.parse("2026-12-21")));

    assertThat(expected).containsExactly(
        new ExpectedDate(LocalDate.parse("2026-11-11"), true),
        new ExpectedDate(LocalDate.parse("2026-11-21"), true),
        new ExpectedDate(LocalDate.parse("2026-12-01"), false),
        new ExpectedDate(LocalDate.parse("2026-12-11"), true),
        new ExpectedDate(LocalDate.parse("2026-12-21"), false));
  }

  @Test
  @DisplayName("Mốc trống sau hạn cuối cùng đã biết không được đoán hạn (lộ trình cũ)")
  void doesNotGuessAfterLastKnownDate() {
    List<ExpectedDate> expected = RoadmapShapePolicy.interpolate(START, Arrays.asList(LocalDate.parse("2026-11-10"), null));
    assertThat(expected.get(1)).isNull();
  }

  @Test
  @DisplayName("LearningPlan: hạn hiệu lực theo thứ tự lộ trình, kèm cờ nội suy")
  void learningPlanExpectedDates() {
    Instant createdAt = Instant.parse("2026-11-01T03:00:00Z"); // 10:00 giờ Việt Nam
    Milestone a = milestone(0, null);
    Milestone b = milestone(1, LocalDate.parse("2026-11-21"));
    LearningPlan plan = new LearningPlan(UUID.randomUUID(), UUID.randomUUID(), null, 0, createdAt, createdAt, List.of(b, a));

    assertThat(plan.startDate()).isEqualTo(START);
    assertThat(plan.expectedTargetDates().get(a.getMilestoneId()))
        .isEqualTo(new ExpectedDate(LocalDate.parse("2026-11-11"), true));
    assertThat(plan.expectedTargetDates().get(b.getMilestoneId()))
        .isEqualTo(new ExpectedDate(LocalDate.parse("2026-11-21"), false));
  }

  private static Milestone milestone(int order, LocalDate target) {
    return new Milestone(UUID.randomUUID(), null, "M" + order, null, target, 1, MilestoneStatus.NOT_STARTED, null,
        false, order, Instant.parse("2026-11-01T03:00:00Z"), List.of());
  }
}

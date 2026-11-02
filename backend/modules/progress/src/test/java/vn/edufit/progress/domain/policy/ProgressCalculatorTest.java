package vn.edufit.progress.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.edufit.progress.domain.policy.ProgressCalculator.MilestoneWeight;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProgressCalculatorTest {

  @Test
  @DisplayName("Trả về null khi danh sách milestone rỗng hoặc null")
  void shouldReturnNullWhenMilestonesEmptyOrNull() {
    assertThat(ProgressCalculator.calculate(null)).isNull();
    assertThat(ProgressCalculator.calculate(Collections.emptyList())).isNull();
  }

  @Test
  @DisplayName("Trả về null khi toàn bộ milestone đều bị archived")
  void shouldReturnNullWhenAllMilestonesAreArchived() {
    List<MilestoneWeight> milestones = List.of(
        new MilestoneWeight(3, true, true),
        new MilestoneWeight(2, false, true)
    );
    assertThat(ProgressCalculator.calculate(milestones)).isNull();
  }

  @Test
  @DisplayName("Trả về 0% khi không có milestone nào ACHIEVED")
  void shouldReturnZeroWhenNoneAchieved() {
    List<MilestoneWeight> milestones = List.of(
        new MilestoneWeight(1, false, false),
        new MilestoneWeight(3, false, false)
    );
    assertThat(ProgressCalculator.calculate(milestones)).isEqualTo(0);
  }

  @Test
  @DisplayName("Trả về 100% khi toàn bộ milestone active đều ACHIEVED")
  void shouldReturn100WhenAllAchieved() {
    List<MilestoneWeight> milestones = List.of(
        new MilestoneWeight(2, true, false),
        new MilestoneWeight(3, true, false),
        new MilestoneWeight(5, false, true) // archived is ignored
    );
    assertThat(ProgressCalculator.calculate(milestones)).isEqualTo(100);
  }

  @ParameterizedTest(name = "Trọng số đạt {0}/{1} -> làm tròn HALF_UP thành {2}%")
  @CsvSource({
      "1, 3, 33",   // 1/3 = 33.333% -> 33%
      "2, 3, 67",   // 2/3 = 66.666% -> 67%
      "1, 2, 50",   // 1/2 = 50.0% -> 50%
      "1, 6, 17",   // 1/6 = 16.666% -> 17%
      "1, 8, 13",   // 1/8 = 12.5% -> 13% (HALF_UP)
      "3, 8, 38",   // 3/8 = 37.5% -> 38% (HALF_UP)
      "5, 8, 63",   // 5/8 = 62.5% -> 63% (HALF_UP)
      "7, 8, 88"    // 7/8 = 87.5% -> 88% (HALF_UP)
  })
  void shouldCalculateWithHalfUpRounding(int achievedWeight, int totalWeight, int expectedPercent) {
    int unachievedWeight = totalWeight - achievedWeight;
    List<MilestoneWeight> milestones = List.of(
        new MilestoneWeight(achievedWeight, true, false),
        new MilestoneWeight(unachievedWeight, false, false)
    );
    assertThat(ProgressCalculator.calculate(milestones)).isEqualTo(expectedPercent);
  }

  @Test
  @DisplayName("Bỏ qua milestone archived kể cả khi milestone đó đã achieved")
  void shouldIgnoreArchivedMilestones() {
    List<MilestoneWeight> milestones = List.of(
        new MilestoneWeight(2, true, false),   // active achieved: 2
        new MilestoneWeight(2, false, false),  // active unachieved: 2
        new MilestoneWeight(5, true, true)     // archived achieved: ignored!
    );
    // total active = 4, achieved active = 2 => 50%
    assertThat(ProgressCalculator.calculate(milestones)).isEqualTo(50);
  }
}

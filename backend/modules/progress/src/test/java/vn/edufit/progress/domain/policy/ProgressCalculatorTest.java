package vn.edufit.progress.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        MilestoneWeight.of(3, true, true),
        MilestoneWeight.of(2, false, true)
    );
    assertThat(ProgressCalculator.calculate(milestones)).isNull();
  }

  @Test
  @DisplayName("Trả về 0% khi không có milestone nào ACHIEVED và chưa tick tiêu chí nào")
  void shouldReturnZeroWhenNoneAchieved() {
    List<MilestoneWeight> milestones = List.of(
        MilestoneWeight.of(1, false, false),
        new MilestoneWeight(3, 0, 4, false)
    );
    assertThat(ProgressCalculator.calculate(milestones)).isEqualTo(0);
  }

  @Test
  @DisplayName("Trả về 100% khi toàn bộ milestone active đều ACHIEVED")
  void shouldReturn100WhenAllAchieved() {
    List<MilestoneWeight> milestones = List.of(
        MilestoneWeight.of(2, true, false),
        MilestoneWeight.of(3, true, false),
        MilestoneWeight.of(5, false, true) // archived is ignored
    );
    assertThat(ProgressCalculator.calculate(milestones)).isEqualTo(100);
  }

  @ParameterizedTest(name = "Trọng số đạt {0}/{1} -> làm tròn HALF_UP thành {2}%")
  @CsvSource({
      "1, 3, 33",
      "2, 3, 67",
      "1, 2, 50",
      "1, 6, 17",
      "1, 8, 13",
      "3, 8, 38",
      "5, 8, 63",
      "7, 8, 88"
  })
  void shouldCalculateWithHalfUpRounding(int achievedWeight, int totalWeight, int expectedPercent) {
    List<MilestoneWeight> milestones = List.of(
        MilestoneWeight.of(achievedWeight, true, false),
        MilestoneWeight.of(totalWeight - achievedWeight, false, false)
    );
    assertThat(ProgressCalculator.calculate(milestones)).isEqualTo(expectedPercent);
  }

  @Test
  @DisplayName("Cột mốc đang học tính theo tỉ lệ tiêu chí đã tick: (2×1 + 2×2/4) / 4 = 75%")
  void shouldCountPartialCriteriaCompletion() {
    List<MilestoneWeight> milestones = List.of(
        new MilestoneWeight(2, 1, 1, false), // ACHIEVED
        new MilestoneWeight(2, 2, 4, false)  // đã tick 2/4 tiêu chí
    );
    assertThat(ProgressCalculator.calculate(milestones)).isEqualTo(75);
  }

  @Test
  @DisplayName("Tỉ lệ một phần không chia hết vẫn làm tròn HALF_UP: 3×1/3 / 3 = 33%")
  void shouldRoundPartialCompletionHalfUp() {
    assertThat(ProgressCalculator.calculate(List.of(new MilestoneWeight(3, 1, 3, false)))).isEqualTo(33);
    assertThat(ProgressCalculator.calculate(List.of(new MilestoneWeight(1, 2, 3, false)))).isEqualTo(67);
  }

  @Test
  @DisplayName("Bỏ qua milestone archived kể cả khi milestone đó đã achieved")
  void shouldIgnoreArchivedMilestones() {
    List<MilestoneWeight> milestones = List.of(
        MilestoneWeight.of(2, true, false),
        MilestoneWeight.of(2, false, false),
        MilestoneWeight.of(5, true, true)
    );
    assertThat(ProgressCalculator.calculate(milestones)).isEqualTo(50);
  }

  @Test
  @DisplayName("Từ chối tỉ lệ hoàn thành không hợp lệ")
  void shouldRejectInvalidShares() {
    assertThatThrownBy(() -> new MilestoneWeight(1, 0, 0, false)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MilestoneWeight(1, 3, 2, false)).isInstanceOf(IllegalArgumentException.class);
  }
}

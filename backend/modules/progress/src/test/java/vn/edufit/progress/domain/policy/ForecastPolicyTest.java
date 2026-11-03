package vn.edufit.progress.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.progress.domain.policy.ForecastPolicy.Basis;
import vn.edufit.progress.domain.policy.ForecastPolicy.MilestoneEffort;

class ForecastPolicyTest {

  private static final LocalDate TODAY = LocalDate.parse("2026-11-02");

  private static MilestoneEffort done(int spent) {
    return new MilestoneEffort(true, 3, spent);
  }

  private static MilestoneEffort open(Integer suggested, int spent) {
    return new MilestoneEffort(false, suggested, spent);
  }

  @Test
  @DisplayName("Chưa đạt cột mốc nào: dùng số buổi gợi ý trừ số buổi đã học, mỗi cột mốc còn tối thiểu 1 buổi")
  void suggestedBasis() {
    ForecastPolicy.Result r = ForecastPolicy.evaluate(List.of(open(4, 1), open(3, 0), open(2, 5)), 0, TODAY);

    assertThat(r.unavailableReason()).isNull();
    assertThat(r.forecast().basis()).isEqualTo(Basis.SUGGESTED);
    assertThat(r.forecast().remainingSessions()).isEqualTo(3 + 3 + 1);
    assertThat(r.forecast().remainingMilestones()).isEqualTo(3);
    assertThat(r.forecast().estimatedFinishDate()).as("không có nhịp học thì không có ngày").isNull();
  }

  @Test
  @DisplayName("Đã đạt cột mốc: dùng nhịp thực tế (trung bình làm tròn lên) thay cho số buổi gợi ý")
  void paceBasisOverridesSuggestion() {
    // Hai cột mốc đạt mất 3 và 4 buổi → nhịp ceil(7/2) = 4 buổi/cột mốc
    ForecastPolicy.Result r = ForecastPolicy.evaluate(
        List.of(done(3), done(4), open(2, 1), open(null, 0)), 0, TODAY);

    assertThat(r.forecast().basis()).isEqualTo(Basis.PACE);
    assertThat(r.forecast().remainingSessions()).isEqualTo((4 - 1) + 4);
    assertThat(r.forecast().remainingMilestones()).isEqualTo(2);
  }

  @Test
  @DisplayName("Ngày xong dự kiến suy từ số buổi đã học trong 28 ngày gần nhất")
  void finishDateFromCadence() {
    // 8 buổi/28 ngày = 2 buổi/tuần; còn 6 buổi → 21 ngày
    ForecastPolicy.Result r = ForecastPolicy.evaluate(List.of(open(3, 0), open(3, 0), open(2, 2)), 8, TODAY);

    assertThat(r.forecast().remainingSessions()).isEqualTo(7);
    assertThat(r.forecast().estimatedFinishDate()).isEqualTo(TODAY.plusDays((7 * 28 + 7) / 8));
  }

  @Test
  @DisplayName("Thiếu số buổi gợi ý khi chưa có nhịp thực tế, hoặc đã đạt hết: không dự báo, kèm lý do")
  void unavailable() {
    assertThat(ForecastPolicy.evaluate(List.of(open(3, 0), open(null, 0)), 4, TODAY).unavailableReason())
        .contains("số buổi gợi ý");
    assertThat(ForecastPolicy.evaluate(List.of(done(2), done(3)), 4, TODAY).unavailableReason())
        .contains("Đã đạt toàn bộ");
    assertThat(ForecastPolicy.evaluate(List.of(), 4, TODAY).unavailableReason()).isNotBlank();
  }

  @Test
  @DisplayName("Cột mốc đạt nhưng không có buổi được chấm (dữ liệu cũ) không tạo nhịp thực tế")
  void achievedWithoutSessionsIsIgnoredForPace() {
    ForecastPolicy.Result r = ForecastPolicy.evaluate(List.of(done(0), open(5, 0)), 0, TODAY);

    assertThat(r.forecast().basis()).isEqualTo(Basis.SUGGESTED);
    assertThat(r.forecast().remainingSessions()).isEqualTo(5);
  }
}

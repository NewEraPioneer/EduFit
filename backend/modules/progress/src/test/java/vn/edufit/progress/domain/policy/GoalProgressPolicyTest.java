package vn.edufit.progress.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.progress.domain.model.IndependenceLevel;
import vn.edufit.progress.domain.policy.GoalProgressPolicy.CriterionSnapshot;
import vn.edufit.progress.domain.policy.GoalProgressPolicy.HighlightKind;
import vn.edufit.progress.domain.policy.GoalProgressPolicy.LevelPoint;
import vn.edufit.progress.domain.policy.GoalProgressPolicy.MilestoneSnapshot;
import vn.edufit.progress.domain.policy.GoalProgressPolicy.Stage;

class GoalProgressPolicyTest {

  private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
  private static final LocalDate TODAY = LocalDate.parse("2026-11-02");
  private static final GoalProgressPolicy.Window WINDOW = GoalProgressPolicy.window(NOW, TODAY);

  private static LevelPoint point(int daysAgo, IndependenceLevel level) {
    return new LevelPoint(UUID.randomUUID(), level, NOW.minusSeconds(daysAgo * 86400L));
  }

  private static MilestoneSnapshot milestone(String title, List<LevelPoint> points) {
    return new MilestoneSnapshot(UUID.randomUUID(), title, false, null, List.of(), points);
  }

  private static GoalProgressPolicy.Result eval(List<MilestoneSnapshot> ms) {
    return GoalProgressPolicy.evaluate(true, 40, 20, ms, "Hệ phương trình", WINDOW);
  }

  @Test
  @DisplayName("Cửa sổ 30 ngày cuốn chiếu: 02/11 → 03/10")
  void rollingWindow() {
    assertThat(WINDOW.startDate()).isEqualTo(LocalDate.parse("2026-10-03"));
  }

  @Test
  @DisplayName("S0/S1: chưa có lộ trình / chưa có lần chấm nào")
  void emptyStages() {
    assertThat(GoalProgressPolicy.evaluate(false, null, null, List.of(), null, WINDOW).stage()).isEqualTo(Stage.NO_PLAN);
    var s1 = eval(List.of(milestone("A", List.of())));
    assertThat(s1.stage()).isEqualTo(Stage.PLAN_NO_SESSIONS);
    assertThat(s1.highlight()).isEqualTo("Buổi đầu: đánh giá điểm xuất phát");
    assertThat(s1.progressDelta()).isNull();
  }

  @Test
  @DisplayName("S2: mới 1 buổi chấm → chip —, dòng phụ 'Đang thiết lập điểm xuất phát'")
  void baselinePending() {
    var r = eval(List.of(milestone("A", List.of(point(1, IndependenceLevel.WITH_HINTS)))));
    assertThat(r.stage()).isEqualTo(Stage.BASELINE_PENDING);
    assertThat(r.progressDelta()).isNull();
    assertThat(r.highlightKind()).isEqualTo(HighlightKind.BASELINE_PENDING);
  }

  @Test
  @DisplayName("S3: delta so với mức ngay trước cửa sổ, chọn cột mốc tăng nhiều nhất")
  void levelUpPicksLargestIncrease() {
    var r = eval(List.of(
        milestone("Phân số", List.of(point(40, IndependenceLevel.NOT_YET), point(2, IndependenceLevel.INDEPENDENT))),
        milestone("Hệ phương trình", List.of(point(20, IndependenceLevel.WITH_HINTS), point(1, IndependenceLevel.INDEPENDENT)))
    ));
    assertThat(r.stage()).isEqualTo(Stage.READY);
    assertThat(r.progressDelta()).isEqualTo(20);
    assertThat(r.highlightKind()).isEqualTo(HighlightKind.LEVEL_UP);
    assertThat(r.highlight()).isEqualTo("Phân số: từ “chưa làm được” → “tự làm đúng”");
  }

  @Test
  @DisplayName("Hòa mức tăng → lấy cột mốc chấm gần nhất")
  void tieTakesMostRecent() {
    var r = eval(List.of(
        milestone("Cũ", List.of(point(20, IndependenceLevel.NOT_YET), point(5, IndependenceLevel.WITH_HINTS))),
        milestone("Mới", List.of(point(20, IndependenceLevel.NOT_YET), point(1, IndependenceLevel.WITH_HINTS)))
    ));
    assertThat(r.highlight()).startsWith("Mới:");
  }

  @Test
  @DisplayName("Tụt mức → nhãn 'Cần củng cố' riêng; không có tăng thì dùng chuỗi dự phòng")
  void declineAndFallbacks() {
    MilestoneSnapshot declined = milestone("Căn thức",
        List.of(point(10, IndependenceLevel.INDEPENDENT), point(1, IndependenceLevel.WITH_HINTS)));
    var r = eval(List.of(declined));
    assertThat(r.reinforcement()).isEqualTo("Cần củng cố: Căn thức");
    assertThat(r.highlightKind()).isEqualTo(HighlightKind.NO_CHANGE);
    assertThat(r.highlight()).isEqualTo("Chưa có thay đổi mức trong 30 ngày. Đang tập trung: Hệ phương trình");

    MilestoneSnapshot justChecked = new MilestoneSnapshot(UUID.randomUUID(), "Hàm số", false, null,
        List.of(new CriterionSnapshot("Vẽ đúng đồ thị", true, NOW.minusSeconds(3600))),
        List.of(point(50, IndependenceLevel.INDEPENDENT), point(40, IndependenceLevel.INDEPENDENT)));
    assertThat(eval(List.of(justChecked)).highlight()).isEqualTo("Vừa đạt tiêu chí “Vẽ đúng đồ thị” (Hàm số)");

    MilestoneSnapshot justAchieved = new MilestoneSnapshot(UUID.randomUUID(), "Hàm số", true, TODAY.minusDays(3),
        List.of(), List.of(point(50, IndependenceLevel.INDEPENDENT), point(40, IndependenceLevel.INDEPENDENT)));
    assertThat(eval(List.of(justAchieved)).highlight()).isEqualTo("Vừa đạt cột mốc “Hàm số”");
  }

  @Test
  @DisplayName("Mức đầu vào = lần chấm đầu, mức hiện tại = lần chấm cuối (gộp theo buổi)")
  void levelSummary() {
    UUID sameSession = UUID.randomUUID();
    var summary = GoalProgressPolicy.summarizeLevels(List.of(
        point(10, IndependenceLevel.NOT_YET),
        new LevelPoint(sameSession, IndependenceLevel.WITH_HINTS, NOW.minusSeconds(7200)),
        new LevelPoint(sameSession, IndependenceLevel.INDEPENDENT, NOW.minusSeconds(3600))
    ));
    assertThat(summary.startLevel()).isEqualTo(IndependenceLevel.NOT_YET);
    assertThat(summary.currentLevel()).isEqualTo(IndependenceLevel.INDEPENDENT);
    assertThat(summary.sessionCount()).isEqualTo(2);
  }
}

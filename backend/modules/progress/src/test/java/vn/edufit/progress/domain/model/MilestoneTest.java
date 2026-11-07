package vn.edufit.progress.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.shared.exception.InvalidOperationException;

class MilestoneTest {

  private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");
  private static final Instant T1 = Instant.parse("2026-10-10T08:00:00Z");
  private static final LocalDate TODAY = LocalDate.parse("2026-10-20");

  private static Milestone newMilestone(List<String> criteria) {
    return Milestone.createNew(UUID.randomUUID(), "Hệ phương trình", null, TODAY.plusDays(10), 2, 0, criteria, Milestone.Planning.NONE, TODAY, T0);
  }

  @Test
  @DisplayName("Tạo mới bắt buộc 1–4 tiêu chí đạt")
  void createNew_requiresOneToFourCriteria() {
    assertThatThrownBy(() -> newMilestone(List.of())).isInstanceOf(InvalidOperationException.class);
    assertThatThrownBy(() -> newMilestone(List.of("a", "b", "c", "d", "e"))).isInstanceOf(InvalidOperationException.class);
    assertThat(newMilestone(List.of("a")).getCriteria()).hasSize(1);
  }

  @Test
  @DisplayName("Tick tiêu chí ghi thời điểm, tỉ lệ hoàn thành = tick/tổng và dựng lại được theo mốc thời gian")
  void applyCriteriaChecks_tracksCompletionOverTime() {
    Milestone m = newMilestone(List.of("a", "b", "c", "d"));
    UUID first = m.getCriteria().get(0).getCriterionId();
    UUID second = m.getCriteria().get(1).getCriterionId();

    assertThat(m.applyCriteriaChecks(List.of(first), T0)).isTrue();
    assertThat(m.applyCriteriaChecks(List.of(first, second), T1)).isTrue();
    assertThat(m.applyCriteriaChecks(List.of(first, second), T1)).isFalse();

    assertThat(m.completionShare()).isEqualTo(new Milestone.CompletionShare(2, 4));
    assertThat(m.completionShareAt(T0, LocalDate.parse("2026-10-01"))).isEqualTo(new Milestone.CompletionShare(1, 4));
    assertThat(m.getCriteria().get(0).getCheckedAt()).isEqualTo(T0);
  }

  @Test
  @DisplayName("Không tick tiêu chí không thuộc cột mốc")
  void applyCriteriaChecks_rejectsForeignCriterion() {
    Milestone m = newMilestone(List.of("a"));
    assertThatThrownBy(() -> m.applyCriteriaChecks(Set.of(UUID.randomUUID()), T0))
        .isInstanceOf(InvalidOperationException.class);
  }

  @Test
  @DisplayName("Chỉ chốt đạt khi tick đủ tiêu chí; cột mốc cũ chưa có tiêu chí được miễn")
  void achieve_requiresAllCriteriaChecked() {
    Milestone m = newMilestone(List.of("a", "b"));
    assertThatThrownBy(() -> m.achieve(null, TODAY)).isInstanceOf(InvalidOperationException.class);

    m.applyCriteriaChecks(m.getCriteria().stream().map(MilestoneCriterion::getCriterionId).toList(), T1);
    m.achieve(null, TODAY);
    assertThat(m.getStatus()).isEqualTo(MilestoneStatus.ACHIEVED);
    assertThat(m.completionShare()).isEqualTo(new Milestone.CompletionShare(1, 1));

    Milestone legacy = new Milestone(UUID.randomUUID(), null, "Cũ", null, null, 1,
        MilestoneStatus.IN_PROGRESS, null, false, 0, T0, List.of());
    legacy.achieve(TODAY, TODAY);
    assertThat(legacy.getStatus()).isEqualTo(MilestoneStatus.ACHIEVED);
  }

  @Test
  @DisplayName("Cột mốc đã đạt khóa tiêu chí; mở lại cần lý do và trả về ngày đạt cũ")
  void achievedMilestone_locksCriteria_andReopenNeedsReason() {
    Milestone m = newMilestone(List.of("a"));
    UUID only = m.getCriteria().getFirst().getCriterionId();
    m.applyCriteriaChecks(List.of(only), T1);
    m.achieve(LocalDate.parse("2026-10-15"), TODAY);

    assertThatThrownBy(() -> m.applyCriteriaChecks(List.of(), T1)).isInstanceOf(InvalidOperationException.class);
    assertThatThrownBy(() -> m.reopen(" ")).isInstanceOf(InvalidOperationException.class);

    assertThat(m.reopen("Chưa vững")).isEqualTo(LocalDate.parse("2026-10-15"));
    assertThat(m.getStatus()).isEqualTo(MilestoneStatus.IN_PROGRESS);
    assertThat(m.getAchievedDate()).isNull();
  }

  @Test
  @DisplayName("startProgress chỉ chuyển NOT_STARTED → IN_PROGRESS")
  void startProgress_onlyFromNotStarted() {
    Milestone m = newMilestone(List.of("a"));
    assertThat(m.startProgress()).isTrue();
    assertThat(m.getStatus()).isEqualTo(MilestoneStatus.IN_PROGRESS);
    assertThat(m.startProgress()).isFalse();
  }

  @Test
  @DisplayName("Sửa nội dung tiêu chí thì bỏ tick tiêu chí đó, giữ tick các tiêu chí không đổi")
  void updateDetails_resetsTickOnlyForChangedCriteria() {
    Milestone m = newMilestone(List.of("a", "b"));
    UUID a = m.getCriteria().get(0).getCriterionId();
    UUID b = m.getCriteria().get(1).getCriterionId();
    m.applyCriteriaChecks(List.of(a, b), T0);

    boolean changed = m.updateDetails("Hệ phương trình", null, m.getTargetDate(), 2, 0,
        List.of(new Milestone.CriterionDraft(a, "a"), new Milestone.CriterionDraft(b, "b mới"),
            new Milestone.CriterionDraft(null, "c")),
        Milestone.Planning.NONE, TODAY, T1);

    assertThat(changed).isTrue();
    assertThat(m.getCriteria()).extracting(MilestoneCriterion::isChecked).containsExactly(true, false, false);
    assertThat(m.getCriteria()).extracting(MilestoneCriterion::getContent).containsExactly("a", "b mới", "c");
  }
}

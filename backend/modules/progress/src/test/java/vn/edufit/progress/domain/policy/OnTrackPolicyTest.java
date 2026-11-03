package vn.edufit.progress.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.progress.domain.policy.AttendancePolicy.AttendanceKind;
import vn.edufit.progress.domain.policy.AttendancePolicy.ClassifiedSession;
import vn.edufit.progress.domain.policy.OnTrackPolicy.MilestoneSchedule;
import vn.edufit.progress.domain.policy.OnTrackPolicy.Status;

class OnTrackPolicyTest {

  private static final ZoneId ZONE = ZoneOffset.UTC;
  private static final LocalDate TODAY = LocalDate.parse("2026-11-02");
  private static final LocalDate PLAN_START = LocalDate.parse("2026-09-01");

  private static MilestoneSchedule open(String title, LocalDate due) {
    return new MilestoneSchedule(title, due, false, null);
  }

  private static MilestoneSchedule achieved(String title, LocalDate due, LocalDate on) {
    return new MilestoneSchedule(title, due, true, on);
  }

  private static OnTrackPolicy.Result eval(List<MilestoneSchedule> ms, boolean started, List<ClassifiedSession> sessions) {
    return OnTrackPolicy.evaluate(true, ms, started, sessions, PLAN_START, TODAY, ZONE);
  }

  @Test
  @DisplayName("Chưa có lộ trình → NO_PLAN")
  void noPlan() {
    var r = OnTrackPolicy.evaluate(false, List.of(), false, List.of(), TODAY, TODAY, ZONE);
    assertThat(r.status()).isEqualTo(Status.NO_PLAN);
    assertThat(r.explanation()).isEqualTo("Gia sư chưa lập lộ trình");
  }

  @Test
  @DisplayName("Không có cột mốc nào có hạn → NOT_ENOUGH_DATA")
  void noDates() {
    assertThat(eval(List.of(open("A", null)), true, List.of()).status()).isEqualTo(Status.NOT_ENOUGH_DATA);
  }

  @Test
  @DisplayName("Quá hạn 2 ngày → NEEDS_ATTENTION kèm câu giải thích")
  void overdueWithinGrace() {
    var r = eval(List.of(open("Hệ phương trình", TODAY.minusDays(2))), true, List.of());
    assertThat(r.status()).isEqualTo(Status.NEEDS_ATTENTION);
    assertThat(r.explanation()).isEqualTo("Cột mốc “Hệ phương trình” trễ 2 ngày so với kế hoạch");
  }

  @Test
  @DisplayName("Quá hạn đúng 7 ngày vẫn trong ân hạn, 8 ngày → BEHIND")
  void graceBoundary() {
    assertThat(eval(List.of(open("A", TODAY.minusDays(7))), true, List.of()).status()).isEqualTo(Status.NEEDS_ATTENTION);
    assertThat(eval(List.of(open("A", TODAY.minusDays(8))), true, List.of()).status()).isEqualTo(Status.BEHIND);
  }

  @Test
  @DisplayName("Từ 2 cột mốc quá hạn → BEHIND")
  void twoOverdue() {
    var r = eval(List.of(open("A", TODAY.minusDays(3)), open("B", TODAY.minusDays(1))), true, List.of());
    assertThat(r.status()).isEqualTo(Status.BEHIND);
    assertThat(r.explanation()).startsWith("2 cột mốc quá hạn, trễ nhất là “A” (3 ngày)");
  }

  @Test
  @DisplayName("Trễ có buổi nghỉ trong giai đoạn → ghi rõ nguyên nhân")
  void explainsMissedSessions() {
    List<ClassifiedSession> sessions = List.of(
        new ClassifiedSession(UUID.randomUUID(), TODAY.minusDays(5).atStartOfDay(ZONE).toInstant(), AttendanceKind.ABSENT),
        new ClassifiedSession(UUID.randomUUID(), TODAY.minusDays(4).atStartOfDay(ZONE).toInstant(), AttendanceKind.CANCELLED_BY_TUTOR),
        new ClassifiedSession(UUID.randomUUID(), PLAN_START.minusDays(1).atStartOfDay(ZONE).toInstant(), AttendanceKind.ABSENT)
    );
    var r = eval(List.of(open("A", TODAY.minusDays(2))), true, sessions);
    assertThat(r.explanation()).endsWith("Trong giai đoạn này có 1 buổi vắng, 1 buổi gia sư dời");
  }

  @Test
  @DisplayName("Cột mốc kế tiếp đã đạt trước hạn → AHEAD")
  void ahead() {
    var r = eval(List.of(achieved("A", TODAY.plusDays(5), TODAY.minusDays(1)), open("B", TODAY.plusDays(20))), true, List.of());
    assertThat(r.status()).isEqualTo(Status.AHEAD);
    assertThat(r.explanation()).isEqualTo("Cột mốc “A” đã đạt sớm 6 ngày so với hạn");
  }

  @Test
  @DisplayName("Không quá hạn → ON_TRACK, nêu cột mốc kế tiếp")
  void onTrack() {
    var r = eval(List.of(achieved("A", TODAY.minusDays(5), TODAY.minusDays(6)), open("B", LocalDate.parse("2026-11-20"))), true, List.of());
    assertThat(r.status()).isEqualTo(Status.ON_TRACK);
    assertThat(r.explanation()).isEqualTo("Cột mốc kế tiếp “B”, hạn 20/11/2026");
  }

  @Test
  @DisplayName("Có lộ trình nhưng chưa học buổi nào → NOT_STARTED")
  void notStarted() {
    var r = eval(List.of(open("A", TODAY.plusDays(10))), false, List.of());
    assertThat(r.status()).isEqualTo(Status.NOT_STARTED);
    assertThat(r.explanation()).isEqualTo("Chưa đến hạn cột mốc đầu");
  }
}

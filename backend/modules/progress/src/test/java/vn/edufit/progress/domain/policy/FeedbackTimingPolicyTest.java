package vn.edufit.progress.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.progress.domain.policy.FeedbackTimingPolicy.ReminderStage;

class FeedbackTimingPolicyTest {

  private final Instant endAt = Instant.parse("2026-11-01T11:00:00Z");

  private Instant after(Duration d) {
    return endAt.plus(d);
  }

  @Test
  @DisplayName("Nhắc lúc +12h, rồi +24h; trước +12h và từ +48h thì không nhắc")
  void dueStage_followsTwelveAndTwentyFourHourMarks() {
    Set<ReminderStage> none = Set.of();
    assertThat(FeedbackTimingPolicy.dueStage(endAt, after(Duration.ofHours(11).plusMinutes(59)), none)).isEmpty();
    assertThat(FeedbackTimingPolicy.dueStage(endAt, after(Duration.ofHours(12)), none)).contains(ReminderStage.H12);
    assertThat(FeedbackTimingPolicy.dueStage(endAt, after(Duration.ofHours(18)), EnumSet.of(ReminderStage.H12))).isEmpty();
    assertThat(FeedbackTimingPolicy.dueStage(endAt, after(Duration.ofHours(24)), EnumSet.of(ReminderStage.H12)))
        .contains(ReminderStage.H24);
    assertThat(FeedbackTimingPolicy.dueStage(endAt, after(Duration.ofHours(30)), EnumSet.allOf(ReminderStage.class)))
        .isEmpty();
    assertThat(FeedbackTimingPolicy.dueStage(endAt, after(Duration.ofHours(48)), none)).isEmpty();
  }

  @Test
  @DisplayName("Job lỡ mốc +12h thì chỉ gửi mốc +24h, không dồn hai lần nhắc")
  void dueStage_skipsMissedEarlierStage() {
    assertThat(FeedbackTimingPolicy.dueStage(endAt, after(Duration.ofHours(25)), Set.of())).contains(ReminderStage.H24);
  }

  @Test
  @DisplayName("Bổ sung muộn khi ghi sau đúng 48 giờ kể từ lúc buổi học kết thúc")
  void isLate_afterFortyEightHours() {
    assertThat(FeedbackTimingPolicy.isLate(endAt, after(Duration.ofHours(48)))).isFalse();
    assertThat(FeedbackTimingPolicy.isLate(endAt, after(Duration.ofHours(48).plusSeconds(1)))).isTrue();
  }
}

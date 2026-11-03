package vn.edufit.progress.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PeriodReportPolicyTest {

  private static final LocalDate START = LocalDate.parse("2026-09-01");

  @Test
  @DisplayName("Chưa đủ 30 ngày kể từ ngày bắt đầu lớp: chưa có kỳ nào kết thúc")
  void noCompletedPeriodBeforeThirtyDays() {
    assertThat(PeriodReportPolicy.latestCompleted(START, START.plusDays(29))).isEmpty();
  }

  @Test
  @DisplayName("Kỳ k gồm 30 ngày [start + 30(k-1), start + 30k - 1], lập từ ngày kế tiếp")
  void periodBoundaries() {
    PeriodReportPolicy.Period first = PeriodReportPolicy.latestCompleted(START, START.plusDays(30)).orElseThrow();
    assertThat(first.index()).isEqualTo(1);
    assertThat(first.start()).isEqualTo(START);
    assertThat(first.end()).isEqualTo(START.plusDays(29));
    assertThat(first.contains(START.plusDays(29))).isTrue();
    assertThat(first.contains(START.plusDays(30))).isFalse();

    PeriodReportPolicy.Period second = PeriodReportPolicy.latestCompleted(START, START.plusDays(75)).orElseThrow();
    assertThat(second.index()).isEqualTo(2);
    assertThat(second.start()).isEqualTo(START.plusDays(30));
    assertThat(second.end()).isEqualTo(START.plusDays(59));
  }
}

package vn.edufit.scheduling.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import vn.edufit.scheduling.domain.policy.LateCancelPolicy;

/**
 * Kiểm thử đơn vị cho {@link LateCancelPolicy} (BR-46).
 */
@DisplayName("Unit Test POJO: Kiểm thử quy tắc phân loại hủy trễ BR-46")
class LateCancelPolicyTest {

  private LateCancelPolicy lateCancelPolicy;
  private Instant sessionStartAt;

  @BeforeEach
  void setUp() {
    lateCancelPolicy = new LateCancelPolicy();
    sessionStartAt = Instant.parse("2026-10-25T14:00:00Z");
  }

  @ParameterizedTest(name = "[{0}] {3}: hủy trước {1}h => Hủy trễ: {2}")
  @CsvFileSource(resources = "/testdata/late-cancel-rules.csv", numLinesToSkip = 1)
  @DisplayName("BR-46: Kiểm thử tham số hóa ngưỡng 12 giờ hủy trễ")
  void shouldEvaluateLateCancelCorrectly(
      String testCaseId,
      long hoursBeforeStart,
      boolean expectedLateCancel,
      String description
  ) {
    Instant cancelTime = sessionStartAt.minus(hoursBeforeStart, ChronoUnit.HOURS);
    boolean actualLateCancel = lateCancelPolicy.isLateCancel(sessionStartAt, cancelTime);
    assertEquals(expectedLateCancel, actualLateCancel, description);
  }
}

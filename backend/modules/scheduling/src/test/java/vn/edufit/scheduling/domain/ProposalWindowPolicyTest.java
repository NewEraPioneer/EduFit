package vn.edufit.scheduling.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import vn.edufit.scheduling.domain.policy.ProposalWindowPolicy;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Kiểm thử đơn vị cho {@link ProposalWindowPolicy} (BR-41, BR-43).
 */
@DisplayName("Unit Test POJO: Kiểm thử khung thời gian đề xuất buổi học")
class ProposalWindowPolicyTest {

  private ProposalWindowPolicy windowPolicy;
  private Instant fixedNow;

  @BeforeEach
  void setUp() {
    windowPolicy = new ProposalWindowPolicy();
    fixedNow = Instant.parse("2026-10-25T08:00:00Z");
  }

  @ParameterizedTest(name = "[{0}] {4}: bắt đầu sau {1}m, thời lượng {2}m => Hợp lệ: {3}")
  @CsvFileSource(resources = "/testdata/proposal-window-rules.csv", numLinesToSkip = 1)
  @DisplayName("BR-41: Kiểm thử thời lượng [30..240]m và điều kiện tương lai")
  void shouldValidateProposalWindowCorrectly(
      String testCaseId,
      long startMinutesFromNow,
      long durationMinutes,
      boolean expectedValid,
      String description
  ) {
    Instant startAt = fixedNow.plus(startMinutesFromNow, ChronoUnit.MINUTES);
    Instant endAt = startAt.plus(durationMinutes, ChronoUnit.MINUTES);

    if (expectedValid) {
      assertDoesNotThrow(() -> windowPolicy.validateWindow(startAt, endAt, fixedNow), description);
    } else {
      assertThrows(
          InvalidOperationException.class,
          () -> windowPolicy.validateWindow(startAt, endAt, fixedNow),
          description
      );
    }
  }

  @Test
  @DisplayName("BR-43: Thời hạn phản hồi expiresAt lấy giá trị nhỏ hơn giữa (now + 3 ngày) và startAt")
  void shouldCalculateExpiresAtCorrectly() {
    // Trường hợp 1: Buổi học diễn ra sau 5 ngày -> expiresAt = now + 3 ngày
    Instant farStartAt = fixedNow.plus(5, ChronoUnit.DAYS);
    Instant expectedExpiry1 = fixedNow.plus(3, ChronoUnit.DAYS);
    assertEquals(expectedExpiry1, windowPolicy.calculateExpiresAt(farStartAt, fixedNow));

    // Trường hợp 2: Buổi học diễn ra sau 1 ngày -> expiresAt = startAt (vì startAt < now + 3 ngày)
    Instant nearStartAt = fixedNow.plus(1, ChronoUnit.DAYS);
    assertEquals(nearStartAt, windowPolicy.calculateExpiresAt(nearStartAt, fixedNow));
  }
}

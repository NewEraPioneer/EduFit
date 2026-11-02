package vn.edufit.scheduling.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import vn.edufit.scheduling.domain.policy.ConflictPolicy;
import vn.edufit.shared.domain.valueobject.TimeInterval;

/**
 * Kiểm thử đơn vị thuần túy cho thuật toán chống trùng lịch {@link ConflictPolicy} (BR-42).
 *
 * <p>Quy chuẩn kiểm thử:
 * <ul>
 *   <li><b>POJO Unit Test:</b> Không khởi động Spring Context, tốc độ thực thi tính bằng mili-giây.</li>
 *   <li><b>Data-Driven Testing (DDT):</b> Sử dụng {@link CsvFileSource} đọc toàn bộ 8 kịch bản biên
 *       từ tệp ngoại vi {@code conflict-policy-intervals.csv}.</li>
 * </ul>
 */
@DisplayName("Unit Test POJO: Kiểm thử thuật toán chống trùng lịch BR-42")
class ConflictPolicyTest {

  private ConflictPolicy conflictPolicy;

  @BeforeEach
  void setUp() {
    conflictPolicy = new ConflictPolicy();
  }

  @ParameterizedTest(name = "[{0}] {6}: ({1}->{2}) vs ({3}->{4}) => Xung đột: {5}")
  @CsvFileSource(resources = "/testdata/conflict-policy-intervals.csv", numLinesToSkip = 1)
  @DisplayName("BR-42: Kiểm thử tham số hóa toàn diện 8 ca biên giao thoa thời gian nửa mở [start, end)")
  void shouldEvaluateIntervalConflictCorrectly(
      String testCaseId,
      String existingStart,
      String existingEnd,
      String newStart,
      String newEnd,
      boolean expectedConflict,
      String description
  ) {
    LocalDate date = LocalDate.of(2026, 10, 25);
    TimeInterval slot1 = new TimeInterval(
        date.atTime(LocalTime.parse(existingStart)),
        date.atTime(LocalTime.parse(existingEnd))
    );
    TimeInterval slot2 = new TimeInterval(
        date.atTime(LocalTime.parse(newStart)),
        date.atTime(LocalTime.parse(newEnd))
    );

    // 1. Kiểm thử phương thức nhận TimeInterval
    boolean actualConflict = conflictPolicy.isOverlapping(slot1, slot2);
    assertEquals(expectedConflict, actualConflict, description);

    // 2. Kiểm thử phương thức tiện ích nhận Instant
    boolean actualInstantConflict = conflictPolicy.isOverlapping(
        date.atTime(LocalTime.parse(existingStart)).toInstant(ZoneOffset.UTC),
        date.atTime(LocalTime.parse(existingEnd)).toInstant(ZoneOffset.UTC),
        date.atTime(LocalTime.parse(newStart)).toInstant(ZoneOffset.UTC),
        date.atTime(LocalTime.parse(newEnd)).toInstant(ZoneOffset.UTC)
    );
    assertEquals(expectedConflict, actualInstantConflict, description + " (Instant overload)");
  }
}

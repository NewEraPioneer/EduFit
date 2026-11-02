package vn.edufit.scheduling.domain.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Value Object đại diện cho một khung giờ học định kỳ trong tuần (Weekly Slot).
 *
 * <p>Quy tắc bất biến:
 * <ul>
 *   <li>{@code dayOfWeek}, {@code startTime}, {@code endTime} không được null.</li>
 *   <li>{@code endTime} phải sau {@code startTime}.</li>
 *   <li>Thời lượng ca học phải từ 30 đến 240 phút (BR-41).</li>
 * </ul>
 */
public record WeeklySlot(
    DayOfWeek dayOfWeek,
    LocalTime startTime,
    LocalTime endTime,
    String raw
) {

  public WeeklySlot {
    Objects.requireNonNull(dayOfWeek, "dayOfWeek không được null");
    Objects.requireNonNull(startTime, "startTime không được null");
    Objects.requireNonNull(endTime, "endTime không được null");

    if (!endTime.isAfter(startTime)) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Thời điểm kết thúc (" + endTime + ") phải sau thời điểm bắt đầu (" + startTime + ")"
      );
    }

    long minutes = Duration.between(startTime, endTime).toMinutes();
    if (minutes < 30 || minutes > 240) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Thời lượng buổi học phải từ 30 đến 240 phút. Hiện tại: " + minutes + " phút"
      );
    }
  }

  /**
   * Kiểm tra xem 2 khung giờ trong tuần có bị trùng nhau hay không.
   * Chỉ có thể trùng nếu cùng rơi vào một thứ trong tuần và giao thoa thời gian [startTime, endTime).
   */
  public boolean overlapsWith(WeeklySlot other) {
    if (other == null || this.dayOfWeek != other.dayOfWeek) {
      return false;
    }
    // Giao thoa nửa khoảng mở: S1 < E2 && S2 < E1
    return this.startTime.isBefore(other.endTime) && other.startTime.isBefore(this.endTime);
  }
}

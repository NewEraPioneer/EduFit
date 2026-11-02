package vn.edufit.scheduling.domain.policy;

import java.time.Instant;
import java.util.Objects;
import vn.edufit.shared.domain.valueobject.TimeInterval;

/**
 * Chính sách miền (Domain Policy) chịu trách nhiệm kiểm tra xung đột thời gian (BR-42).
 *
 * <p>Mục đích thiết kế & Nguyên tắc toán học:
 * <ul>
 *   <li><b>Java POJO Thuần:</b> Độc lập hoàn toàn với Spring Framework và Database.</li>
 *   <li><b>Quy tắc nửa khoảng mở {@code [start, end)}:</b> Hai khoảng thời gian giao nhau khi và chỉ khi:
 *       <pre>{@code
 *         (S1 < E2) VÀ (S2 < E1)
 *       }</pre>
 *   </li>
 *   <li><b>Trường hợp tiếp giáp biên:</b> Nếu khoảng 1 kết thúc lúc 10:00 ({@code E1 = 10:00}) và khoảng 2
 *       bắt đầu lúc 10:00 ({@code S2 = 10:00}), thì {@code !(S1 < E2 && S2 < E1)} vì {@code S2 < E1} sai.
 *       Do đó <b>KHÔNG BỊ COI LÀ TRÙNG LỊCH</b>. Điều này cho phép học sinh và gia sư xếp các ca học
 *       liền kề nhau mà không bị báo lỗi giả.</li>
 *   <li><b>Yêu cầu NFR-17 (Double-booking Protection):</b> Đây là chốt chặn nghiệp vụ ở tầng RAM
 *       trước khi chạm xuống PostgreSQL GiST Exclusion Constraint.</li>
 * </ul>
 */
public class ConflictPolicy {

  /**
   * Kiểm tra xem hai khoảng thời gian {@link TimeInterval} có bị giao nhau (xung đột) hay không.
   *
   * @param slot1 Khoảng thời gian thứ nhất.
   * @param slot2 Khoảng thời gian thứ hai.
   * @return {@code true} nếu bị chồng lấn (xung đột), ngược lại {@code false}.
   */
  public static boolean isOverlapping(TimeInterval slot1, TimeInterval slot2) {
    Objects.requireNonNull(slot1, "Khoảng thời gian slot1 không được null");
    Objects.requireNonNull(slot2, "Khoảng thời gian slot2 không được null");
    return slot1.overlapsWith(slot2);
  }

  /**
   * Phương thức tiện ích kiểm tra giao thoa trực tiếp từ 4 mốc thời gian {@link Instant}.
   *
   * @param start1 Mốc bắt đầu khoảng 1.
   * @param end1   Mốc kết thúc khoảng 1.
   * @param start2 Mốc bắt đầu khoảng 2.
   * @param end2   Mốc kết thúc khoảng 2.
   * @return {@code true} nếu hai khoảng thời gian giao nhau, ngược lại {@code false}.
   */
  public static boolean isOverlapping(Instant start1, Instant end1, Instant start2, Instant end2) {
    Objects.requireNonNull(start1, "start1 không được null");
    Objects.requireNonNull(end1, "end1 không được null");
    Objects.requireNonNull(start2, "start2 không được null");
    Objects.requireNonNull(end2, "end2 không được null");

    if (!end1.isAfter(start1)) {
      throw new IllegalArgumentException("end1 phải sau start1: " + start1 + " -> " + end1);
    }
    if (!end2.isAfter(start2)) {
      throw new IllegalArgumentException("end2 phải sau start2: " + start2 + " -> " + end2);
    }

    // Công thức nửa khoảng mở [start, end): (S1 < E2) && (S2 < E1)
    return start1.isBefore(end2) && start2.isBefore(end1);
  }
}

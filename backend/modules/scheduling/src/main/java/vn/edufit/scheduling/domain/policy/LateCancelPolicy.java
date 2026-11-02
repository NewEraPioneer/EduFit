package vn.edufit.scheduling.domain.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Chính sách phân loại Hủy Trễ buổi học (BR-46).
 *
 * <p>Căn cứ nghiệp vụ:
 * <ul>
 *   <li><b>Quy định 12 giờ:</b> Nếu hành động hủy diễn ra trong vòng <b>dưới 12 giờ</b> trước giờ bắt đầu
 *       buổi học ({@code startAt - now < 12 hours}), hệ thống ghi nhận cờ {@code isLateCancel = true}.</li>
 *   <li><b>Ý nghĩa:</b> Cờ {@code isLateCancel} được dùng để thống kê mức độ uy tín của Gia sư và Học sinh,
 *       ngăn chặn tình trạng bùng lịch sát giờ gây thiệt hại cho đối tác.</li>
 * </ul>
 */
public class LateCancelPolicy {

  /** Ngưỡng thời gian hủy trễ tính bằng giờ (12 giờ). */
  public static final long LATE_CANCEL_THRESHOLD_HOURS = 12;

  /**
   * Đánh giá xem hành động hủy tại thời điểm {@code now} có bị tính là Hủy Trễ hay không.
   *
   * @param startAt Mốc bắt đầu của buổi học.
   * @param now     Thời điểm thực hiện hủy buổi học.
   * @return {@code true} nếu khoảng cách từ {@code now} đến {@code startAt} nhỏ hơn 12 giờ.
   */
  public static boolean isLateCancel(Instant startAt, Instant now) {
    Objects.requireNonNull(startAt, "startAt không được null");
    Objects.requireNonNull(now, "now không được null");

    // Nếu thời điểm hủy đã vượt quá thời gian bắt đầu học, chắc chắn là trễ
    if (!now.isBefore(startAt)) {
      return true;
    }

    Duration remainingTime = Duration.between(now, startAt);
    return remainingTime.toHours() < LATE_CANCEL_THRESHOLD_HOURS;
  }
}

package vn.edufit.scheduling.domain.policy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Chính sách kiểm tra khung thời gian đề xuất buổi học và thời hạn phản hồi.
 *
 * <p>Căn cứ nghiệp vụ:
 * <ul>
 *   <li><b>BR-41 (Thời lượng buổi học):</b> Thời lượng buổi học hợp lệ phải từ 30 phút đến 240 phút (4 tiếng).
 *       Khớp với ràng buộc DB {@code chk_session_time CHECK (end_at > start_at AND end_at - start_at BETWEEN interval '30 minutes' AND interval '240 minutes')}.</li>
 *   <li><b>Thời điểm bắt đầu:</b> Buổi học đề xuất phải bắt đầu trong tương lai so với thời điểm gửi yêu cầu.</li>
 *   <li><b>BR-43 (Thời hạn phản hồi đề xuất):</b> Thời hạn {@code expiresAt} được tính bằng giá trị nhỏ hơn
 *       giữa {@code (now + 3 ngày)} và mốc {@code startAt} của buổi học (tránh tình trạng buổi học đã đến giờ
 *       mà đề xuất vẫn chưa hết hạn).</li>
 * </ul>
 */
public class ProposalWindowPolicy {

  /** Thời lượng tối thiểu của một buổi học (30 phút). */
  public static final long MIN_DURATION_MINUTES = 30;

  /** Thời lượng tối đa của một buổi học (240 phút = 4 tiếng). */
  public static final long MAX_DURATION_MINUTES = 240;

  /** Số ngày hiệu lực tối đa của một đề xuất buổi học (3 ngày = 72 giờ). */
  public static final long PROPOSAL_EXPIRY_DAYS = 3;

  /**
   * Kiểm tra tính hợp lệ của khung thời gian đề xuất.
   *
   * @param startAt Thời điểm bắt đầu dự kiến.
   * @param endAt   Thời điểm kết thúc dự kiến.
   * @param now     Thời điểm hiện tại từ Clock.
   * @throws InvalidOperationException Nếu vi phạm bất kỳ điều kiện nào về thời gian.
   */
  public static void validateWindow(Instant startAt, Instant endAt, Instant now) {
    if (startAt == null || endAt == null) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Thời gian bắt đầu và kết thúc buổi học không được phép null"
      );
    }
    if (!startAt.isAfter(now)) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Thời gian bắt đầu buổi học phải ở trong tương lai"
      );
    }
    validateDuration(startAt, endAt);
  }

  /**
   * Kiểm tra thời lượng buổi học [30..240] phút theo BR-41.
   */
  public static void validateDuration(Instant startAt, Instant endAt) {
    if (startAt == null || endAt == null) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Thời gian bắt đầu và kết thúc buổi học không được phép null"
      );
    }
    if (!endAt.isAfter(startAt)) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Thời gian kết thúc phải lớn hơn thời gian bắt đầu"
      );
    }

    long durationMinutes = Duration.between(startAt, endAt).toMinutes();
    if (durationMinutes < MIN_DURATION_MINUTES || durationMinutes > MAX_DURATION_MINUTES) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Thời lượng buổi học phải từ " + MIN_DURATION_MINUTES + " đến " + MAX_DURATION_MINUTES
              + " phút. Thực tế yêu cầu: " + durationMinutes + " phút"
      );
    }
  }

  /**
   * Tính toán mốc thời gian hết hạn phản hồi của đề xuất (expiresAt).
   *
   * @param startAt Mốc bắt đầu buổi học.
   * @param now     Thời điểm hiện tại từ Clock.
   * @return {@code min(now + 3 ngày, startAt)}.
   */
  public static Instant calculateExpiresAt(Instant startAt, Instant now) {
    Instant maxExpiry = now.plus(PROPOSAL_EXPIRY_DAYS, ChronoUnit.DAYS);
    return maxExpiry.isBefore(startAt) ? maxExpiry : startAt;
  }
}

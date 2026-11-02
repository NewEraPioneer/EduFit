package vn.edufit.shared.exception;

/**
 * Ngoại lệ phát sinh khi người dùng đã đăng nhập thành công nhưng không có quyền thực hiện hành động này.
 *
 * <p>Ví dụ sử dụng liên quan đến phân quyền (NFR-07):
 * <ul>
 *   <li>Học sinh cố tình gọi API duyệt hồ sơ bằng cấp (chỉ dành cho ADMIN - UC1.12).</li>
 *   <li>Phụ huynh cố tình xem kế hoạch học tập của học sinh mà mình chưa liên kết (UC5.3).</li>
 *   <li>Gia sư cố tình sửa đánh giá hoặc xóa phản hồi của người khác.</li>
 * </ul>
 *
 * <p>Khi ngoại lệ này bắn ra, tầng {@code app} sẽ tự động bắt lại và chuyển thành HTTP 403 (Forbidden).
 */
public class ForbiddenOperationException extends BaseBusinessException {

  /**
   * Khởi tạo ngoại lệ với thông điệp chi tiết, mã lỗi mặc định là {@link ErrorCode#FORBIDDEN}.
   *
   * @param message Thông điệp mô tả hành vi bị cấm.
   */
  public ForbiddenOperationException(String message) {
    super(ErrorCode.FORBIDDEN, message);
  }

  public ForbiddenOperationException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }
}

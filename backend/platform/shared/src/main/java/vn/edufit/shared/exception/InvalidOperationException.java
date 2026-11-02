package vn.edufit.shared.exception;

/**
 * Ngoại lệ phát sinh khi một hành động nghiệp vụ vi phạm quy trình chuyển trạng thái (State Machine)
 * hoặc điều kiện tiên quyết (Preconditions).
 *
 * <p>Ví dụ sử dụng liên quan đến quy tắc trạng thái (BR-39 đến BR-48 trong Scheduling & Connection):
 * <ul>
 *   <li>Cố tình bấm "Chấp nhận" một buổi học đã bị Hủy trước đó (BR-40).</li>
 *   <li>Cố tình "Đổi lịch" một buổi học đã diễn ra xong (Status = COMPLETED).</li>
 *   <li>Gia sư nộp đơn xác minh khi chưa điền xong thông tin hồ sơ cơ bản (PRE-2 của UC1.10).</li>
 * </ul>
 *
 * <p>Khi ngoại lệ này bắn ra, tầng {@code app} sẽ tự động bắt lại và chuyển thành HTTP 400 (Bad Request).
 */
public class InvalidOperationException extends BaseBusinessException {

  /**
   * Khởi tạo ngoại lệ với thông điệp chi tiết, mã lỗi mặc định là {@link ErrorCode#INVALID_OPERATION}.
   *
   * @param message Thông điệp mô tả thao tác không hợp lệ.
   */
  public InvalidOperationException(String message) {
    super(ErrorCode.INVALID_OPERATION, message);
  }

  /**
   * Khởi tạo ngoại lệ với mã lỗi cụ thể (ví dụ: PROPOSAL_EXPIRED, SESSION_ALREADY_FINALIZED) và thông điệp.
   *
   * @param errorCode Mã lỗi định danh nghiệp vụ.
   * @param message   Thông điệp mô tả thao tác không hợp lệ.
   */
  public InvalidOperationException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }

  /**
   * Khởi tạo ngoại lệ với thông điệp chi tiết và nguyên nhân gốc.
   *
   * @param message Thông điệp mô tả thao tác không hợp lệ.
   * @param cause   Nguyên nhân gốc gây ra lỗi.
   */
  public InvalidOperationException(String message, Throwable cause) {
    super(ErrorCode.INVALID_OPERATION, message, cause);
  }

  /**
   * Khởi tạo ngoại lệ với mã lỗi cụ thể, thông điệp chi tiết và nguyên nhân gốc.
   *
   * @param errorCode Mã lỗi định danh nghiệp vụ.
   * @param message   Thông điệp mô tả thao tác không hợp lệ.
   * @param cause     Nguyên nhân gốc gây ra lỗi.
   */
  public InvalidOperationException(ErrorCode errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
  }
}

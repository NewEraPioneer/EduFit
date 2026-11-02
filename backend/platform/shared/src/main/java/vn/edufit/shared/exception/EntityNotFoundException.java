package vn.edufit.shared.exception;

/**
 * Ngoại lệ phát sinh khi một thực thể (Entity) hoặc tài nguyên được yêu cầu không tìm thấy trong hệ thống.
 *
 * <p>Ví dụ sử dụng trong các Use Cases:
 * <ul>
 *   <li>Không tìm thấy tài khoản người dùng với {@code userId} được truyền vào.</li>
 *   <li>Không tìm thấy buổi học với {@code sessionId} tương ứng (UC4.3, UC4.5).</li>
 *   <li>Không tìm thấy hồ sơ gia sư (UC1.5).</li>
 * </ul>
 *
 * <p>Khi ngoại lệ này bắn ra, tầng {@code app} sẽ tự động bắt lại và chuyển thành HTTP 404 (Not Found).
 */
public class EntityNotFoundException extends BaseBusinessException {

  /**
   * Khởi tạo ngoại lệ với thông điệp chi tiết, mã lỗi mặc định là {@link ErrorCode#RESOURCE_NOT_FOUND}.
   *
   * @param message Thông điệp mô tả tài nguyên không tìm thấy.
   */
  public EntityNotFoundException(String message) {
    super(ErrorCode.RESOURCE_NOT_FOUND, message);
  }

  public EntityNotFoundException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }

  /**
   * Phương thức tiện ích tạo ngoại lệ theo tên thực thể và ID định danh.
   *
   * @param entityName Tên của thực thể (ví dụ: "User", "Session", "Profile").
   * @param id         Mã định danh của thực thể đó.
   * @return Đối tượng EntityNotFoundException với thông điệp chuẩn hóa.
   */
  public static EntityNotFoundException of(String entityName, Object id) {
    return new EntityNotFoundException(String.format("Không tìm thấy %s với mã định danh: %s", entityName, id));
  }
}

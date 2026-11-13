package vn.edufit.payment.infra;

import vn.edufit.shared.exception.InvalidOperationException;

/**
 * payOS đã trả lời rõ ràng là từ chối (HTTP 200 nhưng {@code code} khác "00").
 *
 * <p>Khác với lỗi mạng hoặc timeout: ở đây chắc chắn payOS không thực hiện yêu cầu, nên nơi gọi
 * có thể quyết định dựa trên {@link #code()} (ví dụ 231 là trùng orderCode, 101 là không tìm thấy
 * mã thanh toán). Thông điệp giữ nguyên câu "chưa xác nhận" để luồng chi tiền không đổi hành vi.
 */
public class PayosRejectedException extends InvalidOperationException {
  /** payOS báo orderCode đã được dùng cho một yêu cầu thanh toán khác. */
  public static final String DUPLICATE_ORDER = "231";
  /** payOS không có yêu cầu thanh toán nào với orderCode này. */
  public static final String NOT_FOUND = "101";

  private final String code;
  private final String description;

  public PayosRejectedException(String code, String description) {
    super("payOS chưa xác nhận kết quả. Tiền được giữ để đối soát; không tạo giao dịch khác.");
    this.code = code == null ? "" : code;
    this.description = description == null ? "" : description;
  }

  public String code() {
    return code;
  }

  public String description() {
    return description;
  }
}

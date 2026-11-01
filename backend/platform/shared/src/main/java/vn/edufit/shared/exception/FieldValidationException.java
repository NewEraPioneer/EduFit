package vn.edufit.shared.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lỗi nghiệp vụ gắn với từng trường dữ liệu (ví dụ tổ hợp Lớp/Môn/Mục tiêu không hợp lệ),
 * để frontend bôi đỏ đúng ô nhập thay vì hiển thị một thông báo chung.
 */
public class FieldValidationException extends BaseBusinessException {

  private final Map<String, String> fieldErrors;

  public FieldValidationException(Map<String, String> fieldErrors) {
    super(ErrorCode.VALIDATION_FAILED, "Dữ liệu chưa hợp lệ. Vui lòng kiểm tra các trường được đánh dấu.");
    if (fieldErrors == null || fieldErrors.isEmpty()) {
      throw new IllegalArgumentException("FieldValidationException cần ít nhất một lỗi trường.");
    }
    this.fieldErrors = Map.copyOf(new LinkedHashMap<>(fieldErrors));
  }

  public static FieldValidationException of(String field, String message) {
    return new FieldValidationException(Map.of(field, message));
  }

  public Map<String, String> getFieldErrors() {
    return fieldErrors;
  }
}

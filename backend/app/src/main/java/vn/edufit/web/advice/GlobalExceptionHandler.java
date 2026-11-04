package vn.edufit.web.advice;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import vn.edufit.shared.exception.BaseBusinessException;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.FieldValidationException;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;
import vn.edufit.shared.response.ApiError;
import vn.edufit.shared.response.FieldErrorItem;

/**
 * Bộ xử lý ngoại lệ tập trung toàn hệ thống (Global Exception Handler).
 *
 * <p>Mục đích thiết kế và giải thích kiến trúc:
 * <ul>
 *   <li><b>Cổng chuyển đổi lỗi (HTTP Error Gateway):</b> Lắng nghe và chặn mọi Exception phát sinh từ tầng Controller
 *       hoặc tầng Service của tất cả các module nghiệp vụ, ngăn không để lỗi rơi vào default HTML handler của Tomcat.</li>
 *   <li><b>Ánh xạ chuẩn HTTP Status:</b> Chuyển đổi các ngoại lệ nghiệp vụ thuần Java (từ {@code platform/shared})
 *       thành HTTP Status Code tương ứng (400, 401, 403, 404, 409, 500) kèm payload {@link ApiError}.</li>
 *   <li><b>Bảo vệ an ninh hệ thống:</b> Đối với các lỗi hệ thống không xác định (Internal Server Error 500),
 *       ghi log chi tiết stack trace ở máy chủ nhưng trả về thông báo chung cho client, tuyệt đối không lộ câu SQL hay tên bảng.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  // =========================================================================
  // 1. CÁC NGOẠI LỆ NGHIỆP VỤ THUẦN JAVA (TỪ PLATFORM/SHARED)
  // =========================================================================

  /**
   * Bắt lỗi không tìm thấy tài nguyên (EntityNotFoundException) -> Ánh xạ thành HTTP 404 Not Found.
   * <p>Ví dụ: Không tìm thấy học sinh, gia sư hoặc buổi học theo ID.
   */
  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ApiError> handleEntityNotFound(EntityNotFoundException ex) {
    log.warn("Resource not found: {}", ex.getMessage());
    ApiError error = ApiError.of(ex.getErrorCode().name(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  /**
   * Bắt lỗi xung đột tài nguyên / trùng lịch / trùng email (ResourceConflictException) -> Ánh xạ thành HTTP 409 Conflict.
   * <p>Ví dụ: Trùng lịch học (BR-42), email đã được đăng ký trước đó.
   */
  @ExceptionHandler(ResourceConflictException.class)
  public ResponseEntity<ApiError> handleResourceConflict(ResourceConflictException ex) {
    log.warn("Resource conflict: {} [ErrorCode: {}]", ex.getMessage(), ex.getErrorCode());
    ApiError error = ApiError.of(ex.getErrorCode().name(), ex.getMessage());
    return ResponseEntity.status(ex.getErrorCode() == ErrorCode.RATE_LIMIT_EXCEEDED
        ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.CONFLICT).body(error);
  }

  /**
   * Bắt lỗi người dùng không có quyền thao tác trên tài nguyên (ForbiddenOperationException) -> Ánh xạ thành HTTP 403 Forbidden.
   * <p>Ví dụ: Học sinh cố tình hủy lịch học của người khác hoặc gia sư chưa được duyệt hồ sơ.
   */
  @ExceptionHandler(ForbiddenOperationException.class)
  public ResponseEntity<ApiError> handleForbiddenOperation(ForbiddenOperationException ex) {
    log.warn("Forbidden operation: {}", ex.getMessage());
    ApiError error = ApiError.of(ex.getErrorCode().name(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
  }

  /**
   * Bắt lỗi thao tác không hợp lệ với trạng thái hiện tại của thực thể (InvalidOperationException) -> Ánh xạ thành HTTP 400 Bad Request.
   * <p>Ví dụ: Buổi học đã hoàn thành nhưng cố tình bấm nút hủy lịch.
   */
  @ExceptionHandler(InvalidOperationException.class)
  public ResponseEntity<ApiError> handleInvalidOperation(InvalidOperationException ex) {
    log.warn("Invalid operation: {}", ex.getMessage());
    ApiError error = ApiError.of(ex.getErrorCode().name(), ex.getMessage());
    return ResponseEntity.status(mapErrorCodeToHttpStatus(ex.getErrorCode())).body(error);
  }

  /**
   * Bắt lỗi nghiệp vụ theo từng trường (FieldValidationException) -> HTTP 400 kèm danh sách lỗi trường.
   */
  @ExceptionHandler(FieldValidationException.class)
  public ResponseEntity<ApiError> handleFieldValidation(FieldValidationException ex) {
    List<FieldErrorItem> details = ex.getFieldErrors().entrySet().stream()
        .map(entry -> new FieldErrorItem(entry.getKey(), entry.getValue(), null))
        .toList();
    log.warn("Field validation failed with {} errors", details.size());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiError.of(ErrorCode.VALIDATION_FAILED.name(), ex.getMessage(), details));
  }

  /**
   * Bắt các ngoại lệ nghiệp vụ khác kế thừa từ BaseBusinessException -> Ánh xạ theo ErrorCode tương ứng.
   */
  @ExceptionHandler(BaseBusinessException.class)
  public ResponseEntity<ApiError> handleBaseBusinessException(BaseBusinessException ex) {
    log.warn("Business exception: {} [ErrorCode: {}]", ex.getMessage(), ex.getErrorCode());
    HttpStatus status = mapErrorCodeToHttpStatus(ex.getErrorCode());
    ApiError error = ApiError.of(ex.getErrorCode().name(), ex.getMessage());
    return ResponseEntity.status(status).body(error);
  }

  // =========================================================================
  // 2. CÁC NGOẠI LỆ KIỂM THỰC DỮ LIỆU ĐẦU VÀO (BEAN VALIDATION)
  // =========================================================================

  /**
   * Bắt lỗi kiểm thực khi client gửi body JSON sai định dạng hoặc thiếu trường (@Valid trên @RequestBody) -> HTTP 400.
   * <p>Tự động bóc tách từng lỗi của từng trường để Frontend Next.js bôi đỏ ô input tương ứng.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
    BindingResult bindingResult = ex.getBindingResult();
    List<FieldErrorItem> details = bindingResult.getFieldErrors().stream()
        .map(fe -> new FieldErrorItem(
            fe.getField(),
            fe.isBindingFailure() ? "Giá trị không đúng kiểu hoặc định dạng."
                : fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Giá trị không hợp lệ",
            null))
        .toList();

    log.warn("Validation failed for request with {} errors", details.size());
    ApiError error = ApiError.of(
        ErrorCode.VALIDATION_FAILED.name(),
        "Dữ liệu đầu vào không hợp lệ. Vui lòng kiểm tra lại các trường thông tin.",
        details
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleUnreadableBody(
      org.springframework.http.converter.HttpMessageNotReadableException ex) {
    // Parser errors can contain raw request values. Do not log or return their messages.
    return ResponseEntity.badRequest().body(ApiError.of(
        ErrorCode.VALIDATION_FAILED.name(), "Nội dung JSON không hợp lệ hoặc thiếu trường bắt buộc."));
  }

  @ExceptionHandler({org.springframework.beans.TypeMismatchException.class,
      org.springframework.web.bind.MissingServletRequestParameterException.class,
      org.springframework.web.bind.ServletRequestBindingException.class})
  public ResponseEntity<ApiError> handleMalformedParameter(Exception ex) {
    // Conversion messages may embed passwords, tokens or arbitrary rejected values.
    return ResponseEntity.badRequest().body(ApiError.of(
        ErrorCode.VALIDATION_FAILED.name(), "Tham số yêu cầu bị thiếu hoặc không đúng định dạng."));
  }

  @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
  public ResponseEntity<ApiError> handleMethodValidation(
      org.springframework.web.method.annotation.HandlerMethodValidationException ex) {
    // Return-value violations are server defects, not invalid client input.
    if (ex.isForReturnValue()) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(
          ErrorCode.INTERNAL_SERVER_ERROR.name(), "Đã xảy ra lỗi nội bộ hệ thống."));
    }
    return ResponseEntity.badRequest().body(ApiError.of(
        ErrorCode.VALIDATION_FAILED.name(), "Tham số yêu cầu không thỏa mãn ràng buộc hợp lệ."));
  }

  /**
   * Bắt lỗi vi phạm ràng buộc trên RequestParam hoặc PathVariable (@Validated) -> HTTP 400.
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
    List<FieldErrorItem> details = ex.getConstraintViolations().stream()
        .map(cv -> new FieldErrorItem(
            cv.getPropertyPath().toString(),
            cv.getMessage(),
            null))
        .toList();

    log.warn("Constraint validation failed with {} errors", details.size());
    ApiError error = ApiError.of(
        ErrorCode.VALIDATION_FAILED.name(),
        "Tham số yêu cầu không thỏa mãn ràng buộc hợp lệ.",
        details
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  // =========================================================================
  // 3. CÁC NGOẠI LỆ BẢO MẬT & PHÂN QUYỀN (SPRING SECURITY)
  // =========================================================================

  /**
   * Bắt lỗi người dùng chưa đăng nhập hoặc phiên làm việc đã hết hạn -> HTTP 401 Unauthorized.
   */
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiError> handleAuthenticationException(AuthenticationException ex) {
    log.warn("Authentication failed: {}", ex.getMessage());
    ApiError error = ApiError.of(
        ErrorCode.UNAUTHORIZED.name(),
        "Yêu cầu cần được xác thực hoặc phiên làm việc đã hết hạn."
    );
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
  }

  /**
   * Bắt lỗi từ chối truy cập từ Spring Security (@PreAuthorize, URL security) -> HTTP 403 Forbidden.
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
    log.warn("Access denied: {}", ex.getMessage());
    ApiError error = ApiError.of(
        ErrorCode.FORBIDDEN.name(),
        "Bạn không có quyền thực hiện hành động này trong hệ thống."
    );
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
  }

  // =========================================================================
  // 4. CÁC NGOẠI LỆ TẦNG MẠNG & ROUTING HTTP CỦA SPRING MVC
  // =========================================================================

  /**
   * Bắt lỗi URL đường dẫn không tồn tại trên máy chủ (Spring 6+) -> HTTP 404 Not Found.
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex) {
    ApiError error = ApiError.of(
        ErrorCode.RESOURCE_NOT_FOUND.name(),
        "Đường dẫn API yêu cầu không tồn tại trên hệ thống: " + ex.getResourcePath()
    );
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  /**
   * Bắt lỗi phương thức HTTP không được hỗ trợ (Method Not Allowed) -> HTTP 405.
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
    ApiError error = ApiError.of(
        "METHOD_NOT_ALLOWED",
        "Phương thức HTTP " + ex.getMethod() + " không được hỗ trợ cho đường dẫn này."
    );
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
  }

  /**
   * Bắt lỗi định dạng dữ liệu gửi lên không được hỗ trợ (Unsupported Media Type) -> HTTP 415.
   */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
    ApiError error = ApiError.of(
        "UNSUPPORTED_MEDIA_TYPE",
        "Định dạng nội dung gửi lên (Content-Type) không được hỗ trợ: " + ex.getContentType()
    );
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(error);
  }

  /**
   * Bắt lỗi dung lượng tệp tin tải lên vượt quá giới hạn cho phép (MaxUploadSizeExceededException) -> HTTP 413 Payload Too Large.
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
    log.warn("Max upload size exceeded: {}", ex.getMessage());
    ApiError error = ApiError.of(
        "PAYLOAD_TOO_LARGE",
        "Kích thước tệp tải lên vượt quá giới hạn cho phép (tối đa 10MB cho mỗi tệp và 20MB cho toàn bộ yêu cầu)."
    );
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
  }

  /**
   * Bắt lỗi xung đột phiên bản Optimistic Locking từ Hibernate/JPA khi cập nhật đồng thời -> HTTP 409 Conflict.
   */
  @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ApiError> handleOptimisticLocking(
      org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
    log.warn("Optimistic locking conflict detected: {}", ex.getMessage());
    ApiError error = ApiError.of(
        ErrorCode.PLAN_VERSION_CONFLICT.name(),
        "Kế hoạch đã bị chỉnh sửa bởi phiên làm việc khác. Vui lòng tải lại trang để đối chiếu phiên bản mới nhất."
    );
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
  }

  // =========================================================================
  // 5. NGOẠI LỆ HỆ THỐNG KHÔNG XÁC ĐỊNH (CATCH-ALL / FALLBACK)
  // =========================================================================

  /**
   * Chốt chặn cuối cùng bắt toàn bộ các lỗi ngoại lệ chưa lường trước khác -> HTTP 500 Internal Server Error.
   * Ghi log nghiêm trọng ở server nhưng trả về thông báo chung an toàn cho client.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnhandledException(Exception ex) {
    log.error("Unhandled internal server error: ", ex);
    ApiError error = ApiError.of(
        ErrorCode.INTERNAL_SERVER_ERROR.name(),
        "Đã xảy ra lỗi nội bộ hệ thống. Vui lòng thử lại sau hoặc liên hệ quản trị viên."
    );
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }

  // =========================================================================
  // HÀM TIỆN ÍCH ÁNH XẠ MÃ LỖI
  // =========================================================================

  private HttpStatus mapErrorCodeToHttpStatus(ErrorCode errorCode) {
    if (errorCode == null) {
      return HttpStatus.BAD_REQUEST;
    }
    return switch (errorCode) {
      case UNAUTHORIZED, INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
      case FORBIDDEN, USER_NOT_ACTIVE -> HttpStatus.FORBIDDEN;
      case ACCOUNT_LOCKED -> HttpStatus.LOCKED;
      case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
      case CONFLICT_DETECTED, USER_ALREADY_EXISTS, SCHEDULE_OVERLAP, REVIEW_ALREADY_EXISTS,
          SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE, PLAN_VERSION_CONFLICT -> HttpStatus.CONFLICT;
      case AI_SERVICE_UNAVAILABLE, EMAIL_DELIVERY_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
      case AI_REQUEST_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
      case INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
      default -> HttpStatus.BAD_REQUEST;
    };
  }
}

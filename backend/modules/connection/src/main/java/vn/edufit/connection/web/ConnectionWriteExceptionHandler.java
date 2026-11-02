package vn.edufit.connection.web;

import org.springframework.core.annotation.Order;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edufit.shared.response.ApiError;

/** Constraint/flush/commit errors reach this boundary only after transaction rollback. */
@Order(0)
@RestControllerAdvice(assignableTypes = ConnectionController.class)
public class ConnectionWriteExceptionHandler {
  @ExceptionHandler({DataIntegrityViolationException.class, ConstraintViolationException.class, OptimisticLockingFailureException.class})
  public ResponseEntity<ApiError> conflict(RuntimeException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiError.of("CONFLICT_DETECTED", "Dữ liệu kết nối đã thay đổi hoặc không còn hợp lệ"));
  }
}

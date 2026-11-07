package vn.edufit.progress.domain.model;

import java.util.Arrays;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Cảm nhận 1 chạm của học sinh sau buổi học (3 biểu tượng). Độc lập với điểm chấm của gia sư.
 */
public enum SelfRating {
  STRUGGLING(1),
  OKAY(2),
  CONFIDENT(3);

  private final int value;

  SelfRating(int value) { this.value = value; }

  public static SelfRating fromValue(int value) {
    return Arrays.stream(values())
        .filter(r -> r.value == value)
        .findFirst()
        .orElseThrow(() -> new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Cảm nhận buổi học không hợp lệ"));
  }

  public int value() { return value; }
}

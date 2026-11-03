package vn.edufit.progress.domain.model;

import java.util.Arrays;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Thang mức độ tự lập 4 mức, mô tả bằng hành vi nhìn thấy được. Là thang duy nhất để đo tiến bộ theo cột mốc.
 */
public enum IndependenceLevel {
  NOT_YET(1, "chưa làm được"),
  WITH_HINTS(2, "làm được khi có gợi ý"),
  INDEPENDENT(3, "tự làm đúng"),
  MASTERY(4, "tự làm đúng cả dạng khó và giải thích lại được");

  private final int value;
  private final String label;

  IndependenceLevel(int value, String label) {
    this.value = value;
    this.label = label;
  }

  public static IndependenceLevel fromValue(int value) {
    return Arrays.stream(values())
        .filter(l -> l.value == value)
        .findFirst()
        .orElseThrow(() -> new InvalidOperationException(
            ErrorCode.VALIDATION_FAILED, "Mức độ tự lập phải từ 1 đến 4"));
  }

  public int value() { return value; }
  public String label() { return label; }
}

package vn.edufit.verification.web.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewVerificationRequest(
    @NotNull(message = "Hành động duyệt hồ sơ là bắt buộc")
    @JsonAlias({"action", "decision"})
    Action action,

    @Size(max = 1000, message = "Lý do từ chối tối đa 1000 ký tự")
    @JsonAlias({"reason", "rejectionReason"})
    String reason,

    /** Loại gia sư admin xác nhận khi duyệt, dựa trên giấy tờ đã nộp (tùy chọn). */
    @Pattern(regexp = "UNIVERSITY_STUDENT|GRADUATE|IN_SERVICE_TEACHER|RETIRED_TEACHER",
        message = "Loại gia sư không hợp lệ")
    String confirmedTutorType
) {

  public enum Action {
    APPROVE,
    REJECT;

    @JsonCreator
    public static Action fromString(String value) {
      if (value == null || value.isBlank()) {
        return null;
      }
      String normalized = value.trim().toUpperCase();
      if ("APPROVED".equals(normalized) || "APPROVE".equals(normalized)) {
        return APPROVE;
      }
      if ("REJECTED".equals(normalized) || "REJECT".equals(normalized)) {
        return REJECT;
      }
      return Action.valueOf(normalized);
    }
  }
}

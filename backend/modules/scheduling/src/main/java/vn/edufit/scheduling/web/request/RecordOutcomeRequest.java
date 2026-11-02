package vn.edufit.scheduling.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.edufit.scheduling.domain.model.SessionStatus;

/**
 * Request DTO gửi lên khi Gia sư ghi nhận kết quả và biên bản buổi học (UC4.6).
 */
public record RecordOutcomeRequest(
    @NotNull(message = "outcome không được để trống (COMPLETED hoặc ABSENT)")
    SessionStatus outcome,

    @Size(max = 5000, message = "noteContent không được vượt quá 5000 ký tự")
    String noteContent
) {}

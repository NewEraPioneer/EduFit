package vn.edufit.verification.web.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeVerificationRequest(
    @NotBlank(message = "Lý do thu hồi phê duyệt không được để trống")
    @Size(min = 5, max = 1000, message = "Lý do thu hồi phê duyệt phải từ 5 đến 1000 ký tự")
    @JsonAlias({"reason", "rejectionReason"})
    String reason
) {}

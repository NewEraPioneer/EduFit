package vn.edufit.progress.web.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Cảm nhận 1 chạm của học sinh: PUT /api/v1/progress/sessions/{sessionId}/self-rating. */
public record SelfRatingRequest(
    @NotNull(message = "Cần chọn một cảm nhận về buổi học")
    @Min(value = 1, message = "Cảm nhận từ 1 đến 3")
    @Max(value = 3, message = "Cảm nhận từ 1 đến 3")
    Integer rating
) {}

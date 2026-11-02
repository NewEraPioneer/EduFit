package vn.edufit.scheduling.web.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Request DTO gửi lên khi yêu cầu dời lịch học sang thời gian mới (UC4.4).
 */
public record RescheduleSessionRequest(
    @NotNull(message = "newStartAt không được để trống")
    @Future(message = "newStartAt phải ở tương lai")
    Instant newStartAt,

    @NotNull(message = "newEndAt không được để trống")
    @Future(message = "newEndAt phải ở tương lai")
    Instant newEndAt,

    @Size(max = 500, message = "reason không được vượt quá 500 ký tự")
    String reason
) {}

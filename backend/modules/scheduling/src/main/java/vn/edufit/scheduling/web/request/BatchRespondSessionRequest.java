package vn.edufit.scheduling.web.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO cho API phản hồi hàng loạt đề xuất buổi học (Batch Respond Proposals).
 */
public record BatchRespondSessionRequest(
    @NotEmpty(message = "Danh sách sessionId không được để trống")
    List<UUID> sessionIds,
    @NotNull(message = "accept không được để trống")
    Boolean accept,
    String reason
) {}

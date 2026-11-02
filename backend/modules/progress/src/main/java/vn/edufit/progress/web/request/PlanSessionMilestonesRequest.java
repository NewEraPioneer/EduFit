package vn.edufit.progress.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Cột mốc dự kiến của buổi học: PUT /api/v1/progress/sessions/{sessionId}/planned-milestones. Rỗng = bỏ dự kiến. */
public record PlanSessionMilestonesRequest(
    @NotNull(message = "Danh sách cột mốc dự kiến không được để trống (gửi [] để bỏ dự kiến)")
    @Size(max = 3, message = "Một buổi học gắn tối đa 3 cột mốc dự kiến")
    List<UUID> milestoneIds
) {}

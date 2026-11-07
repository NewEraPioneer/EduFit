package vn.edufit.progress.web.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Yêu cầu bản nháp lộ trình bằng AI cho lớp học. */
public record PlanDraftRequest(
    @NotNull(message = "classId không được để trống")
    UUID classId
) {}

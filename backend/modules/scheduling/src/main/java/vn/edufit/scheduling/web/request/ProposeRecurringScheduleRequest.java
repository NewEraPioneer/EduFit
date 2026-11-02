package vn.edufit.scheduling.web.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import vn.edufit.scheduling.domain.model.SessionMode;

/**
 * Request DTO cho API đề xuất đổi lịch học định kỳ của lớp học (UC4.8).
 */
public record ProposeRecurringScheduleRequest(
    @NotNull(message = "classId không được để trống")
    UUID classId,
    @NotNull(message = "effectiveFrom không được để trống")
    LocalDate effectiveFrom,
    @NotNull(message = "effectiveTo không được để trống")
    LocalDate effectiveTo,
    @NotEmpty(message = "newWeeklySlots không được để trống")
    List<String> newWeeklySlots,
    SessionMode mode,
    String placeOrLink,
    String message
) {}

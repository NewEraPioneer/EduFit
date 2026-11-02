package vn.edufit.profile.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import vn.edufit.profile.infra.persistence.entity.TeachingMode;

/**
 * Tạo hoặc thay thế toàn bộ một nhu cầu học, gồm cả khung giờ, trong một lời gọi.
 * Phân loại (lớp, môn, nhánh, mục tiêu...) bắt buộc; điều kiện học (hình thức, khu vực, ngân sách,
 * lịch) có thể bổ sung sau, khi đó nhu cầu ở trạng thái chưa đủ thông tin.
 *
 * @param studentId bắt buộc khi phụ huynh thao tác cho con; học sinh để trống
 */
public record SaveLearningNeedRequest(
    UUID studentId,
    @NotNull(message = "Vui lòng chọn khối lớp.") Integer educationLevelId,
    @NotNull(message = "Vui lòng chọn môn học.") Integer subjectId,
    Integer trackId,
    @NotNull(message = "Vui lòng chọn mục tiêu học.") Integer goalTagId,
    Integer textbookSeriesId,
    Integer examProvinceId,
    Integer targetSchoolId,
    TeachingMode mode,
    Integer provinceId,
    Integer districtId,
    @Positive(message = "Ngân sách mỗi buổi phải lớn hơn 0.") Long budgetMaxPerSession,
    @Size(max = 28, message = "Tối đa 28 khung giờ mỗi tuần.") List<@NotNull @Valid Slot> availabilitySlots) {

  public List<Slot> slotsOrEmpty() {
    return availabilitySlots == null ? List.of() : availabilitySlots;
  }

  public record Slot(
      @NotNull(message = "Thiếu thứ trong tuần.") @Min(1) @Max(7) Short dayOfWeek,
      @NotNull(message = "Thiếu giờ bắt đầu.") LocalTime startTime,
      @NotNull(message = "Thiếu giờ kết thúc.") LocalTime endTime) {

    @AssertTrue(message = "Giờ kết thúc phải sau giờ bắt đầu.")
    public boolean isTimeOrderValid() {
      return startTime == null || endTime == null || startTime.isBefore(endTime);
    }

    @AssertTrue(message = "Khung giờ chỉ tính đến phút.")
    public boolean isMinuteAligned() {
      return (startTime == null || (startTime.getSecond() == 0 && startTime.getNano() == 0))
          && (endTime == null || (endTime.getSecond() == 0 && endTime.getNano() == 0));
    }
  }
}

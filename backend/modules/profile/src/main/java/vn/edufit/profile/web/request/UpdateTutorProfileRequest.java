package vn.edufit.profile.web.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import vn.edufit.profile.infra.persistence.entity.TeachingMode;
import vn.edufit.profile.infra.persistence.entity.TutorType;

/**
 * Thông tin chung của gia sư. Học phí, môn và lớp nằm ở từng gói dạy.
 * Khu vực dạy trực tiếp: một Tỉnh/Thành và nhiều Khu vực thuộc tỉnh đó.
 */
public record UpdateTutorProfileRequest(
    @NotBlank(message = "Tên hiển thị không được để trống")
    @Size(max = 100, message = "Tên hiển thị không được vượt quá 100 ký tự")
    String displayName,

    @Size(max = 200, message = "Tiêu đề không được vượt quá 200 ký tự")
    String headline,

    String bio,

    @NotNull(message = "Vui lòng chọn hình thức dạy")
    TeachingMode teachingMode,

    TutorType tutorType,

    Integer provinceId,

    @Size(max = 30, message = "Tối đa 30 khu vực")
    List<Integer> districtIds,

    @Min(value = 0, message = "Số năm kinh nghiệm không thể âm")
    Short experienceYears,

    String teachingMethod
) {
  public List<Integer> districtIdsOrEmpty() {
    return districtIds == null ? List.of() : districtIds;
  }
}

package vn.edufit.profile.web.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Tạo hoặc thay thế một gói dạy: 1 môn (+ nhánh), các khối lớp, mục tiêu (theo luật danh mục),
 * bộ sách (khi có mục tiêu cần bộ sách), tỉnh luyện đề (khi có Luyện thi vào 10) và học phí.
 */
public record SaveTutorOfferingRequest(
    @NotNull(message = "Vui lòng chọn môn học.") Integer subjectId,
    Integer trackId,
    @NotEmpty(message = "Vui lòng chọn ít nhất một khối lớp.") List<@NotNull Integer> levelIds,
    @NotEmpty(message = "Vui lòng chọn ít nhất một mục tiêu nhận dạy.") List<@NotNull Integer> goalTagIds,
    @Size(max = 10) List<@NotNull Integer> textbookSeriesIds,
    @Size(max = 34) List<@NotNull Integer> examProvinceIds,
    @NotNull(message = "Vui lòng nhập học phí mỗi buổi.")
    @Min(value = 0, message = "Học phí không thể âm.")
    Long pricePerSession
) {
  public List<Integer> textbooksOrEmpty() {
    return textbookSeriesIds == null ? List.of() : textbookSeriesIds;
  }

  public List<Integer> examProvincesOrEmpty() {
    return examProvinceIds == null ? List.of() : examProvinceIds;
  }
}

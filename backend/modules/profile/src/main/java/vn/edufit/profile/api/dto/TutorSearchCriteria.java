package vn.edufit.profile.api.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Bộ lọc cứng do người dùng tự chọn ở Discovery. Mọi tiêu chí là tùy chọn;
 * các điều kiện về gói dạy (môn, nhánh, lớp, mục tiêu, bộ sách, giá) phải cùng thỏa trên một gói.
 */
public record TutorSearchCriteria(
    Integer levelId,
    Integer subjectId,
    Integer trackId,
    Integer goalTagId,
    Integer textbookSeriesId,
    String tutorType,
    String mode,
    Integer provinceId,
    Integer districtId,
    Long minPrice,
    Long maxPrice,
    BigDecimal minRating,
    String keyword,
    Short dayOfWeek,
    LocalTime availableFrom,
    LocalTime availableTo
) {
  public static TutorSearchCriteria none() {
    return new TutorSearchCriteria(null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null);
  }
}

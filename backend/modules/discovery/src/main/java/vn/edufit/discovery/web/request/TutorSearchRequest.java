package vn.edufit.discovery.web.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;
import vn.edufit.profile.api.dto.TutorSearchCriteria;

/**
 * Bộ lọc Discovery. Mọi tiêu chí ở đây là lọc cứng do người dùng tự bấm;
 * {@code needId} (tùy chọn) chỉ dùng để xếp hạng theo độ khớp với nhu cầu học.
 */
public record TutorSearchRequest(
    @Min(value = 1, message = "levelId phải lớn hơn 0") Integer levelId,
    @Min(value = 1, message = "subjectId phải lớn hơn 0") Integer subjectId,
    @Min(value = 1, message = "trackId phải lớn hơn 0") Integer trackId,
    @Min(value = 1, message = "goalTagId phải lớn hơn 0") Integer goalTagId,
    @Min(value = 1, message = "textbookSeriesId phải lớn hơn 0") Integer textbookSeriesId,
    @Pattern(regexp = "(?i)UNIVERSITY_STUDENT|GRADUATE|IN_SERVICE_TEACHER|RETIRED_TEACHER",
        message = "tutorType không hợp lệ") String tutorType,
    @Pattern(regexp = "(?i)ONLINE|OFFLINE|BOTH", message = "mode phải là ONLINE, OFFLINE hoặc BOTH") String mode,
    @Min(value = 1, message = "provinceId phải lớn hơn 0") Integer provinceId,
    @Min(value = 1, message = "districtId phải lớn hơn 0") Integer districtId,
    @Min(value = 0, message = "minPrice không được âm") Long minPrice,
    @Min(value = 0, message = "maxPrice không được âm") Long maxPrice,
    @DecimalMin(value = "0.0", message = "minRating không được âm")
    @DecimalMax(value = "5.0", message = "minRating không được lớn hơn 5") BigDecimal minRating,
    String keyword,
    @Min(value = 1, message = "dayOfWeek phải từ 1 đến 7")
    @Max(value = 7, message = "dayOfWeek phải từ 1 đến 7") Short dayOfWeek,
    LocalTime availableFrom,
    LocalTime availableTo,
    UUID needId
) {

  @AssertTrue(message = "minPrice không được lớn hơn maxPrice")
  public boolean isPriceRangeValid() {
    return minPrice == null || maxPrice == null || minPrice <= maxPrice;
  }

  @AssertTrue(message = "dayOfWeek, availableFrom và availableTo phải được cung cấp cùng nhau")
  public boolean isAvailabilityComplete() {
    boolean none = dayOfWeek == null && availableFrom == null && availableTo == null;
    boolean all = dayOfWeek != null && availableFrom != null && availableTo != null;
    return none || all;
  }

  @AssertTrue(message = "availableTo phải sau availableFrom")
  public boolean isAvailabilityRangeValid() {
    return availableFrom == null || availableTo == null || availableTo.isAfter(availableFrom);
  }

  @AssertTrue(message = "districtId cần đi kèm provinceId")
  public boolean isDistrictWithProvince() {
    return districtId == null || provinceId != null;
  }

  public TutorSearchCriteria toCriteria() {
    return new TutorSearchCriteria(
        levelId, subjectId, trackId, goalTagId, textbookSeriesId, tutorType, mode, provinceId, districtId,
        minPrice, maxPrice, minRating, keyword, dayOfWeek, availableFrom, availableTo
    );
  }
}

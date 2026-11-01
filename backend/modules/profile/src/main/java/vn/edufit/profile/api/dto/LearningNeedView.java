package vn.edufit.profile.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Nhu cầu học kèm tên hiển thị đã tra từ danh mục. Không chứa thông tin liên hệ của gia đình.
 * {@code missingFields} liệt kê các trường còn thiếu để nhu cầu đủ điều kiện ghép gia sư.
 */
public record LearningNeedView(
    UUID needId,
    UUID studentId,
    UUID studentUserId,
    UUID createdBy,
    Integer educationLevelId,
    String educationLevelName,
    Integer subjectId,
    String subjectName,
    Integer trackId,
    String trackName,
    Integer goalTagId,
    String goalTagCode,
    String goalTagName,
    boolean goalTagPremium,
    Integer textbookSeriesId,
    String textbookSeriesName,
    Integer examProvinceId,
    String examProvinceName,
    Integer targetSchoolId,
    String targetSchoolName,
    String mode,
    Integer provinceId,
    String provinceName,
    Integer districtId,
    String districtName,
    Long budgetMaxPerSession,
    String status,
    List<WeeklyAvailabilityDto> availabilitySlots,
    List<String> missingFields,
    Instant createdAt,
    Instant updatedAt) {

  public LearningNeedView {
    availabilitySlots = List.copyOf(availabilitySlots);
    missingFields = List.copyOf(missingFields);
  }

  public boolean readyForMatching() {
    return "ACTIVE".equals(status);
  }
}

package vn.edufit.profile.web.response;

import java.util.List;

/**
 * Toàn bộ danh mục nhu cầu học (chỉ mục đang hoạt động). Frontend dựng bộ chọn
 * Khối lớp → Môn → (Nhánh) → Mục tiêu từ {@code goalTagRules}, nên không thể chọn tổ hợp sai.
 */
public record LearningCatalogResponse(
    List<LevelItem> educationLevels,
    List<SubjectItem> subjects,
    List<TrackItem> tracks,
    List<GoalTagItem> goalTags,
    List<GoalTagRuleItem> goalTagRules,
    List<TextbookItem> textbooks,
    List<ProvinceItem> provinces,
    List<DistrictItem> districts,
    List<SchoolItem> specializedSchools
) {
  public record LevelItem(Integer levelId, String name, Integer sortOrder) {}

  public record SubjectItem(Integer subjectId, String name) {}

  public record TrackItem(Integer trackId, Integer subjectId, String code, String name, List<Integer> levelIds) {}

  public record GoalTagItem(
      Integer goalTagId,
      String code,
      String name,
      String description,
      boolean requiresTextbook,
      boolean requiresExamProvince,
      boolean premium) {}

  public record GoalTagRuleItem(Integer goalTagId, Integer subjectId, Integer trackId, Integer levelId) {}

  public record TextbookItem(Integer seriesId, String code, String name, List<Integer> subjectIds) {}

  public record ProvinceItem(Integer provinceId, String name) {}

  public record DistrictItem(Integer districtId, Integer provinceId, String name) {}

  public record SchoolItem(Integer schoolId, Integer provinceId, String name) {}
}

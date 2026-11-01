package vn.edufit.profile.application.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import vn.edufit.profile.infra.persistence.entity.District;
import vn.edufit.profile.infra.persistence.entity.EducationLevel;
import vn.edufit.profile.infra.persistence.entity.GoalTag;
import vn.edufit.profile.infra.persistence.entity.GoalTagRule;
import vn.edufit.profile.infra.persistence.entity.Province;
import vn.edufit.profile.infra.persistence.entity.SpecializedSchool;
import vn.edufit.profile.infra.persistence.entity.Subject;
import vn.edufit.profile.infra.persistence.entity.SubjectTrack;
import vn.edufit.profile.infra.persistence.entity.TextbookSeries;

/**
 * Ảnh chụp danh mục nhu cầu học và là nơi duy nhất áp luật Khối lớp → Môn → (Nhánh) → Mục tiêu.
 * Frontend nhận đúng các luật này qua API danh mục; backend kiểm tra lại khi lưu nhu cầu và gói dạy.
 */
public final class LearningCatalog {

  /** Mục tiêu cho phép chọn trường chuyên làm ngữ cảnh. */
  public static final String GIFTED_TAG_CODE = "GIFTED";

  private final Map<Integer, EducationLevel> levels;
  private final Map<Integer, Subject> subjects;
  private final Map<Integer, SubjectTrack> tracks;
  private final Map<Integer, GoalTag> goalTags;
  private final List<GoalTagRule> rules;
  private final Map<Integer, TextbookSeries> textbooks;
  private final Map<Integer, Province> provinces;
  private final Map<Integer, District> districts;
  private final Map<Integer, SpecializedSchool> schools;

  LearningCatalog(
      Collection<EducationLevel> levels,
      Collection<Subject> subjects,
      Collection<SubjectTrack> tracks,
      Collection<GoalTag> goalTags,
      Collection<GoalTagRule> rules,
      Collection<TextbookSeries> textbooks,
      Collection<Province> provinces,
      Collection<District> districts,
      Collection<SpecializedSchool> schools) {
    this.levels = index(levels, EducationLevel::getLevelId);
    this.subjects = index(subjects, Subject::getSubjectId);
    this.tracks = index(tracks, SubjectTrack::getTrackId);
    this.goalTags = index(goalTags, GoalTag::getGoalTagId);
    this.rules = List.copyOf(rules);
    this.textbooks = index(textbooks, TextbookSeries::getSeriesId);
    this.provinces = index(provinces, Province::getProvinceId);
    this.districts = index(districts, District::getDistrictId);
    this.schools = index(schools, SpecializedSchool::getSchoolId);
  }

  /** Phân loại nhu cầu/gói dạy đã được chuẩn hóa: trường không áp dụng theo luật bị đặt về null. */
  public record Classification(
      Integer educationLevelId,
      Integer subjectId,
      Integer trackId,
      Integer goalTagId,
      Integer textbookSeriesId,
      Integer examProvinceId,
      Integer targetSchoolId) {}

  /**
   * Kiểm tra phân loại của một nhu cầu học. Ghi lỗi vào {@code errors} theo tên trường và trả về
   * phân loại đã chuẩn hóa khi hợp lệ.
   */
  public Optional<Classification> validateNeed(Classification input, Map<String, String> errors) {
    int before = errors.size();
    Integer levelId = input.educationLevelId();
    Integer subjectId = input.subjectId();
    if (levelId == null || !activeLevel(levelId)) {
      errors.put("educationLevelId", "Vui lòng chọn khối lớp hợp lệ.");
    }
    if (subjectId == null || !activeSubject(subjectId)) {
      errors.put("subjectId", "Vui lòng chọn môn học hợp lệ.");
    }
    if (errors.size() > before) {
      return Optional.empty();
    }
    Integer trackId = input.trackId();
    if (!validTrack(subjectId, trackId, levelId, errors)) {
      return Optional.empty();
    }
    GoalTag tag = input.goalTagId() == null ? null : goalTags.get(input.goalTagId());
    if (tag == null || !tag.isActive() || !ruleExists(tag.getGoalTagId(), subjectId, trackId, levelId)) {
      errors.put("goalTagId", "Mục tiêu không phù hợp với khối lớp và môn đã chọn.");
      return Optional.empty();
    }
    Integer textbookId = null;
    if (tag.requiresTextbook()) {
      TextbookSeries series = input.textbookSeriesId() == null ? null : textbooks.get(input.textbookSeriesId());
      if (series == null || !series.isActive() || !series.getSubjectIds().contains(subjectId)) {
        errors.put("textbookSeriesId", "Vui lòng chọn bộ sách con đang học trên trường.");
      } else {
        textbookId = series.getSeriesId();
      }
    }
    Integer examProvinceId = null;
    if (tag.requiresExamProvince()) {
      if (input.examProvinceId() == null || !activeProvince(input.examProvinceId())) {
        errors.put("examProvinceId", "Vui lòng chọn tỉnh/thành nơi con dự thi vào 10.");
      } else {
        examProvinceId = input.examProvinceId();
      }
    }
    Integer schoolId = null;
    if (GIFTED_TAG_CODE.equals(tag.getCode()) && input.targetSchoolId() != null) {
      SpecializedSchool school = schools.get(input.targetSchoolId());
      if (school == null || !school.isActive()) {
        errors.put("targetSchoolId", "Trường chuyên không hợp lệ.");
      } else {
        schoolId = school.getSchoolId();
      }
    }
    if (errors.size() > before) {
      return Optional.empty();
    }
    return Optional.of(new Classification(
        levelId, subjectId, trackId, tag.getGoalTagId(), textbookId, examProvinceId, schoolId));
  }

  /**
   * Kiểm tra Môn + Nhánh của gói dạy và lọc các khối lớp, mục tiêu theo luật.
   * Ghi lỗi theo tên trường của request gói dạy.
   */
  public boolean validateOfferingScope(
      Integer subjectId, Integer trackId, Collection<Integer> levelIds, Collection<Integer> goalTagIds,
      Map<String, String> errors) {
    int before = errors.size();
    if (subjectId == null || !activeSubject(subjectId)) {
      errors.put("subjectId", "Vui lòng chọn môn học hợp lệ.");
      return false;
    }
    if (levelIds == null || levelIds.isEmpty()) {
      errors.put("levelIds", "Vui lòng chọn ít nhất một khối lớp.");
      return false;
    }
    for (Integer levelId : levelIds) {
      if (levelId == null || !activeLevel(levelId)) {
        errors.put("levelIds", "Khối lớp không hợp lệ.");
        return false;
      }
      if (!validTrack(subjectId, trackId, levelId, errors)) {
        if (errors.containsKey("trackId") && trackId != null && tracks.containsKey(trackId)) {
          errors.remove("trackId");
          errors.put("levelIds", "Nhánh " + tracks.get(trackId).getName() + " không áp dụng cho khối lớp đã chọn.");
        }
        return false;
      }
    }
    for (Integer tagId : goalTagIds == null ? List.<Integer>of() : goalTagIds) {
      GoalTag tag = tagId == null ? null : goalTags.get(tagId);
      boolean allowed = tag != null && tag.isActive()
          && levelIds.stream().anyMatch(level -> ruleExists(tag.getGoalTagId(), subjectId, trackId, level));
      if (!allowed) {
        errors.put("goalTagIds", "Có mục tiêu không áp dụng cho môn và các khối lớp đã chọn.");
        break;
      }
    }
    return errors.size() == before;
  }

  /** Kiểm tra khu vực dạy/học trực tiếp: Tỉnh bắt buộc, Khu vực (nếu có) phải thuộc Tỉnh. */
  public void validateLocation(
      Integer provinceId, Collection<Integer> districtIds, String provinceField, String districtField,
      Map<String, String> errors) {
    if (provinceId == null || !activeProvince(provinceId)) {
      errors.put(provinceField, "Vui lòng chọn tỉnh/thành.");
      return;
    }
    for (Integer districtId : districtIds) {
      District district = districtId == null ? null : districts.get(districtId);
      if (district == null || !district.isActive() || !district.getProvinceId().equals(provinceId)) {
        errors.put(districtField, "Khu vực không thuộc tỉnh/thành đã chọn.");
        return;
      }
    }
  }

  public boolean subjectHasTracks(Integer subjectId) {
    return tracks.values().stream().anyMatch(t -> t.isActive() && t.getSubjectId().equals(subjectId));
  }

  public boolean ruleExists(Integer goalTagId, Integer subjectId, Integer trackId, Integer levelId) {
    return rules.stream().anyMatch(rule -> rule.getGoalTagId().equals(goalTagId)
        && rule.getSubjectId().equals(subjectId)
        && Objects.equals(rule.getTrackId(), trackId)
        && rule.getLevelId().equals(levelId));
  }

  public boolean textbookAllowed(Integer seriesId, Integer subjectId) {
    TextbookSeries series = textbooks.get(seriesId);
    return series != null && series.isActive() && series.getSubjectIds().contains(subjectId);
  }

  public boolean activeProvince(Integer provinceId) {
    Province province = provinces.get(provinceId);
    return province != null && province.isActive();
  }

  /** Mục tiêu đang hoạt động hợp lệ cho tổ hợp đã chọn, theo thứ tự hiển thị. */
  public List<GoalTag> goalTagsFor(Integer levelId, Integer subjectId, Integer trackId) {
    return rules.stream()
        .filter(rule -> rule.getSubjectId().equals(subjectId) && rule.getLevelId().equals(levelId)
            && Objects.equals(rule.getTrackId(), trackId))
        .map(rule -> goalTags.get(rule.getGoalTagId()))
        .filter(tag -> tag != null && tag.isActive())
        .distinct()
        .sorted(Comparator.comparing(GoalTag::getSortOrder).thenComparing(GoalTag::getGoalTagId))
        .toList();
  }

  public Optional<GoalTag> goalTag(Integer goalTagId) {
    return Optional.ofNullable(goalTagId == null ? null : goalTags.get(goalTagId));
  }

  public Optional<SubjectTrack> track(Integer trackId) {
    return Optional.ofNullable(trackId == null ? null : tracks.get(trackId));
  }

  public Optional<Subject> subject(Integer subjectId) {
    return Optional.ofNullable(subjectId == null ? null : subjects.get(subjectId));
  }

  public Optional<EducationLevel> level(Integer levelId) {
    return Optional.ofNullable(levelId == null ? null : levels.get(levelId));
  }

  public Optional<TextbookSeries> textbook(Integer seriesId) {
    return Optional.ofNullable(seriesId == null ? null : textbooks.get(seriesId));
  }

  public Optional<Province> province(Integer provinceId) {
    return Optional.ofNullable(provinceId == null ? null : provinces.get(provinceId));
  }

  public Optional<District> district(Integer districtId) {
    return Optional.ofNullable(districtId == null ? null : districts.get(districtId));
  }

  public Optional<SpecializedSchool> school(Integer schoolId) {
    return Optional.ofNullable(schoolId == null ? null : schools.get(schoolId));
  }

  public Collection<EducationLevel> levels() {
    return levels.values();
  }

  public Collection<Subject> subjects() {
    return subjects.values();
  }

  public Collection<SubjectTrack> tracks() {
    return tracks.values();
  }

  public Collection<GoalTag> goalTags() {
    return goalTags.values();
  }

  public List<GoalTagRule> rules() {
    return rules;
  }

  public Collection<TextbookSeries> textbooks() {
    return textbooks.values();
  }

  public Collection<Province> provinces() {
    return provinces.values();
  }

  public Collection<District> districts() {
    return districts.values();
  }

  public Collection<SpecializedSchool> schools() {
    return schools.values();
  }

  private boolean validTrack(Integer subjectId, Integer trackId, Integer levelId, Map<String, String> errors) {
    if (!subjectHasTracks(subjectId)) {
      if (trackId != null) {
        errors.put("trackId", "Môn học này không chia nhánh.");
        return false;
      }
      return true;
    }
    SubjectTrack track = trackId == null ? null : tracks.get(trackId);
    if (track == null || !track.isActive() || !track.getSubjectId().equals(subjectId)
        || !track.getLevelIds().contains(levelId)) {
      errors.put("trackId", "Vui lòng chọn nhánh phù hợp với môn và khối lớp.");
      return false;
    }
    return true;
  }

  private boolean activeLevel(Integer levelId) {
    EducationLevel level = levels.get(levelId);
    return level != null && Boolean.TRUE.equals(level.getIsActive());
  }

  private boolean activeSubject(Integer subjectId) {
    Subject subject = subjects.get(subjectId);
    return subject != null && Boolean.TRUE.equals(subject.getIsActive());
  }

  private static <K, V> Map<K, V> index(Collection<V> values, Function<V, K> key) {
    return values.stream().collect(Collectors.toMap(key, Function.identity(), (a, b) -> a, LinkedHashMap::new));
  }
}

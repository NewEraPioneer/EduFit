package vn.edufit.profile.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
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
 * Danh mục thu gọn giống seed V1.24: Lớp 6–9, Toán (không nhánh), KHTN (tích hợp 6–7, Trọng tâm Lý 8–9),
 * 4 nhóm mục tiêu chung, 2 bộ sách, Hà Nội + Cầu Giấy, Đà Nẵng.
 */
public final class CatalogFixtures {

  public static final int LEVEL_6 = 1;
  public static final int LEVEL_7 = 2;
  public static final int LEVEL_8 = 3;
  public static final int LEVEL_9 = 4;
  public static final int MATH = 1;
  public static final int SCIENCE = 7;
  public static final int RETIRED_PHYSICS = 4;
  public static final int SCI_INTEGRATED = 1;
  public static final int SCI_PHYSICS = 2;
  public static final int FOUNDATION = 1;
  public static final int GRADE_BOOST = 2;
  public static final int GIFTED = 3;
  public static final int GRADE10_EXAM = 4;
  public static final int KNTT = 1;
  public static final int GLOBAL_SUCCESS = 4;
  public static final int HA_NOI = 1;
  public static final int DA_NANG = 4;
  public static final int CAU_GIAY = 5;
  public static final int HAI_CHAU = 31;
  public static final int AMSTERDAM = 1;

  private CatalogFixtures() {}

  public static LearningCatalog catalog() {
    var levels = List.of(level(LEVEL_6, "Lớp 6", 1), level(LEVEL_7, "Lớp 7", 2), level(LEVEL_8, "Lớp 8", 3),
        level(LEVEL_9, "Lớp 9", 4));
    var subjects = List.of(subject(MATH, "Toán", true), subject(SCIENCE, "Khoa học tự nhiên", true),
        subject(RETIRED_PHYSICS, "Vật Lý", false));
    var tracks = List.of(
        track(SCI_INTEGRATED, SCIENCE, "SCI_INTEGRATED", "KHTN tích hợp", Set.of(LEVEL_6, LEVEL_7)),
        track(SCI_PHYSICS, SCIENCE, "SCI_PHYSICS", "Trọng tâm Vật lý", Set.of(LEVEL_8, LEVEL_9)));
    var tags = List.of(
        tag(FOUNDATION, "FOUNDATION", "Lấy lại gốc & Bám sát SGK", true, false, false, 1),
        tag(GRADE_BOOST, "GRADE_BOOST", "Nâng cao điểm số định kỳ (8+/9+)", true, false, false, 2),
        tag(GIFTED, "GIFTED", "Bồi dưỡng HSG & Thi chuyên", false, false, true, 3),
        tag(GRADE10_EXAM, "GRADE10_EXAM", "Chuyển cấp – Luyện thi vào 10", false, true, false, 4));
    var rules = new ArrayList<GoalTagRule>();
    int ruleId = 1;
    for (int level : List.of(LEVEL_6, LEVEL_7, LEVEL_8, LEVEL_9)) {
      for (int tagId : List.of(FOUNDATION, GRADE_BOOST, GIFTED)) {
        rules.add(rule(ruleId++, tagId, MATH, null, level));
      }
      if (level >= LEVEL_8) {
        rules.add(rule(ruleId++, GRADE10_EXAM, MATH, null, level));
      }
    }
    for (int level : List.of(LEVEL_6, LEVEL_7)) {
      for (int tagId : List.of(FOUNDATION, GRADE_BOOST, GIFTED)) {
        rules.add(rule(ruleId++, tagId, SCIENCE, SCI_INTEGRATED, level));
      }
    }
    for (int level : List.of(LEVEL_8, LEVEL_9)) {
      for (int tagId : List.of(FOUNDATION, GRADE_BOOST, GIFTED, GRADE10_EXAM)) {
        rules.add(rule(ruleId++, tagId, SCIENCE, SCI_PHYSICS, level));
      }
    }
    var textbooks = List.of(
        textbook(KNTT, "KNTT", "Kết nối tri thức với cuộc sống", Set.of(MATH, SCIENCE)),
        textbook(GLOBAL_SUCCESS, "GLOBAL_SUCCESS", "Global Success", Set.of(3)));
    var provinces = List.of(province(HA_NOI, "HA_NOI", "Hà Nội"), province(DA_NANG, "DA_NANG", "Đà Nẵng"));
    var districts = List.of(district(CAU_GIAY, HA_NOI, "Cầu Giấy"), district(HAI_CHAU, DA_NANG, "Hải Châu"));
    var schools = List.of(school(AMSTERDAM, HA_NOI, "THPT Chuyên Hà Nội – Amsterdam"));
    return new LearningCatalog(levels, subjects, tracks, tags, rules, textbooks, provinces, districts, schools);
  }

  private static <T> T make(Class<T> type) {
    return BeanUtils.instantiateClass(type);
  }

  private static EducationLevel level(int id, String name, int order) {
    var level = new EducationLevel(name, order, true);
    ReflectionTestUtils.setField(level, "levelId", id);
    return level;
  }

  private static Subject subject(int id, String name, boolean active) {
    var subject = new Subject(name, active);
    ReflectionTestUtils.setField(subject, "subjectId", id);
    return subject;
  }

  private static SubjectTrack track(int id, int subjectId, String code, String name, Set<Integer> levels) {
    var track = make(SubjectTrack.class);
    ReflectionTestUtils.setField(track, "trackId", id);
    ReflectionTestUtils.setField(track, "subjectId", subjectId);
    ReflectionTestUtils.setField(track, "code", code);
    ReflectionTestUtils.setField(track, "name", name);
    ReflectionTestUtils.setField(track, "sortOrder", id);
    ReflectionTestUtils.setField(track, "active", true);
    ReflectionTestUtils.setField(track, "levelIds", levels);
    return track;
  }

  private static GoalTag tag(int id, String code, String name, boolean textbook, boolean exam, boolean premium,
      int order) {
    var tag = make(GoalTag.class);
    ReflectionTestUtils.setField(tag, "goalTagId", id);
    ReflectionTestUtils.setField(tag, "code", code);
    ReflectionTestUtils.setField(tag, "name", name);
    ReflectionTestUtils.setField(tag, "requiresTextbook", textbook);
    ReflectionTestUtils.setField(tag, "requiresExamProvince", exam);
    ReflectionTestUtils.setField(tag, "premium", premium);
    ReflectionTestUtils.setField(tag, "sortOrder", order);
    ReflectionTestUtils.setField(tag, "active", true);
    return tag;
  }

  private static GoalTagRule rule(int id, int tagId, int subjectId, Integer trackId, int levelId) {
    var rule = make(GoalTagRule.class);
    ReflectionTestUtils.setField(rule, "ruleId", id);
    ReflectionTestUtils.setField(rule, "goalTagId", tagId);
    ReflectionTestUtils.setField(rule, "subjectId", subjectId);
    ReflectionTestUtils.setField(rule, "trackId", trackId);
    ReflectionTestUtils.setField(rule, "levelId", levelId);
    return rule;
  }

  private static TextbookSeries textbook(int id, String code, String name, Set<Integer> subjectIds) {
    var series = make(TextbookSeries.class);
    ReflectionTestUtils.setField(series, "seriesId", id);
    ReflectionTestUtils.setField(series, "code", code);
    ReflectionTestUtils.setField(series, "name", name);
    ReflectionTestUtils.setField(series, "sortOrder", id);
    ReflectionTestUtils.setField(series, "active", true);
    ReflectionTestUtils.setField(series, "subjectIds", subjectIds);
    return series;
  }

  private static Province province(int id, String code, String name) {
    var province = make(Province.class);
    ReflectionTestUtils.setField(province, "provinceId", id);
    ReflectionTestUtils.setField(province, "code", code);
    ReflectionTestUtils.setField(province, "name", name);
    ReflectionTestUtils.setField(province, "sortOrder", id);
    ReflectionTestUtils.setField(province, "active", true);
    return province;
  }

  private static District district(int id, int provinceId, String name) {
    var district = make(District.class);
    ReflectionTestUtils.setField(district, "districtId", id);
    ReflectionTestUtils.setField(district, "provinceId", provinceId);
    ReflectionTestUtils.setField(district, "name", name);
    ReflectionTestUtils.setField(district, "sortOrder", id);
    ReflectionTestUtils.setField(district, "active", true);
    return district;
  }

  private static SpecializedSchool school(int id, int provinceId, String name) {
    var school = make(SpecializedSchool.class);
    ReflectionTestUtils.setField(school, "schoolId", id);
    ReflectionTestUtils.setField(school, "provinceId", provinceId);
    ReflectionTestUtils.setField(school, "name", name);
    ReflectionTestUtils.setField(school, "active", true);
    return school;
  }
}

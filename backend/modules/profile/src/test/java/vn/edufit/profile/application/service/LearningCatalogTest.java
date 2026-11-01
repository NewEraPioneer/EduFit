package vn.edufit.profile.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.edufit.profile.application.service.CatalogFixtures.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.profile.infra.persistence.entity.GoalTag;

class LearningCatalogTest {

  private final LearningCatalog catalog = CatalogFixtures.catalog();

  private Map<String, String> validate(Integer level, Integer subject, Integer track, Integer tag, Integer textbook,
      Integer examProvince, Integer school) {
    Map<String, String> errors = new LinkedHashMap<>();
    catalog.validateNeed(new LearningCatalog.Classification(level, subject, track, tag, textbook, examProvince, school),
        errors);
    return errors;
  }

  @Test
  @DisplayName("Lớp 6 không được chọn Luyện thi vào 10")
  void grade6CannotPickGrade10Exam() {
    var errors = validate(LEVEL_6, MATH, null, GRADE10_EXAM, null, HA_NOI, null);
    assertTrue(errors.containsKey("goalTagId"));
  }

  @Test
  @DisplayName("Lớp 9 + Luyện thi vào 10 bắt buộc tỉnh dự thi, không cần bộ sách")
  void grade10ExamRequiresExamProvinceOnly() {
    var errors = validate(LEVEL_9, MATH, null, GRADE10_EXAM, null, null, null);
    assertEquals(Map.of("examProvinceId", errors.get("examProvinceId")), errors);

    Map<String, String> ok = new LinkedHashMap<>();
    var normalized = catalog.validateNeed(new LearningCatalog.Classification(
        LEVEL_9, MATH, null, GRADE10_EXAM, KNTT, HA_NOI, AMSTERDAM), ok).orElseThrow();
    assertTrue(ok.isEmpty());
    // Bộ sách và trường chuyên không áp dụng cho mục tiêu này nên bị bỏ đi
    assertNull(normalized.textbookSeriesId());
    assertNull(normalized.targetSchoolId());
    assertEquals(HA_NOI, normalized.examProvinceId());
  }

  @Test
  @DisplayName("Lấy lại gốc bắt buộc bộ sách thuộc đúng môn")
  void foundationRequiresTextbookOfSubject() {
    assertTrue(validate(LEVEL_7, MATH, null, FOUNDATION, null, null, null).containsKey("textbookSeriesId"));
    assertTrue(validate(LEVEL_7, MATH, null, FOUNDATION, GLOBAL_SUCCESS, null, null).containsKey("textbookSeriesId"));
    assertTrue(validate(LEVEL_7, MATH, null, FOUNDATION, KNTT, null, null).isEmpty());
  }

  @Test
  @DisplayName("KHTN bắt buộc chọn nhánh hợp lệ với khối lớp")
  void scienceRequiresTrackForLevel() {
    assertTrue(validate(LEVEL_8, SCIENCE, null, GIFTED, null, null, null).containsKey("trackId"));
    assertTrue(validate(LEVEL_8, SCIENCE, SCI_INTEGRATED, GIFTED, null, null, null).containsKey("trackId"));
    assertTrue(validate(LEVEL_8, SCIENCE, SCI_PHYSICS, GIFTED, null, null, null).isEmpty());
  }

  @Test
  @DisplayName("Môn không chia nhánh thì không được gửi nhánh")
  void subjectWithoutTracksRejectsTrack() {
    assertTrue(validate(LEVEL_8, MATH, SCI_PHYSICS, GIFTED, null, null, null).containsKey("trackId"));
  }

  @Test
  @DisplayName("Môn đã ngừng (Vật Lý cũ) không được chọn")
  void retiredSubjectRejected() {
    assertTrue(validate(LEVEL_8, RETIRED_PHYSICS, null, GIFTED, null, null, null).containsKey("subjectId"));
  }

  @Test
  @DisplayName("HSG & Thi chuyên giữ trường chuyên làm ngữ cảnh")
  void giftedKeepsTargetSchool() {
    Map<String, String> errors = new LinkedHashMap<>();
    var normalized = catalog.validateNeed(new LearningCatalog.Classification(
        LEVEL_9, MATH, null, GIFTED, null, null, AMSTERDAM), errors).orElseThrow();
    assertEquals(AMSTERDAM, normalized.targetSchoolId());
  }

  @Test
  @DisplayName("Danh sách mục tiêu theo tổ hợp đúng thứ tự hiển thị")
  void goalTagsForCombination() {
    assertEquals(List.of("FOUNDATION", "GRADE_BOOST", "GIFTED"),
        catalog.goalTagsFor(LEVEL_7, MATH, null).stream().map(GoalTag::getCode).toList());
    assertEquals(List.of("FOUNDATION", "GRADE_BOOST", "GIFTED", "GRADE10_EXAM"),
        catalog.goalTagsFor(LEVEL_9, SCIENCE, SCI_PHYSICS).stream().map(GoalTag::getCode).toList());
  }

  @Test
  @DisplayName("Khu vực phải thuộc tỉnh đã chọn")
  void districtMustBelongToProvince() {
    Map<String, String> errors = new LinkedHashMap<>();
    catalog.validateLocation(HA_NOI, List.of(HAI_CHAU), "provinceId", "districtId", errors);
    assertTrue(errors.containsKey("districtId"));
    errors.clear();
    catalog.validateLocation(HA_NOI, List.of(CAU_GIAY), "provinceId", "districtId", errors);
    assertTrue(errors.isEmpty());
  }

  @Test
  @DisplayName("Gói dạy: nhánh phải áp dụng cho mọi khối lớp đã chọn, mục tiêu phải theo luật")
  void offeringScopeFollowsRules() {
    Map<String, String> errors = new LinkedHashMap<>();
    assertFalse(catalog.validateOfferingScope(SCIENCE, SCI_PHYSICS, List.of(LEVEL_7, LEVEL_8), List.of(GIFTED), errors));
    assertTrue(errors.containsKey("levelIds"));

    errors.clear();
    assertFalse(catalog.validateOfferingScope(MATH, null, List.of(LEVEL_6, LEVEL_7), List.of(GRADE10_EXAM), errors));
    assertTrue(errors.containsKey("goalTagIds"));

    errors.clear();
    assertTrue(catalog.validateOfferingScope(MATH, null, List.of(LEVEL_7, LEVEL_8), List.of(GRADE10_EXAM, FOUNDATION),
        errors));
  }
}

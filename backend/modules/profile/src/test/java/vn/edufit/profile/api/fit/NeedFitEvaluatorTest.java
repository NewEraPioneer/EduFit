package vn.edufit.profile.api.fit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.edufit.profile.api.fit.FitFixtures.*;

import java.util.List;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NeedFitEvaluatorTest {

  private static final BiPredicate<Integer, Integer> ADJACENT = (a, b) -> Math.abs(a - b) == 1;

  private static NeedFitEvaluator.NeedFit fit(
      vn.edufit.profile.api.dto.LearningNeedView need, vn.edufit.profile.api.dto.TutorDiscoveryProfileDto tutor) {
    return NeedFitEvaluator.evaluate(need, tutor, ADJACENT).orElseThrow();
  }

  private static FitCheck check(NeedFitEvaluator.NeedFit fit, FitCriterion criterion) {
    return fit.check(criterion).orElseThrow(() -> new AssertionError("missing " + criterion));
  }

  @Test
  @DisplayName("Không có gói dạy đúng môn thì không đủ tư cách (bị loại)")
  void noOfferingInSubjectIsIneligible() {
    var tutor = tutor("ONLINE", null, List.of(), List.of(), offering().subject(SCIENCE, SCI_PHYSICS).build());
    assertTrue(NeedFitEvaluator.evaluate(need().build(), tutor, ADJACENT).isEmpty());
  }

  @Test
  @DisplayName("Chọn gói cùng nhánh trước, rồi tới gói có dạy đúng lớp")
  void bestOfferingPrefersSameTrackThenLevel() {
    var chemistry = offering().subject(SCIENCE, SCI_CHEMISTRY).levels(LEVEL_9).build();
    var physics = offering().subject(SCIENCE, SCI_PHYSICS).levels(LEVEL_8).build();
    var need = need().subject(SCIENCE, SCI_PHYSICS).build();
    assertEquals(physics.offeringId(), NeedFitEvaluator.bestOffering(need, List.of(chemistry, physics)).orElseThrow().offeringId());
  }

  @ParameterizedTest
  @CsvSource({"4,100,true", "3,50,false", "1,0,false"})
  @DisplayName("Khối lớp: đúng lớp 100, lớp liền kề 50, lớp khác 0 – không loại gia sư")
  void levelIsSoft(int taughtLevel, int score, boolean matched) {
    var tutor = tutor("BOTH", HA_NOI, List.of(CAU_GIAY), List.of(slot(2, 18, 22)), offering().levels(taughtLevel).build());
    var level = check(fit(need().build(), tutor), FitCriterion.LEVEL);
    assertEquals(score, level.score());
    assertEquals(matched, level.matched());
  }

  @Test
  @DisplayName("Mục tiêu premium chưa xác minh chuyên môn tính ½ và gắn cảnh báo")
  void unverifiedPremiumGoalScoresHalf() {
    var need = need().goal(GIFTED, "Bồi dưỡng HSG & Thi chuyên").textbook(null).build();
    var unverified = tutor("ONLINE", null, List.of(), List.of(), offering().goals(goal(GIFTED, true, false)).build());
    var verified = tutor("ONLINE", null, List.of(), List.of(), offering().goals(goal(GIFTED, true, true)).build());
    var missing = tutor("ONLINE", null, List.of(), List.of(), offering().goals(goal(FOUNDATION, false, false)).build());

    var half = check(fit(need, unverified), FitCriterion.GOAL);
    assertEquals(50, half.score());
    assertTrue(half.label().contains("chưa xác minh chuyên môn"));
    assertEquals(100, check(fit(need, verified), FitCriterion.GOAL).score());
    assertEquals(0, check(fit(need, missing), FitCriterion.GOAL).score());
    // Không cần bộ sách thì không xét tiêu chí bộ sách
    assertTrue(fit(need, verified).check(FitCriterion.TEXTBOOK).isEmpty());
  }

  @Test
  @DisplayName("Bộ sách: khớp khi gói dạy có đúng bộ sách của nhu cầu")
  void textbookMatch() {
    var tutor = tutor("ONLINE", null, List.of(), List.of(), offering().textbooks(CTST).build());
    assertFalse(check(fit(need().build(), tutor), FitCriterion.TEXTBOOK).matched());
  }

  @Test
  @DisplayName("Luyện thi vào 10: vị trí so theo tỉnh luyện đề")
  void examProvinceDrivesLocation() {
    var need = need().goal(GRADE10_EXAM, "Luyện thi vào 10").textbook(null).examProvince(HA_NOI).mode("ONLINE")
        .location(null, null).build();
    var drillsHanoi = tutor("ONLINE", null, List.of(), List.of(),
        offering().goals(goal(GRADE10_EXAM, false, false)).examProvinces(HA_NOI).build());
    var drillsDanang = tutor("ONLINE", null, List.of(), List.of(),
        offering().goals(goal(GRADE10_EXAM, false, false)).examProvinces(DA_NANG).build());
    assertTrue(check(fit(need, drillsHanoi), FitCriterion.LOCATION).matched());
    assertEquals(0, check(fit(need, drillsDanang), FitCriterion.LOCATION).score());
  }

  @Test
  @DisplayName("Hình thức/khu vực là tiêu chí mềm: lệch hình thức 0 điểm, cùng tỉnh khác khu vực 50")
  void locationIsSoft() {
    var offlineNeed = need().build();
    var sameDistrict = tutor("BOTH", HA_NOI, List.of(CAU_GIAY), List.of(), offering().build());
    var sameProvince = tutor("OFFLINE", HA_NOI, List.of(DONG_DA), List.of(), offering().build());
    var onlineOnly = tutor("ONLINE", null, List.of(), List.of(), offering().build());

    assertTrue(check(fit(offlineNeed, sameDistrict), FitCriterion.LOCATION).matched());
    assertEquals(50, check(fit(offlineNeed, sameProvince), FitCriterion.LOCATION).score());
    assertEquals(0, check(fit(offlineNeed, onlineOnly), FitCriterion.LOCATION).score());
    // Học cả hai hình thức thì gia sư chỉ dạy online vẫn được một phần
    assertEquals(50, check(fit(need().mode("BOTH").build(), onlineOnly), FitCriterion.LOCATION).score());
    // Học online mà gia sư chỉ dạy trực tiếp: lệch
    var offlineOnly = tutor("OFFLINE", HA_NOI, List.of(CAU_GIAY), List.of(), offering().build());
    assertEquals(0, check(fit(need().mode("ONLINE").location(null, null).build(), offlineOnly), FitCriterion.LOCATION).score());
  }

  @ParameterizedTest
  @CsvSource({"300000,100", "345000,75", "390000,50", "420000,25", "450000,0"})
  @DisplayName("Ngân sách: trong ngân sách 100; vượt tới 30% giảm về 50; vượt tới 50% giảm về 0")
  void budgetUsesTwoLinearSegments(long price, int score) {
    var tutor = tutor("BOTH", HA_NOI, List.of(CAU_GIAY), List.of(), offering().price(price).build());
    assertEquals(score, check(fit(need().build(), tutor), FitCriterion.BUDGET).score());
  }

  @Test
  @DisplayName("Lịch: tính theo phút trùng; gộp khung giờ chồng nhau; gia sư chưa khai báo lịch tính ½")
  void scheduleUsesMinutesAndMergesSlots() {
    var need = need().slots(List.of(slot(2, 18, 20), slot(2, 19, 21))).build();
    var halfOverlap = tutor("ONLINE", null, List.of(), List.of(slot(2, 18, 19), slot(2, 18, 19), slot(2, 20, 21)), offering().build());
    assertEquals(67, check(fit(need, halfOverlap), FitCriterion.SCHEDULE).score());
    var noSlots = tutor("ONLINE", null, List.of(), List.of(), offering().build());
    assertEquals(50, check(fit(need, noSlots), FitCriterion.SCHEDULE).score());
    var otherDay = tutor("ONLINE", null, List.of(), List.of(slot(3, 18, 21)), offering().build());
    assertEquals(0, check(fit(need, otherDay), FitCriterion.SCHEDULE).score());
  }
}

package vn.edufit.profile.api.fit;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import vn.edufit.profile.api.dto.CatalogRef;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.OfferingGoalDto;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.TutorOfferingDto;
import vn.edufit.profile.api.dto.WeeklyAvailabilityDto;

/**
 * So nhu cầu học với một gia sư theo từng tiêu chí (lọc mềm). Dùng chung cho AI Match, Discovery
 * và hộp thư yêu cầu kết nối để nhãn ✔/⚠ nhất quán ở cả hai phía.
 *
 * <p>Điều kiện tư cách duy nhất là gia sư có gói dạy đúng môn; mọi tiêu chí khác chỉ cho điểm.
 * Tiêu chí không áp dụng cho nhu cầu (ví dụ bộ sách khi Luyện thi vào 10) không xuất hiện trong kết quả.
 */
public final class NeedFitEvaluator {

  private NeedFitEvaluator() {}

  /** Kết quả so khớp: gói dạy được dùng để so và danh sách tiêu chí. */
  public record NeedFit(TutorOfferingDto offering, List<FitCheck> checks) {
    public NeedFit {
      checks = List.copyOf(checks);
    }

    public Optional<FitCheck> check(FitCriterion criterion) {
      return checks.stream().filter(check -> check.criterion() == criterion).findFirst();
    }
  }

  /**
   * Gói dạy phù hợp nhất cho nhu cầu: cùng môn; ưu tiên cùng nhánh, có dạy đúng lớp, có mục tiêu,
   * rồi học phí thấp hơn. Rỗng khi gia sư không dạy môn này (không đủ tư cách).
   */
  public static Optional<TutorOfferingDto> bestOffering(LearningNeedView need, List<TutorOfferingDto> offerings) {
    return offerings.stream()
        .filter(offering -> offering.subjectId().equals(need.subjectId()))
        .min(Comparator
            .comparing((TutorOfferingDto offering) -> !Objects.equals(offering.trackId(), need.trackId()))
            .thenComparing(offering -> !offering.teachesLevel(need.educationLevelId()))
            .thenComparing(offering -> offering.goals().stream()
                .noneMatch(goal -> goal.goalTagId().equals(need.goalTagId())))
            .thenComparingLong(TutorOfferingDto::pricePerSession));
  }

  /** So khớp theo góc nhìn học sinh/phụ huynh (mặc định cho Discovery, AI Match, form gửi yêu cầu). */
  public static Optional<NeedFit> evaluate(
      LearningNeedView need, TutorDiscoveryProfileDto tutor, BiPredicate<Integer, Integer> adjacentLevels) {
    return evaluate(need, tutor, adjacentLevels, FitAudience.LEARNER);
  }

  /**
   * So khớp với câu chữ theo người đọc. Điểm số và ✔/⚠ không phụ thuộc {@code audience};
   * chỉ nhãn được viết lại để gia sư đọc đúng góc nhìn của mình.
   */
  public static Optional<NeedFit> evaluate(
      LearningNeedView need, TutorDiscoveryProfileDto tutor, BiPredicate<Integer, Integer> adjacentLevels,
      FitAudience audience) {
    Voice voice = new Voice(Objects.requireNonNull(audience, "audience"));
    return bestOffering(need, tutor.offerings())
        .map(offering -> new NeedFit(offering, checks(need, tutor, offering, adjacentLevels, voice)));
  }

  /** Chọn câu theo người đọc: học sinh/phụ huynh hay gia sư. */
  private record Voice(FitAudience audience) {
    String say(String learner, String tutor) {
      return audience == FitAudience.TUTOR ? tutor : learner;
    }
  }

  private static List<FitCheck> checks(
      LearningNeedView need, TutorDiscoveryProfileDto tutor, TutorOfferingDto offering,
      BiPredicate<Integer, Integer> adjacentLevels, Voice voice) {
    var checks = new ArrayList<FitCheck>();
    checks.add(level(need, offering, adjacentLevels, voice));
    checks.add(goal(need, offering, voice));
    if (need.textbookSeriesId() != null) {
      checks.add(textbook(need, offering, voice));
    }
    if (need.mode() != null) {
      checks.add(location(need, tutor, offering, voice));
    }
    if (need.budgetMaxPerSession() != null && need.budgetMaxPerSession() > 0) {
      checks.add(budget(need.budgetMaxPerSession(), offering.pricePerSession(), voice));
    }
    if (!need.availabilitySlots().isEmpty()) {
      checks.add(schedule(need.availabilitySlots(), tutor.availabilitySlots(), voice));
    }
    return checks;
  }

  private static FitCheck level(
      LearningNeedView need, TutorOfferingDto offering, BiPredicate<Integer, Integer> adjacentLevels, Voice voice) {
    String wanted = Objects.requireNonNullElse(need.educationLevelName(), "khối lớp này");
    if (need.educationLevelId() != null && offering.teachesLevel(need.educationLevelId())) {
      return FitCheck.match(FitCriterion.LEVEL,
          voice.say("Dạy " + wanted, "Học sinh " + wanted + " – đúng lớp bạn dạy"));
    }
    String taught = names(offering.levels());
    String label = voice.say("Chưa dạy " + wanted + " (đang dạy " + taught + ")",
        "Học sinh " + wanted + " – bạn đang dạy " + taught);
    boolean adjacent = need.educationLevelId() != null && offering.levels().stream()
        .anyMatch(level -> adjacentLevels.test(level.id(), need.educationLevelId()));
    return adjacent
        ? FitCheck.partial(FitCriterion.LEVEL, 50, label)
        : FitCheck.mismatch(FitCriterion.LEVEL, label);
  }

  private static FitCheck goal(LearningNeedView need, TutorOfferingDto offering, Voice voice) {
    String wanted = Objects.requireNonNullElse(need.goalTagName(), "mục tiêu này");
    Optional<OfferingGoalDto> goal = offering.goals().stream()
        .filter(item -> item.goalTagId().equals(need.goalTagId()))
        .findFirst();
    if (goal.isEmpty()) {
      return FitCheck.mismatch(FitCriterion.GOAL, voice.say("Gói dạy chưa có mục tiêu " + wanted,
          "Học sinh cần " + wanted + " – gói dạy của bạn chưa có mục tiêu này"));
    }
    if (goal.get().premium() && !goal.get().evidenceVerified()) {
      return FitCheck.partial(FitCriterion.GOAL, 50, voice.say("Nhận dạy " + wanted + " – chưa xác minh chuyên môn",
          "Học sinh cần " + wanted + " – bạn chưa xác minh chuyên môn"));
    }
    return FitCheck.match(FitCriterion.GOAL,
        voice.say("Nhận dạy " + wanted, "Học sinh cần " + wanted + " – bạn có nhận dạy"));
  }

  private static FitCheck textbook(LearningNeedView need, TutorOfferingDto offering, Voice voice) {
    String wanted = Objects.requireNonNullElse(need.textbookSeriesName(), "bộ sách này");
    boolean teaches = offering.textbooks().stream().anyMatch(book -> book.id().equals(need.textbookSeriesId()));
    return teaches
        ? FitCheck.match(FitCriterion.TEXTBOOK, voice.say("Dạy bộ sách " + wanted,
            "Học sinh học bộ sách " + wanted + " – bạn có dạy"))
        : FitCheck.mismatch(FitCriterion.TEXTBOOK, voice.say("Chưa khai báo dạy bộ sách " + wanted,
            "Học sinh học bộ sách " + wanted + " – bạn chưa khai báo"));
  }

  /**
   * Vị trí: với Luyện thi vào 10 so tỉnh luyện đề; còn lại so hình thức học và khu vực dạy trực tiếp.
   */
  private static FitCheck location(
      LearningNeedView need, TutorDiscoveryProfileDto tutor, TutorOfferingDto offering, Voice voice) {
    String needMode = need.mode();
    String tutorMode = Objects.requireNonNullElse(tutor.tutor().teachingMode(), "");
    boolean tutorOnline = "ONLINE".equals(tutorMode) || "BOTH".equals(tutorMode);
    boolean tutorInPerson = "OFFLINE".equals(tutorMode) || "BOTH".equals(tutorMode);
    boolean modeCompatible = switch (needMode) {
      case "ONLINE" -> tutorOnline;
      case "OFFLINE" -> tutorInPerson;
      default -> tutorOnline || tutorInPerson;
    };
    if (!modeCompatible) {
      return FitCheck.mismatch(FitCriterion.LOCATION, "ONLINE".equals(needMode)
          ? voice.say("Gia sư chỉ dạy trực tiếp", "Học sinh muốn học trực tuyến – bạn chỉ dạy trực tiếp")
          : voice.say("Gia sư chỉ dạy trực tuyến", "Học sinh muốn học trực tiếp – bạn chỉ dạy trực tuyến"));
    }
    if (need.examProvinceId() != null) {
      String wanted = Objects.requireNonNullElse(need.examProvinceName(), "tỉnh dự thi");
      boolean drills = offering.examProvinces().stream().anyMatch(p -> p.id().equals(need.examProvinceId()));
      return drills
          ? FitCheck.match(FitCriterion.LOCATION, voice.say("Luyện đề thi vào 10 " + wanted,
              "Học sinh thi vào 10 tại " + wanted + " – bạn có luyện đề"))
          : FitCheck.mismatch(FitCriterion.LOCATION, voice.say("Chưa luyện đề thi vào 10 " + wanted,
              "Học sinh thi vào 10 tại " + wanted + " – bạn chưa luyện đề tỉnh này"));
    }
    if ("ONLINE".equals(needMode)) {
      return FitCheck.match(FitCriterion.LOCATION,
          voice.say("Dạy trực tuyến", "Học sinh học trực tuyến – bạn có dạy trực tuyến"));
    }
    boolean sameProvince = tutorInPerson && need.provinceId() != null
        && need.provinceId().equals(tutor.tutor().provinceId());
    boolean sameDistrict = sameProvince && need.districtId() != null
        && tutor.districts().stream().anyMatch(d -> d.id().equals(need.districtId()));
    String place = need.districtName() != null ? need.districtName() : need.provinceName();
    if (sameDistrict || (sameProvince && need.districtId() == null)) {
      return FitCheck.match(FitCriterion.LOCATION, voice.say("Dạy trực tiếp tại " + place,
          "Học sinh ở " + place + " – trong khu vực bạn dạy"));
    }
    if (sameProvince) {
      return FitCheck.partial(FitCriterion.LOCATION, 50, voice.say(
          "Dạy trực tiếp tại " + need.provinceName() + " nhưng chưa khai báo khu vực " + need.districtName(),
          "Học sinh ở " + need.districtName() + " – bạn chưa khai báo dạy khu vực này"));
    }
    String where = Objects.requireNonNullElse(place, "khu vực khác");
    if ("BOTH".equals(needMode) && tutorOnline) {
      return FitCheck.partial(FitCriterion.LOCATION, 50, voice.say(
          "Chỉ học trực tuyến được (không dạy trực tiếp tại " + place + ")",
          "Học sinh ở " + where + " – chỉ dạy trực tuyến được"));
    }
    return FitCheck.mismatch(FitCriterion.LOCATION, voice.say(
        "Không dạy trực tiếp tại " + Objects.requireNonNullElse(place, "khu vực của bạn"),
        "Học sinh ở " + where + " – ngoài khu vực bạn dạy"));
  }

  private static FitCheck budget(long budgetMax, long price, Voice voice) {
    String priceText = money(price);
    String budgetText = money(budgetMax);
    if (price <= budgetMax) {
      return FitCheck.match(FitCriterion.BUDGET, voice.say("Học phí " + priceText + "/buổi trong ngân sách",
          "Ngân sách học sinh " + budgetText + "/buổi – đủ học phí " + priceText + " của bạn"));
    }
    double over = (price - budgetMax) / (double) budgetMax;
    int score = over <= 0.3
        ? (int) Math.round(100 - over * 50 / 0.3)
        : (int) Math.max(0, Math.round(50 - (over - 0.3) * 50 / 0.2));
    long overPercent = Math.round(over * 100);
    return FitCheck.partial(FitCriterion.BUDGET, score, voice.say(
        "Học phí " + priceText + "/buổi vượt ngân sách " + overPercent + "%",
        "Ngân sách học sinh " + budgetText + "/buổi – học phí " + priceText + " của bạn vượt " + overPercent + "%"));
  }

  private static FitCheck schedule(
      List<WeeklyAvailabilityDto> requested, List<WeeklyAvailabilityDto> offered, Voice voice) {
    if (offered.isEmpty()) {
      return FitCheck.partial(FitCriterion.SCHEDULE, 50,
          voice.say("Gia sư chưa khai báo lịch rảnh", "Bạn chưa khai báo lịch rảnh để so với lịch học sinh"));
    }
    var wanted = merge(requested);
    var available = merge(offered);
    long wantedMinutes = wanted.stream().mapToLong(NeedFitEvaluator::minutes).sum();
    long overlapMinutes = 0;
    for (var slot : wanted) {
      for (var free : available) {
        if (slot.dayOfWeek().equals(free.dayOfWeek())
            && slot.startTime().isBefore(free.endTime()) && free.startTime().isBefore(slot.endTime())) {
          var start = slot.startTime().isAfter(free.startTime()) ? slot.startTime() : free.startTime();
          var end = slot.endTime().isBefore(free.endTime()) ? slot.endTime() : free.endTime();
          overlapMinutes += Duration.between(start, end).toMinutes();
        }
      }
    }
    int score = wantedMinutes == 0 ? 0 : (int) Math.round(overlapMinutes * 100.0 / wantedMinutes);
    if (score >= 100) {
      return FitCheck.match(FitCriterion.SCHEDULE,
          voice.say("Rảnh đủ các khung giờ bạn muốn", "Bạn rảnh đủ các khung giờ học sinh muốn học"));
    }
    return score == 0
        ? FitCheck.mismatch(FitCriterion.SCHEDULE,
            voice.say("Không trùng khung giờ bạn muốn", "Lịch rảnh của bạn không trùng khung giờ học sinh muốn"))
        : FitCheck.partial(FitCriterion.SCHEDULE, score, voice.say("Trùng " + score + "% thời gian bạn muốn học",
            "Lịch rảnh của bạn trùng " + score + "% thời gian học sinh muốn học"));
  }

  private static List<WeeklyAvailabilityDto> merge(List<WeeklyAvailabilityDto> slots) {
    var sorted = slots.stream()
        .filter(slot -> slot.startTime().isBefore(slot.endTime()))
        .sorted(Comparator.comparing(WeeklyAvailabilityDto::dayOfWeek)
            .thenComparing(WeeklyAvailabilityDto::startTime))
        .toList();
    var merged = new ArrayList<WeeklyAvailabilityDto>();
    for (var slot : sorted) {
      if (!merged.isEmpty()) {
        var last = merged.getLast();
        if (last.dayOfWeek().equals(slot.dayOfWeek()) && !slot.startTime().isAfter(last.endTime())) {
          merged.set(merged.size() - 1, new WeeklyAvailabilityDto(last.dayOfWeek(), last.startTime(),
              last.endTime().isAfter(slot.endTime()) ? last.endTime() : slot.endTime()));
          continue;
        }
      }
      merged.add(slot);
    }
    return merged;
  }

  private static long minutes(WeeklyAvailabilityDto slot) {
    return Duration.between(slot.startTime(), slot.endTime()).toMinutes();
  }

  private static String names(List<CatalogRef> refs) {
    return refs.isEmpty() ? "chưa khai báo lớp" : String.join(", ", refs.stream().map(CatalogRef::name).toList());
  }

  private static String money(long amount) {
    return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount) + "đ";
  }
}

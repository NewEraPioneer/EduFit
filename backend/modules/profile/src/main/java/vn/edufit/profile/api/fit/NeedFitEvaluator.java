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

  public static Optional<NeedFit> evaluate(
      LearningNeedView need, TutorDiscoveryProfileDto tutor, BiPredicate<Integer, Integer> adjacentLevels) {
    return bestOffering(need, tutor.offerings())
        .map(offering -> new NeedFit(offering, checks(need, tutor, offering, adjacentLevels)));
  }

  private static List<FitCheck> checks(
      LearningNeedView need, TutorDiscoveryProfileDto tutor, TutorOfferingDto offering,
      BiPredicate<Integer, Integer> adjacentLevels) {
    var checks = new ArrayList<FitCheck>();
    checks.add(level(need, offering, adjacentLevels));
    checks.add(goal(need, offering));
    if (need.textbookSeriesId() != null) {
      checks.add(textbook(need, offering));
    }
    if (need.mode() != null) {
      checks.add(location(need, tutor, offering));
    }
    if (need.budgetMaxPerSession() != null && need.budgetMaxPerSession() > 0) {
      checks.add(budget(need.budgetMaxPerSession(), offering.pricePerSession()));
    }
    if (!need.availabilitySlots().isEmpty()) {
      checks.add(schedule(need.availabilitySlots(), tutor.availabilitySlots()));
    }
    return checks;
  }

  private static FitCheck level(
      LearningNeedView need, TutorOfferingDto offering, BiPredicate<Integer, Integer> adjacentLevels) {
    String wanted = Objects.requireNonNullElse(need.educationLevelName(), "khối lớp này");
    if (need.educationLevelId() != null && offering.teachesLevel(need.educationLevelId())) {
      return FitCheck.match(FitCriterion.LEVEL, "Dạy " + wanted);
    }
    String taught = names(offering.levels());
    boolean adjacent = need.educationLevelId() != null && offering.levels().stream()
        .anyMatch(level -> adjacentLevels.test(level.id(), need.educationLevelId()));
    return adjacent
        ? FitCheck.partial(FitCriterion.LEVEL, 50, "Chưa dạy " + wanted + " (đang dạy " + taught + ")")
        : FitCheck.mismatch(FitCriterion.LEVEL, "Chưa dạy " + wanted + " (đang dạy " + taught + ")");
  }

  private static FitCheck goal(LearningNeedView need, TutorOfferingDto offering) {
    String wanted = Objects.requireNonNullElse(need.goalTagName(), "mục tiêu này");
    Optional<OfferingGoalDto> goal = offering.goals().stream()
        .filter(item -> item.goalTagId().equals(need.goalTagId()))
        .findFirst();
    if (goal.isEmpty()) {
      return FitCheck.mismatch(FitCriterion.GOAL, "Gói dạy chưa có mục tiêu " + wanted);
    }
    if (goal.get().premium() && !goal.get().evidenceVerified()) {
      return FitCheck.partial(FitCriterion.GOAL, 50, "Nhận dạy " + wanted + " – chưa xác minh chuyên môn");
    }
    return FitCheck.match(FitCriterion.GOAL, "Nhận dạy " + wanted);
  }

  private static FitCheck textbook(LearningNeedView need, TutorOfferingDto offering) {
    String wanted = Objects.requireNonNullElse(need.textbookSeriesName(), "bộ sách này");
    boolean teaches = offering.textbooks().stream().anyMatch(book -> book.id().equals(need.textbookSeriesId()));
    return teaches
        ? FitCheck.match(FitCriterion.TEXTBOOK, "Dạy bộ sách " + wanted)
        : FitCheck.mismatch(FitCriterion.TEXTBOOK, "Chưa khai báo dạy bộ sách " + wanted);
  }

  /**
   * Vị trí: với Luyện thi vào 10 so tỉnh luyện đề; còn lại so hình thức học và khu vực dạy trực tiếp.
   */
  private static FitCheck location(LearningNeedView need, TutorDiscoveryProfileDto tutor, TutorOfferingDto offering) {
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
      return FitCheck.mismatch(FitCriterion.LOCATION,
          "ONLINE".equals(needMode) ? "Gia sư chỉ dạy trực tiếp" : "Gia sư chỉ dạy trực tuyến");
    }
    if (need.examProvinceId() != null) {
      String wanted = Objects.requireNonNullElse(need.examProvinceName(), "tỉnh dự thi");
      boolean drills = offering.examProvinces().stream().anyMatch(p -> p.id().equals(need.examProvinceId()));
      return drills
          ? FitCheck.match(FitCriterion.LOCATION, "Luyện đề thi vào 10 " + wanted)
          : FitCheck.mismatch(FitCriterion.LOCATION, "Chưa luyện đề thi vào 10 " + wanted);
    }
    if ("ONLINE".equals(needMode)) {
      return FitCheck.match(FitCriterion.LOCATION, "Dạy trực tuyến");
    }
    boolean sameProvince = tutorInPerson && need.provinceId() != null
        && need.provinceId().equals(tutor.tutor().provinceId());
    boolean sameDistrict = sameProvince && need.districtId() != null
        && tutor.districts().stream().anyMatch(d -> d.id().equals(need.districtId()));
    String place = need.districtName() != null ? need.districtName() : need.provinceName();
    if (sameDistrict || (sameProvince && need.districtId() == null)) {
      return FitCheck.match(FitCriterion.LOCATION, "Dạy trực tiếp tại " + place);
    }
    if (sameProvince) {
      return FitCheck.partial(FitCriterion.LOCATION, 50,
          "Dạy trực tiếp tại " + need.provinceName() + " nhưng chưa khai báo khu vực " + need.districtName());
    }
    if ("BOTH".equals(needMode) && tutorOnline) {
      return FitCheck.partial(FitCriterion.LOCATION, 50, "Chỉ học trực tuyến được (không dạy trực tiếp tại " + place + ")");
    }
    return FitCheck.mismatch(FitCriterion.LOCATION, "Không dạy trực tiếp tại " + Objects.requireNonNullElse(place, "khu vực của bạn"));
  }

  private static FitCheck budget(long budgetMax, long price) {
    String priceText = money(price);
    if (price <= budgetMax) {
      return FitCheck.match(FitCriterion.BUDGET, "Học phí " + priceText + "/buổi trong ngân sách");
    }
    double over = (price - budgetMax) / (double) budgetMax;
    int score = over <= 0.3
        ? (int) Math.round(100 - over * 50 / 0.3)
        : (int) Math.max(0, Math.round(50 - (over - 0.3) * 50 / 0.2));
    return FitCheck.partial(FitCriterion.BUDGET, score,
        "Học phí " + priceText + "/buổi vượt ngân sách " + Math.round(over * 100) + "%");
  }

  private static FitCheck schedule(List<WeeklyAvailabilityDto> requested, List<WeeklyAvailabilityDto> offered) {
    if (offered.isEmpty()) {
      return FitCheck.partial(FitCriterion.SCHEDULE, 50, "Gia sư chưa khai báo lịch rảnh");
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
      return FitCheck.match(FitCriterion.SCHEDULE, "Rảnh đủ các khung giờ bạn muốn");
    }
    return score == 0
        ? FitCheck.mismatch(FitCriterion.SCHEDULE, "Không trùng khung giờ bạn muốn")
        : FitCheck.partial(FitCriterion.SCHEDULE, score, "Trùng " + score + "% thời gian bạn muốn học");
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

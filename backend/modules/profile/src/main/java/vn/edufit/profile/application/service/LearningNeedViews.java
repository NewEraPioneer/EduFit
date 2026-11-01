package vn.edufit.profile.application.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.WeeklyAvailabilityDto;
import vn.edufit.profile.infra.persistence.entity.District;
import vn.edufit.profile.infra.persistence.entity.EducationLevel;
import vn.edufit.profile.infra.persistence.entity.GoalTag;
import vn.edufit.profile.infra.persistence.entity.LearningNeed;
import vn.edufit.profile.infra.persistence.entity.LearningNeedSlot;
import vn.edufit.profile.infra.persistence.entity.NeedStatus;
import vn.edufit.profile.infra.persistence.entity.Province;
import vn.edufit.profile.infra.persistence.entity.SpecializedSchool;
import vn.edufit.profile.infra.persistence.entity.Subject;
import vn.edufit.profile.infra.persistence.entity.SubjectTrack;
import vn.edufit.profile.infra.persistence.entity.TeachingMode;
import vn.edufit.profile.infra.persistence.entity.TextbookSeries;

/** Dựng {@link LearningNeedView} và tính trạng thái đủ/thiếu thông tin theo danh mục. */
final class LearningNeedViews {

  private LearningNeedViews() {}

  /** Các trường còn thiếu để nhu cầu được ghép gia sư, theo đúng tên trường của request. */
  static List<String> missingFields(LearningNeed need, LearningCatalog catalog, int slotCount) {
    var missing = new ArrayList<String>();
    if (need.getEducationLevelId() == null) missing.add("educationLevelId");
    if (need.getTrackId() == null && catalog.subjectHasTracks(need.getSubjectId())) missing.add("trackId");
    GoalTag tag = catalog.goalTag(need.getGoalTagId()).orElse(null);
    if (tag == null) {
      missing.add("goalTagId");
    } else {
      if (tag.requiresTextbook() && need.getTextbookSeriesId() == null) missing.add("textbookSeriesId");
      if (tag.requiresExamProvince() && need.getExamProvinceId() == null) missing.add("examProvinceId");
    }
    if (need.getMode() == null) missing.add("mode");
    if (need.getMode() != null && need.getMode() != TeachingMode.ONLINE && need.getProvinceId() == null) {
      missing.add("provinceId");
    }
    if (need.getBudgetMaxPerSession() == null) missing.add("budgetMaxPerSession");
    if (slotCount == 0) missing.add("availabilitySlots");
    return missing;
  }

  static NeedStatus statusFor(LearningNeed need, LearningCatalog catalog, int slotCount) {
    if (need.getStatus() == NeedStatus.ARCHIVED) return NeedStatus.ARCHIVED;
    return missingFields(need, catalog, slotCount).isEmpty() ? NeedStatus.ACTIVE : NeedStatus.INCOMPLETE;
  }

  static List<LearningNeedView> fromAll(
      Collection<LearningNeed> needs, Map<UUID, UUID> studentUserIds, Collection<LearningNeedSlot> slots,
      LearningCatalog catalog) {
    Map<UUID, List<WeeklyAvailabilityDto>> byNeed = slots.stream()
        .collect(Collectors.groupingBy(
            LearningNeedSlot::getNeedId,
            Collectors.mapping(
                slot -> new WeeklyAvailabilityDto(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime()),
                Collectors.toList())));
    return needs.stream()
        .map(need -> from(need, studentUserIds.get(need.getStudentId()),
            byNeed.getOrDefault(need.getNeedId(), List.of()), catalog))
        .toList();
  }

  static LearningNeedView from(
      LearningNeed need, UUID studentUserId, List<WeeklyAvailabilityDto> slots, LearningCatalog catalog) {
    GoalTag tag = catalog.goalTag(need.getGoalTagId()).orElse(null);
    List<String> missing = need.getStatus() == NeedStatus.ARCHIVED
        ? List.of() : missingFields(need, catalog, slots.size());
    return new LearningNeedView(
        need.getNeedId(),
        need.getStudentId(),
        studentUserId,
        need.getCreatedBy(),
        need.getEducationLevelId(),
        catalog.level(need.getEducationLevelId()).map(EducationLevel::getName).orElse(null),
        need.getSubjectId(),
        catalog.subject(need.getSubjectId()).map(Subject::getName).orElse(null),
        need.getTrackId(),
        catalog.track(need.getTrackId()).map(SubjectTrack::getName).orElse(null),
        need.getGoalTagId(),
        tag == null ? null : tag.getCode(),
        tag == null ? null : tag.getName(),
        tag != null && tag.isPremium(),
        need.getTextbookSeriesId(),
        catalog.textbook(need.getTextbookSeriesId()).map(TextbookSeries::getName).orElse(null),
        need.getExamProvinceId(),
        catalog.province(need.getExamProvinceId()).map(Province::getName).orElse(null),
        need.getTargetSchoolId(),
        catalog.school(need.getTargetSchoolId()).map(SpecializedSchool::getName).orElse(null),
        need.getMode() == null ? null : need.getMode().name(),
        need.getProvinceId(),
        catalog.province(need.getProvinceId()).map(Province::getName).orElse(null),
        need.getDistrictId(),
        catalog.district(need.getDistrictId()).map(District::getName).orElse(null),
        need.getBudgetMaxPerSession(),
        need.getStatus().name(),
        slots,
        missing,
        need.getCreatedAt(),
        need.getUpdatedAt());
  }
}

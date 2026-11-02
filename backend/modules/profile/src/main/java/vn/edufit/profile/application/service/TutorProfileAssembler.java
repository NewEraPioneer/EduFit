package vn.edufit.profile.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import vn.edufit.profile.api.dto.CatalogRef;
import vn.edufit.profile.api.dto.OfferingGoalDto;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.TutorOfferingDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.profile.api.dto.WeeklyAvailabilityDto;
import vn.edufit.profile.infra.persistence.entity.District;
import vn.edufit.profile.infra.persistence.entity.EducationLevel;
import vn.edufit.profile.infra.persistence.entity.GoalTag;
import vn.edufit.profile.infra.persistence.entity.Province;
import vn.edufit.profile.infra.persistence.entity.Subject;
import vn.edufit.profile.infra.persistence.entity.SubjectTrack;
import vn.edufit.profile.infra.persistence.entity.TextbookSeries;
import vn.edufit.profile.infra.persistence.entity.TutorOffering;
import vn.edufit.profile.infra.persistence.entity.TutorProfile;
import vn.edufit.profile.infra.persistence.repository.TutorAvailabilitySlotRepository;
import vn.edufit.profile.infra.persistence.repository.TutorOfferingRepository;

/** Dựng các DTO hồ sơ gia sư và gói dạy (tra tên từ danh mục, nạp theo lô). */
@Component
public class TutorProfileAssembler {

  private final TutorOfferingRepository offerings;
  private final TutorAvailabilitySlotRepository slots;
  private final LearningCatalogLoader catalogLoader;

  public TutorProfileAssembler(
      TutorOfferingRepository offerings,
      TutorAvailabilitySlotRepository slots,
      LearningCatalogLoader catalogLoader) {
    this.offerings = offerings;
    this.slots = slots;
    this.catalogLoader = catalogLoader;
  }

  public TutorSummaryDto summary(TutorProfile profile) {
    return summary(profile, catalogLoader.load());
  }

  public List<TutorDiscoveryProfileDto> discoveryProfiles(List<TutorProfile> profiles) {
    if (profiles.isEmpty()) {
      return List.of();
    }
    LearningCatalog catalog = catalogLoader.load();
    Set<UUID> tutorIds = profiles.stream().map(TutorProfile::getTutorId).collect(Collectors.toSet());
    Map<UUID, List<TutorOfferingDto>> offeringsByTutor = offerings.findByTutorIdIn(tutorIds).stream()
        .sorted(Comparator.comparing(TutorOffering::getCreatedAt).thenComparing(TutorOffering::getOfferingId))
        .collect(Collectors.groupingBy(TutorOffering::getTutorId,
            Collectors.mapping(offering -> offering(offering, catalog), Collectors.toList())));
    Map<UUID, List<WeeklyAvailabilityDto>> slotsByTutor = slots
        .findByTutorIdInOrderByDayOfWeekAscStartTimeAsc(tutorIds).stream()
        .collect(Collectors.groupingBy(
            slot -> slot.getTutorId(),
            Collectors.mapping(
                slot -> new WeeklyAvailabilityDto(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime()),
                Collectors.toList())));
    return profiles.stream()
        .map(profile -> new TutorDiscoveryProfileDto(
            summary(profile, catalog),
            offeringsByTutor.getOrDefault(profile.getTutorId(), List.of()),
            districts(profile.getDistrictIds(), catalog),
            slotsByTutor.getOrDefault(profile.getTutorId(), List.of()),
            profile.getCreatedAt()))
        .toList();
  }

  public List<TutorOfferingDto> offeringsOf(UUID tutorId) {
    LearningCatalog catalog = catalogLoader.load();
    return offerings.findByTutorIdOrderByCreatedAtAsc(tutorId).stream()
        .map(offering -> offering(offering, catalog))
        .toList();
  }

  List<CatalogRef> districts(Set<Integer> districtIds, LearningCatalog catalog) {
    return districtIds.stream()
        .map(id -> catalog.district(id).orElse(null))
        .filter(java.util.Objects::nonNull)
        .sorted(Comparator.comparing(District::getSortOrder).thenComparing(District::getDistrictId))
        .map(district -> new CatalogRef(district.getDistrictId(), district.getName()))
        .toList();
  }

  TutorSummaryDto summary(TutorProfile profile, LearningCatalog catalog) {
    return new TutorSummaryDto(
        profile.getTutorId(),
        profile.getUserId(),
        profile.getDisplayName(),
        profile.getHeadline(),
        profile.getBio(),
        profile.getTeachingMode() != null ? profile.getTeachingMode().name() : null,
        profile.getTutorType() != null ? profile.getTutorType().name() : null,
        profile.isTutorTypeVerified(),
        profile.getProvinceId(),
        catalog.province(profile.getProvinceId()).map(Province::getName).orElse(null),
        profile.getExperienceYears(),
        profile.getTeachingMethod(),
        profile.getStatus() != null ? profile.getStatus().name() : null,
        profile.getVerifiedAt(),
        profile.getRatingAvg(),
        profile.getReviewCount());
  }

  TutorOfferingDto offering(TutorOffering offering, LearningCatalog catalog) {
    Map<Integer, Boolean> evidence = offering.goalEvidence();
    List<CatalogRef> levels = offering.getLevelIds().stream()
        .map(id -> catalog.level(id).orElse(null))
        .filter(java.util.Objects::nonNull)
        .sorted(Comparator.comparing(EducationLevel::getSortOrder))
        .map(level -> new CatalogRef(level.getLevelId(), level.getName()))
        .toList();
    List<OfferingGoalDto> goals = evidence.keySet().stream()
        .map(id -> catalog.goalTag(id).orElse(null))
        .filter(java.util.Objects::nonNull)
        .sorted(Comparator.comparing(GoalTag::getSortOrder))
        .map(tag -> new OfferingGoalDto(tag.getGoalTagId(), tag.getCode(), tag.getName(), tag.isPremium(),
            Boolean.TRUE.equals(evidence.get(tag.getGoalTagId()))))
        .toList();
    List<CatalogRef> textbooks = offering.getTextbookIds().stream()
        .map(id -> catalog.textbook(id).orElse(null))
        .filter(java.util.Objects::nonNull)
        .sorted(Comparator.comparing(TextbookSeries::getSortOrder))
        .map(series -> new CatalogRef(series.getSeriesId(), series.getName()))
        .toList();
    List<CatalogRef> examProvinces = offering.getExamProvinceIds().stream()
        .map(id -> catalog.province(id).orElse(null))
        .filter(java.util.Objects::nonNull)
        .sorted(Comparator.comparing(Province::getSortOrder))
        .map(province -> new CatalogRef(province.getProvinceId(), province.getName()))
        .toList();
    return new TutorOfferingDto(
        offering.getOfferingId(),
        offering.getSubjectId(),
        catalog.subject(offering.getSubjectId()).map(Subject::getName).orElse(null),
        offering.getTrackId(),
        catalog.track(offering.getTrackId()).map(SubjectTrack::getName).orElse(null),
        offering.getPricePerSession(),
        levels,
        goals,
        textbooks,
        examProvinces);
  }
}

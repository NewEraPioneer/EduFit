package vn.edufit.discovery.web.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import vn.edufit.profile.api.dto.CatalogRef;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.WeeklyAvailabilityDto;

public record TutorDetailResponse(
    UUID tutorId,
    String displayName,
    String headline,
    String bio,
    String teachingMethod,
    String teachingMode,
    String tutorType,
    boolean tutorTypeVerified,
    String provinceName,
    List<CatalogRef> districts,
    Short experienceYears,
    BigDecimal ratingAvg,
    Integer reviewCount,
    boolean verified,
    List<OfferingResponse> offerings,
    List<WeeklyAvailabilityDto> availabilitySlots
) {

  public static TutorDetailResponse from(TutorDiscoveryProfileDto profile) {
    var tutor = profile.tutor();
    return new TutorDetailResponse(
        tutor.tutorId(), tutor.displayName(), tutor.headline(), tutor.bio(), tutor.teachingMethod(),
        tutor.teachingMode(), tutor.tutorType(), tutor.tutorTypeVerified(), tutor.provinceName(),
        profile.districts(), tutor.experienceYears(), tutor.ratingAvg(), tutor.reviewCount(),
        "VERIFIED".equals(tutor.status()),
        profile.offerings().stream().map(OfferingResponse::from).toList(),
        profile.availabilitySlots()
    );
  }
}

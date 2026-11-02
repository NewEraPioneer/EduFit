package vn.edufit.profile.web.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import vn.edufit.profile.api.dto.CatalogRef;
import vn.edufit.profile.api.dto.TutorOfferingDto;

public record TutorProfileResponse(
    UUID tutorId,
    UUID userId,
    String displayName,
    String headline,
    String bio,
    String teachingMode,
    String tutorType,
    boolean tutorTypeVerified,
    Integer provinceId,
    String provinceName,
    List<CatalogRef> districts,
    Short experienceYears,
    String teachingMethod,
    String status,
    Instant verifiedAt,
    BigDecimal ratingAvg,
    Integer reviewCount,
    List<TutorOfferingDto> offerings,
    List<TutorSlotResponse> availabilitySlots,
    Instant createdAt,
    Instant updatedAt
) {

  public record TutorSlotResponse(
      UUID slotId,
      Short dayOfWeek,
      LocalTime startTime,
      LocalTime endTime
  ) {}
}

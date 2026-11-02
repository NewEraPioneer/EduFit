package vn.edufit.profile.api.dto;

import java.time.Instant;
import java.util.List;

/** Hồ sơ gia sư đầy đủ cho tìm kiếm và ghép đôi: gói dạy, khu vực dạy trực tiếp và lịch rảnh. */
public record TutorDiscoveryProfileDto(
    TutorSummaryDto tutor,
    List<TutorOfferingDto> offerings,
    List<CatalogRef> districts,
    List<WeeklyAvailabilityDto> availabilitySlots,
    Instant registeredAt
) {
  public TutorDiscoveryProfileDto {
    offerings = offerings == null ? List.of() : List.copyOf(offerings);
    districts = districts == null ? List.of() : List.copyOf(districts);
    availabilitySlots = availabilitySlots == null ? List.of() : List.copyOf(availabilitySlots);
  }
}

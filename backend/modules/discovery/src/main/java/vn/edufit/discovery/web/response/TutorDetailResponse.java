package vn.edufit.discovery.web.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.edufit.profile.api.dto.CatalogRef;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.WeeklyAvailabilityDto;
import vn.edufit.scheduling.api.dto.TutorBusySlotView;

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
    List<WeeklyAvailabilityDto> availabilitySlots,
    List<BusySlot> busySlots
) {

  /** Khung giờ gia sư đã có lịch dạy chính thức; không kèm thông tin học sinh hay lớp học. */
  public record BusySlot(Instant startAt, Instant endAt) {
    public static BusySlot from(TutorBusySlotView view) {
      return new BusySlot(view.startAt(), view.endAt());
    }
  }

  public static TutorDetailResponse from(TutorDiscoveryProfileDto profile, List<TutorBusySlotView> busySlots) {
    var tutor = profile.tutor();
    return new TutorDetailResponse(
        tutor.tutorId(), tutor.displayName(), tutor.headline(), tutor.bio(), tutor.teachingMethod(),
        tutor.teachingMode(), tutor.tutorType(), tutor.tutorTypeVerified(), tutor.provinceName(),
        profile.districts(), tutor.experienceYears(), tutor.ratingAvg(), tutor.reviewCount(),
        "VERIFIED".equals(tutor.status()),
        profile.offerings().stream().map(OfferingResponse::from).toList(),
        profile.availabilitySlots(),
        busySlots.stream().map(BusySlot::from).toList()
    );
  }
}

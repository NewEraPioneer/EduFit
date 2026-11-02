package vn.edufit.discovery.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import vn.edufit.discovery.application.dto.DiscoveredTutor;
import vn.edufit.profile.api.dto.CatalogRef;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.TutorOfferingDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;

/** Thẻ gia sư ở Discovery/AI Match; {@code fit} chỉ có khi tìm kèm nhu cầu học. */
public record TutorDiscoveryCardResponse(
    UUID tutorId,
    String displayName,
    String headline,
    String shortBio,
    String teachingMode,
    String tutorType,
    boolean tutorTypeVerified,
    String provinceName,
    List<CatalogRef> districts,
    Short experienceYears,
    BigDecimal ratingAvg,
    Integer reviewCount,
    boolean verified,
    Long minPricePerSession,
    List<OfferingResponse> offerings,
    @JsonInclude(JsonInclude.Include.NON_NULL) FitResponse fit
) {

  public static TutorDiscoveryCardResponse from(DiscoveredTutor tutor) {
    return from(tutor.profile(), FitResponse.from(tutor.fit()));
  }

  public static TutorDiscoveryCardResponse from(TutorDiscoveryProfileDto profile, FitResponse fit) {
    TutorSummaryDto tutor = profile.tutor();
    return new TutorDiscoveryCardResponse(
        tutor.tutorId(),
        tutor.displayName(),
        tutor.headline(),
        shorten(tutor.bio()),
        tutor.teachingMode(),
        tutor.tutorType(),
        tutor.tutorTypeVerified(),
        tutor.provinceName(),
        profile.districts(),
        tutor.experienceYears(),
        tutor.ratingAvg(),
        tutor.reviewCount(),
        "VERIFIED".equals(tutor.status()),
        profile.offerings().stream().map(TutorOfferingDto::pricePerSession).min(Long::compare).orElse(null),
        profile.offerings().stream().map(OfferingResponse::from).toList(),
        fit
    );
  }

  private static String shorten(String value) {
    if (value == null || value.length() <= 180) {
      return value;
    }
    return value.substring(0, 177) + "...";
  }
}

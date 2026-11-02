package vn.edufit.discovery.web.response;

import java.util.List;
import java.util.UUID;
import vn.edufit.profile.api.dto.CatalogRef;
import vn.edufit.profile.api.dto.OfferingGoalDto;
import vn.edufit.profile.api.dto.TutorOfferingDto;

/** Gói dạy hiển thị công khai trên thẻ và trang chi tiết gia sư. */
public record OfferingResponse(
    UUID offeringId,
    Integer subjectId,
    String subjectName,
    Integer trackId,
    String trackName,
    long pricePerSession,
    List<CatalogRef> levels,
    List<OfferingGoalDto> goals,
    List<CatalogRef> textbooks,
    List<CatalogRef> examProvinces
) {
  public static OfferingResponse from(TutorOfferingDto offering) {
    return new OfferingResponse(offering.offeringId(), offering.subjectId(), offering.subjectName(),
        offering.trackId(), offering.trackName(), offering.pricePerSession(), offering.levels(),
        offering.goals(), offering.textbooks(), offering.examProvinces());
  }
}

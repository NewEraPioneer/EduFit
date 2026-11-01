package vn.edufit.profile.api.dto;

import java.util.List;
import java.util.UUID;

/** Gói dạy của gia sư kèm tên hiển thị đã tra từ danh mục. */
public record TutorOfferingDto(
    UUID offeringId,
    Integer subjectId,
    String subjectName,
    Integer trackId,
    String trackName,
    long pricePerSession,
    List<CatalogRef> levels,
    List<OfferingGoalDto> goals,
    List<CatalogRef> textbooks,
    List<CatalogRef> examProvinces) {

  public TutorOfferingDto {
    levels = List.copyOf(levels);
    goals = List.copyOf(goals);
    textbooks = List.copyOf(textbooks);
    examProvinces = List.copyOf(examProvinces);
  }

  public boolean teachesLevel(Integer levelId) {
    return levels.stream().anyMatch(level -> level.id().equals(levelId));
  }
}

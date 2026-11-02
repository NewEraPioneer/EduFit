package vn.edufit.connection.web.response;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import vn.edufit.connection.application.dto.TutorInboxResult;
import vn.edufit.profile.api.dto.LearningNeedView;

/** Hộp thư gia sư: nhu cầu học (chỉ thông tin hiển thị), lời nhắn và nhãn so khớp ✔/⚠. */
public record TutorInboxResponse(
    UUID requestId,
    List<String> desiredSlots,
    String message,
    String senderRole,
    String status,
    Instant expiresAt,
    String studentDisplayName,
    NeedSummary need,
    String offeringName,
    List<FitItem> fitChecks,
    boolean hasSlotConflict,
    List<String> conflictingSlots
) {

  public record NeedSummary(
      String educationLevelName,
      String subjectName,
      String trackName,
      String goalTagName,
      String textbookSeriesName,
      String examProvinceName,
      String targetSchoolName,
      String mode,
      String provinceName,
      String districtName,
      Long budgetMaxPerSession,
      List<Slot> availabilitySlots
  ) {
    static NeedSummary of(LearningNeedView need) {
      return new NeedSummary(need.educationLevelName(), need.subjectName(), need.trackName(),
          need.goalTagName(), need.textbookSeriesName(), need.examProvinceName(), need.targetSchoolName(),
          need.mode(), need.provinceName(), need.districtName(), need.budgetMaxPerSession(),
          need.availabilitySlots().stream()
              .map(slot -> new Slot(slot.dayOfWeek(), slot.startTime(), slot.endTime()))
              .toList());
    }
  }

  public record Slot(Short dayOfWeek, LocalTime startTime, LocalTime endTime) {}

  public record FitItem(String criterion, boolean matched, String label) {}

  public static TutorInboxResponse of(TutorInboxResult result) {
    return new TutorInboxResponse(
        result.requestId(),
        result.desiredSlots(),
        result.message(),
        result.senderRole(),
        result.status(),
        result.expiresAt(),
        result.studentDisplayName(),
        NeedSummary.of(result.need()),
        result.offeringName(),
        result.fitChecks().stream()
            .map(check -> new FitItem(check.criterion().name(), check.matched(), check.label()))
            .toList(),
        result.hasSlotConflict(),
        result.conflictingSlots()
    );
  }
}

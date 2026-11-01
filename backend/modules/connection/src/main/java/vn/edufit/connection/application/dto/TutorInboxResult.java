package vn.edufit.connection.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.fit.FitCheck;

/**
 * Yêu cầu kết nối trong hộp thư gia sư: đầy đủ nhu cầu học, lời nhắn, và so khớp từng tiêu chí
 * với gói dạy của gia sư (✔/⚠) để gia sư tự quyết định nhận hay từ chối.
 *
 * @param offeringName tên gói dạy dùng để so (môn + nhánh), null khi gia sư không còn dạy môn này
 */
public record TutorInboxResult(
    UUID requestId,
    List<String> desiredSlots,
    String message,
    String senderRole,
    String status,
    Instant expiresAt,
    String studentDisplayName,
    LearningNeedView need,
    String offeringName,
    List<FitCheck> fitChecks,
    boolean hasSlotConflict,
    List<String> conflictingSlots
) {
  public TutorInboxResult {
    desiredSlots = desiredSlots == null ? List.of() : List.copyOf(desiredSlots);
    fitChecks = List.copyOf(fitChecks);
    conflictingSlots = List.copyOf(conflictingSlots);
  }
}

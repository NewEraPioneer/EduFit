package vn.edufit.scheduling.web.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import vn.edufit.scheduling.domain.model.RecurringRescheduleProposal;

/**
 * Response DTO trả về thông tin chi tiết của đề xuất đổi lịch học định kỳ (UC4.8).
 */
public record RecurringProposalResponse(
    UUID proposalId,
    UUID classId,
    UUID tutorId,
    UUID studentId,
    UUID proposedBy,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    List<String> newWeeklySlots,
    String defaultMode,
    String placeOrLink,
    String message,
    String status,
    UUID respondedBy,
    Instant respondedAt,
    String responseReason,
    Instant expiresAt,
    Instant createdAt
) {

  public static RecurringProposalResponse from(RecurringRescheduleProposal p) {
    if (p == null) {
      return null;
    }
    return new RecurringProposalResponse(
        p.getProposalId(),
        p.getClassId(),
        p.getTutorId(),
        p.getStudentId(),
        p.getProposedBy(),
        p.getEffectiveFrom(),
        p.getEffectiveTo(),
        p.getNewWeeklySlots(),
        p.getDefaultMode() != null ? p.getDefaultMode().name() : null,
        p.getPlaceOrLink(),
        p.getMessage(),
        p.getStatus() != null ? p.getStatus().name() : null,
        p.getRespondedBy(),
        p.getRespondedAt(),
        p.getResponseReason(),
        p.getExpiresAt(),
        p.getCreatedAt()
    );
  }
}

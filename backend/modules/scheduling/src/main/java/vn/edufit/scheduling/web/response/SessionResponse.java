package vn.edufit.scheduling.web.response;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.scheduling.domain.model.TutoringSession;

/**
 * Response DTO trả về thông tin chi tiết đầy đủ của buổi học cho REST API.
 */
public record SessionResponse(
    UUID sessionId,
    UUID classId,
    UUID tutorId,
    UUID studentId,
    Instant startAt,
    Instant endAt,
    String mode,
    String placeOrLink,
    String repeatNote,
    String message,
    String status,
    UUID proposedBy,
    UUID respondedBy,
    Instant respondedAt,
    String responseReason,
    Instant expiresAt,
    UUID cancelledBy,
    Instant cancelledAt,
    String cancelReason,
    String cancelComment,
    boolean isLateCancel,
    UUID outcomeRecordedBy,
    Instant outcomeRecordedAt,
    int version,
    Instant createdAt
) {

  public static SessionResponse from(TutoringSession s) {
    if (s == null) {
      return null;
    }
    return new SessionResponse(
        s.getSessionId(),
        s.getClassId(),
        s.getTutorId(),
        s.getStudentId(),
        s.getStartAt(),
        s.getEndAt(),
        s.getMode() != null ? s.getMode().name() : null,
        s.getPlaceOrLink(),
        s.getRepeatNote(),
        s.getMessage(),
        s.getStatus() != null ? s.getStatus().name() : null,
        s.getProposedBy(),
        s.getRespondedBy(),
        s.getRespondedAt(),
        s.getResponseReason(),
        s.getExpiresAt(),
        s.getCancelledBy(),
        s.getCancelledAt(),
        s.getCancelReason() != null ? s.getCancelReason().name() : null,
        s.getCancelComment(),
        s.isLateCancel(),
        s.getOutcomeRecordedBy(),
        s.getOutcomeRecordedAt(),
        s.getVersion(),
        s.getCreatedAt()
    );
  }
}

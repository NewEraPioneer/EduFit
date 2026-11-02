package vn.edufit.scheduling.web.response;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.scheduling.domain.model.SessionRescheduleProposal;

/**
 * Response DTO trả về thông tin chi tiết của đề xuất đổi lịch học.
 */
public record RescheduleProposalResponse(
    UUID proposalId,
    UUID sessionId,
    UUID proposedBy,
    Instant newStartAt,
    Instant newEndAt,
    String reason,
    String status,
    Instant expiresAt,
    UUID respondedBy,
    Instant respondedAt,
    String responseReason,
    Instant createdAt
) {

  public static RescheduleProposalResponse from(SessionRescheduleProposal p) {
    if (p == null) {
      return null;
    }
    return new RescheduleProposalResponse(
        p.getProposalId(),
        p.getSessionId(),
        p.getProposedBy(),
        p.getNewStartAt(),
        p.getNewEndAt(),
        p.getReason(),
        p.getStatus() != null ? p.getStatus().name() : null,
        p.getExpiresAt(),
        p.getRespondedBy(),
        p.getRespondedAt(),
        p.getResponseReason(),
        p.getCreatedAt()
    );
  }
}

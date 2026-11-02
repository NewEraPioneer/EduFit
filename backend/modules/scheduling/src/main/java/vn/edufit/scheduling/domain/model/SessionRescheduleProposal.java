package vn.edufit.scheduling.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Entity POJO đại diện cho một Đề xuất đổi thời gian buổi học (Reschedule Proposal).
 *
 * <p>Theo BR-44: Mỗi buổi học tại một thời điểm chỉ cho phép tối đa 1 đề xuất ở trạng thái {@link ProposalStatus#PENDING}.
 */
public class SessionRescheduleProposal {

  private final UUID proposalId;
  private final UUID sessionId;
  private final UUID proposedBy;
  private final Instant newStartAt;
  private final Instant newEndAt;
  private final String reason;
  private ProposalStatus status;
  private final Instant expiresAt;
  private UUID respondedBy;
  private Instant respondedAt;
  private String responseReason;
  private final Instant createdAt;

  public SessionRescheduleProposal(
      UUID proposalId,
      UUID sessionId,
      UUID proposedBy,
      Instant newStartAt,
      Instant newEndAt,
      String reason,
      ProposalStatus status,
      Instant expiresAt,
      UUID respondedBy,
      Instant respondedAt,
      String responseReason,
      Instant createdAt
  ) {
    this.proposalId = Objects.requireNonNull(proposalId, "proposalId không được null");
    this.sessionId = Objects.requireNonNull(sessionId, "sessionId không được null");
    this.proposedBy = Objects.requireNonNull(proposedBy, "proposedBy không được null");
    this.newStartAt = Objects.requireNonNull(newStartAt, "newStartAt không được null");
    this.newEndAt = Objects.requireNonNull(newEndAt, "newEndAt không được null");
    this.reason = reason;
    this.status = Objects.requireNonNull(status, "status không được null");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt không được null");
    this.respondedBy = respondedBy;
    this.respondedAt = respondedAt;
    this.responseReason = responseReason;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt không được null");
  }

  public static SessionRescheduleProposal create(
      UUID proposalId,
      UUID sessionId,
      UUID proposedBy,
      Instant newStartAt,
      Instant newEndAt,
      String reason,
      Instant expiresAt,
      Instant now
  ) {
    return new SessionRescheduleProposal(
        proposalId != null ? proposalId : UUID.randomUUID(),
        sessionId,
        proposedBy,
        newStartAt,
        newEndAt,
        reason,
        ProposalStatus.PENDING,
        expiresAt,
        null,
        null,
        null,
        now
    );
  }

  public void accept(UUID responderId, Instant now) {
    if (this.status != ProposalStatus.PENDING) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Chỉ có thể chấp thuận đề xuất đổi lịch đang ở trạng thái PENDING"
      );
    }
    this.status = ProposalStatus.ACCEPTED;
    this.respondedBy = Objects.requireNonNull(responderId, "responderId không được null");
    this.respondedAt = Objects.requireNonNull(now, "now không được null");
  }

  public void reject(UUID responderId, String reason, Instant now) {
    if (this.status != ProposalStatus.PENDING) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Chỉ có thể từ chối đề xuất đổi lịch đang ở trạng thái PENDING"
      );
    }
    this.status = ProposalStatus.REJECTED;
    this.respondedBy = Objects.requireNonNull(responderId, "responderId không được null");
    this.respondedAt = Objects.requireNonNull(now, "now không được null");
    this.responseReason = reason;
  }

  public void withdraw(UUID actorId, Instant now) {
    if (!this.proposedBy.equals(actorId)) {
      throw new InvalidOperationException(ErrorCode.FORBIDDEN, "Chỉ người tạo đề xuất mới có quyền rút lại");
    }
    if (this.status != ProposalStatus.PENDING) {
      throw new InvalidOperationException(ErrorCode.INVALID_OPERATION, "Chỉ có thể rút lại đề xuất PENDING");
    }
    this.status = ProposalStatus.WITHDRAWN;
    this.respondedBy = actorId;
    this.respondedAt = now;
    this.responseReason = "Người đề xuất tự rút lại yêu cầu";
  }

  public void expire(Instant now) {
    if (this.status != ProposalStatus.PENDING) {
      throw new InvalidOperationException(ErrorCode.INVALID_OPERATION, "Chỉ đề xuất PENDING mới có thể hết hạn");
    }
    this.status = ProposalStatus.EXPIRED;
    this.respondedAt = now;
    this.responseReason = "Tự động hết hạn";
  }

  // ==================== GETTERS ====================
  public UUID getProposalId() { return proposalId; }
  public UUID getSessionId() { return sessionId; }
  public UUID getProposedBy() { return proposedBy; }
  public Instant getNewStartAt() { return newStartAt; }
  public Instant getNewEndAt() { return newEndAt; }
  public String getReason() { return reason; }
  public ProposalStatus getStatus() { return status; }
  public Instant getExpiresAt() { return expiresAt; }
  public UUID getRespondedBy() { return respondedBy; }
  public Instant getRespondedAt() { return respondedAt; }
  public String getResponseReason() { return responseReason; }
  public Instant getCreatedAt() { return createdAt; }
}

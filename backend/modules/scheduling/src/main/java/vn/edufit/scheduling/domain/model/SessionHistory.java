package vn.edufit.scheduling.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity POJO lưu vết lịch sử biến động trạng thái của buổi học (Audit Trail).
 *
 * <p>Khớp với bảng {@code session_history} trong DB migration V1.05.
 */
public class SessionHistory {

  private final UUID historyId;
  private final UUID sessionId;
  private final UUID rescheduleProposalId;
  private final String action;
  private final UUID actorUserId;
  private final String reason;
  private final Instant createdAt;

  public SessionHistory(
      UUID historyId,
      UUID sessionId,
      UUID rescheduleProposalId,
      String action,
      UUID actorUserId,
      String reason,
      Instant createdAt
  ) {
    this.historyId = Objects.requireNonNull(historyId, "historyId không được null");
    this.sessionId = Objects.requireNonNull(sessionId, "sessionId không được null");
    this.rescheduleProposalId = rescheduleProposalId;
    this.action = Objects.requireNonNull(action, "action không được null");
    this.actorUserId = actorUserId;
    this.reason = reason;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt không được null");
  }

  public static SessionHistory create(
      UUID sessionId,
      UUID rescheduleProposalId,
      String action,
      UUID actorUserId,
      String reason,
      Instant now
  ) {
    return new SessionHistory(
        UUID.randomUUID(),
        sessionId,
        rescheduleProposalId,
        action,
        actorUserId,
        reason,
        now
    );
  }

  public UUID getHistoryId() { return historyId; }
  public UUID getSessionId() { return sessionId; }
  public UUID getRescheduleProposalId() { return rescheduleProposalId; }
  public String getAction() { return action; }
  public UUID getActorUserId() { return actorUserId; }
  public String getReason() { return reason; }
  public Instant getCreatedAt() { return createdAt; }
}

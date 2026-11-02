package vn.edufit.progress.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity miền ghi lại lịch sử các thay đổi đối với Learning Plan và Milestone (Audit Trail).
 */
public class PlanHistory {

  private final UUID historyId;
  private final UUID planId;
  private final UUID milestoneId;
  private final String action;
  private final UUID actorUserId;
  private final String oldValue;
  private final String newValue;
  private final Instant createdAt;

  public PlanHistory(
      UUID historyId,
      UUID planId,
      UUID milestoneId,
      String action,
      UUID actorUserId,
      String oldValue,
      String newValue,
      Instant createdAt
  ) {
    this.historyId = historyId != null ? historyId : UUID.randomUUID();
    this.planId = planId;
    this.milestoneId = milestoneId;
    this.action = action;
    this.actorUserId = actorUserId;
    this.oldValue = truncate(oldValue, 500);
    this.newValue = truncate(newValue, 500);
    this.createdAt = createdAt != null ? createdAt : Instant.now();
  }

  public static PlanHistory of(
      UUID planId,
      UUID milestoneId,
      String action,
      UUID actorUserId,
      String oldValue,
      String newValue
  ) {
    return of(planId, milestoneId, action, actorUserId, oldValue, newValue, Instant.now());
  }

  public static PlanHistory of(
      UUID planId,
      UUID milestoneId,
      String action,
      UUID actorUserId,
      String oldValue,
      String newValue,
      Instant now
  ) {
    return new PlanHistory(
        UUID.randomUUID(),
        planId,
        milestoneId,
        action,
        actorUserId,
        oldValue,
        newValue,
        now != null ? now : Instant.now()
    );
  }

  private static String truncate(String val, int maxLen) {
    if (val == null) return null;
    return val.length() <= maxLen ? val : val.substring(0, maxLen);
  }

  public UUID getHistoryId() { return historyId; }
  public UUID getPlanId() { return planId; }
  public UUID getMilestoneId() { return milestoneId; }
  public String getAction() { return action; }
  public UUID getActorUserId() { return actorUserId; }
  public String getOldValue() { return oldValue; }
  public String getNewValue() { return newValue; }
  public Instant getCreatedAt() { return createdAt; }
}

package vn.edufit.progress.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA Entity ánh xạ bảng {@code plan_history}.
 */
@Entity
@Table(name = "plan_history")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PlanHistoryJpaEntity {

  @Id
  @Column(name = "history_id", nullable = false, updatable = false)
  private UUID historyId;

  @Column(name = "plan_id", nullable = false)
  private UUID planId;

  @Column(name = "milestone_id")
  private UUID milestoneId;

  @Column(name = "action", nullable = false, length = 40)
  private String action;

  @Column(name = "actor_user_id", nullable = false)
  private UUID actorUserId;

  @Column(name = "old_value", length = 500)
  private String oldValue;

  @Column(name = "new_value", length = 500)
  private String newValue;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}

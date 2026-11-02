package vn.edufit.scheduling.infra.persistence.entity;

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
 * JPA Entity ánh xạ bảng cơ sở dữ liệu {@code session_history} (Audit Trail).
 */
@Entity
@Table(name = "session_history")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SessionHistoryJpaEntity {

  @Id
  @Column(name = "history_id", nullable = false, updatable = false)
  private UUID historyId;

  @Column(name = "session_id", nullable = false)
  private UUID sessionId;

  @Column(name = "reschedule_proposal_id")
  private UUID rescheduleProposalId;

  @Column(name = "action", nullable = false, length = 40)
  private String action;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(name = "reason", length = 300)
  private String reason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}

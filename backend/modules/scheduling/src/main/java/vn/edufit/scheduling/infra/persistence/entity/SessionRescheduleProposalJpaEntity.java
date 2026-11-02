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
 * JPA Entity ánh xạ bảng cơ sở dữ liệu {@code session_reschedule_proposal} (BR-44).
 */
@Entity
@Table(name = "session_reschedule_proposal")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SessionRescheduleProposalJpaEntity {

  @Id
  @Column(name = "proposal_id", nullable = false, updatable = false)
  private UUID proposalId;

  @Column(name = "session_id", nullable = false)
  private UUID sessionId;

  @Column(name = "proposed_by", nullable = false)
  private UUID proposedBy;

  @Column(name = "new_start_at", nullable = false)
  private Instant newStartAt;

  @Column(name = "new_end_at", nullable = false)
  private Instant newEndAt;

  @Column(name = "reason", length = 300)
  private String reason;

  @Column(name = "status", nullable = false, length = 30)
  private String status;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "responded_by")
  private UUID respondedBy;

  @Column(name = "responded_at")
  private Instant respondedAt;

  @Column(name = "response_reason", length = 300)
  private String responseReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}

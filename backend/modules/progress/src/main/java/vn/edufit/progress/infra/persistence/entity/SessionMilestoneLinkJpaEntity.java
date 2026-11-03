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

/**
 * JPA Entity ánh xạ bảng {@code session_milestone_link} (PLANNED: dự kiến khi xếp lịch, ACTUAL: thực tế khi phản hồi).
 */
@Entity
@Table(name = "session_milestone_link")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SessionMilestoneLinkJpaEntity {

  @Id
  @Column(name = "link_id", nullable = false, updatable = false)
  private UUID linkId;

  @Column(name = "session_id", nullable = false, updatable = false)
  private UUID sessionId;

  @Column(name = "milestone_id", nullable = false, updatable = false)
  private UUID milestoneId;

  @Column(name = "link_type", nullable = false, updatable = false, length = 20)
  private String linkType;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}

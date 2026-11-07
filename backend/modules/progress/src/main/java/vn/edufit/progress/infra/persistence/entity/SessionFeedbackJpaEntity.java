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
 * JPA Entity ánh xạ bảng {@code session_feedback}. {@code attitudeChips} lưu tên enum ngăn cách bởi dấu phẩy.
 */
@Entity
@Table(name = "session_feedback")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SessionFeedbackJpaEntity {

  @Id
  @Column(name = "feedback_id", nullable = false, updatable = false)
  private UUID feedbackId;

  @Column(name = "session_id", nullable = false, updatable = false, unique = true)
  private UUID sessionId;

  @Column(name = "class_id", nullable = false, updatable = false)
  private UUID classId;

  @Column(name = "homework_status", nullable = false, length = 30)
  private String homeworkStatus;

  @Column(name = "attitude_chips", length = 300)
  private String attitudeChips;

  @Column(name = "parent_message", length = 300)
  private String parentMessage;

  @Column(name = "submitted_late", nullable = false, updatable = false)
  private boolean submittedLate;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}

package vn.edufit.scheduling.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA Entity ánh xạ bảng cơ sở dữ liệu {@code tutoring_session} (PostgreSQL 17).
 *
 * <p>Đặc điểm kiến trúc & Ràng buộc toàn vẹn:
 * <ul>
 *   <li><b>Khóa lạc quan (Optimistic Locking):</b> Trường {@link #version} có annotation {@link Version}
 *       để ngăn chặn tình trạng Lost Update khi nhiều người dùng cùng chỉnh sửa bản ghi (NFR-17).</li>
 *   <li><b>Khóa bi quan & Ràng buộc loại trừ:</b> Bảng này được cấu hình PostgreSQL GiST Exclusion Constraint
 *       {@code ex_tutoring_session_no_tutor_overlap} và {@code ex_tutoring_session_no_student_overlap}
 *       trên dải thời gian {@code tstzrange(start_at, end_at, '[)')} khi ở trạng thái {@code SCHEDULED}.</li>
 * </ul>
 */
@Entity
@Table(name = "tutoring_session")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TutoringSessionJpaEntity {

  @Id
  @Column(name = "session_id", nullable = false, updatable = false)
  private UUID sessionId;

  @Column(name = "class_id", nullable = false)
  private UUID classId;

  @Column(name = "tutor_id", nullable = false)
  private UUID tutorId;

  @Column(name = "student_id", nullable = false)
  private UUID studentId;

  @Column(name = "start_at", nullable = false)
  private Instant startAt;

  @Column(name = "end_at", nullable = false)
  private Instant endAt;

  @Column(name = "mode", nullable = false, length = 20)
  private String mode;

  @Column(name = "place_or_link", length = 500)
  private String placeOrLink;

  @Column(name = "repeat_note", length = 300)
  private String repeatNote;

  @Column(name = "message", length = 500)
  private String message;

  @Column(name = "status", nullable = false, length = 30)
  private String status;

  @Column(name = "proposed_by", nullable = false)
  private UUID proposedBy;

  @Column(name = "responded_by")
  private UUID respondedBy;

  @Column(name = "responded_at")
  private Instant respondedAt;

  @Column(name = "response_reason", length = 300)
  private String responseReason;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "cancelled_by")
  private UUID cancelledBy;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancel_reason", length = 40)
  private String cancelReason;

  @Column(name = "cancel_comment", length = 300)
  private String cancelComment;

  @Column(name = "is_late_cancel", nullable = false)
  private boolean lateCancel;

  @Column(name = "outcome_recorded_by")
  private UUID outcomeRecordedBy;

  @Column(name = "outcome_recorded_at")
  private Instant outcomeRecordedAt;

  @Version
  @Column(name = "version", nullable = false)
  private int version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}

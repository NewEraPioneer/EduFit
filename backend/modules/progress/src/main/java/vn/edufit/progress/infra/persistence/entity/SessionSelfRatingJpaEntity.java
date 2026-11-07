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

/** JPA Entity ánh xạ bảng {@code session_self_rating} — cảm nhận 1 chạm của học sinh (1–3). */
@Entity
@Table(name = "session_self_rating")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SessionSelfRatingJpaEntity {

  @Id
  @Column(name = "rating_id", nullable = false, updatable = false)
  private UUID ratingId;

  @Column(name = "session_id", nullable = false, updatable = false, unique = true)
  private UUID sessionId;

  @Column(name = "class_id", nullable = false, updatable = false)
  private UUID classId;

  @Column(name = "student_id", nullable = false, updatable = false)
  private UUID studentId;

  @Column(name = "rating", nullable = false)
  private short rating;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}

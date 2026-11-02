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
 * JPA Entity ánh xạ bảng {@code session_note} (Bất biến - Immutable).
 */
@Entity
@Table(name = "session_note")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SessionNoteJpaEntity {

  @Id
  @Column(name = "note_id", nullable = false, updatable = false)
  private UUID noteId;

  @Column(name = "session_id", nullable = false, unique = true)
  private UUID sessionId;

  @Column(name = "tutor_id", nullable = false)
  private UUID tutorId;

  @Column(name = "note_text")
  private String noteText;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}

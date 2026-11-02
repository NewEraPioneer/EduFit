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
import org.hibernate.annotations.Immutable;

/** JPA Entity ánh xạ bảng {@code feedback_reminder} — mỗi buổi học nhận tối đa một lần nhắc cho mỗi mốc. */
@Entity
@Immutable
@Table(name = "feedback_reminder")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FeedbackReminderJpaEntity {

  @Id
  @Column(name = "reminder_id", nullable = false, updatable = false)
  private UUID reminderId;

  @Column(name = "session_id", nullable = false, updatable = false)
  private UUID sessionId;

  @Column(name = "stage", nullable = false, updatable = false, length = 10)
  private String stage;

  @Column(name = "sent_at", nullable = false, updatable = false)
  private Instant sentAt;
}

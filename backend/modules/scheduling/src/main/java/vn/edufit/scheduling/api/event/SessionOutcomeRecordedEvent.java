package vn.edufit.scheduling.api.event;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/**
 * Sự kiện miền phát sinh khi Gia sư hoàn thành việc ghi nhận kết quả buổi học (COMPLETED hoặc ABSENT).
 *
 * <p>Module lắng nghe:
 * <ul>
 *   <li>{@code progress}: Cập nhật trạng thái các Cột mốc (Milestones) và tính toán lại tiến độ bình quân.</li>
 *   <li>{@code notification}: Gửi thông báo tóm tắt buổi học và biên bản tới Phụ huynh & Học sinh.</li>
 * </ul>
 */
public record SessionOutcomeRecordedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID sessionId,
    UUID classId,
    UUID tutorId,
    UUID studentId,
    String outcome,
    String noteText,
    Instant recordedAt
) implements BaseDomainEvent {

  public static SessionOutcomeRecordedEvent of(
      UUID sessionId,
      UUID classId,
      UUID tutorId,
      UUID studentId,
      String outcome,
      String noteText,
      Instant recordedAt
  ) {
    return new SessionOutcomeRecordedEvent(
        UUID.randomUUID(),
        Instant.now(),
        sessionId,
        classId,
        tutorId,
        studentId,
        outcome,
        noteText,
        recordedAt
    );
  }

  @Override
  public UUID getEventId() { return eventId; }

  @Override
  public Instant getOccurredAt() { return occurredAt; }
}

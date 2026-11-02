package vn.edufit.connection.api.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sự kiện miền phát ra khi một Lớp học kết thúc (status = 'ENDED').
 * Phân hệ Scheduling sẽ lắng nghe sự kiện này để tự động cascade hủy các buổi học tương lai.
 */
public record TutoringClassEndedEvent(
    UUID classId,
    UUID studentId,
    UUID tutorId,
    UUID endedBy,
    String reason,
    Instant endedAt
) {
  public TutoringClassEndedEvent {
    Objects.requireNonNull(classId, "classId");
    Objects.requireNonNull(studentId, "studentId");
    Objects.requireNonNull(tutorId, "tutorId");
    Objects.requireNonNull(endedBy, "endedBy");
    Objects.requireNonNull(endedAt, "endedAt");
  }
}

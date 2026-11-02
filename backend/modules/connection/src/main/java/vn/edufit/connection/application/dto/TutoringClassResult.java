package vn.edufit.connection.application.dto;

import java.time.Instant;
import java.util.UUID;

public record TutoringClassResult(
    UUID classId,
    UUID studentId,
    UUID tutorId,
    UUID needId,
    String status,
    String studentDisplayName,
    String tutorDisplayName,
    String subjectName,
    String educationLevelName,
    String scheduleStatus,
    int sessionCount,
    int completedCount,
    int scheduledCount,
    int totalCount,
    Instant startedAt,
    Instant endedAt
) {
  public TutoringClassResult(
      UUID classId,
      UUID studentId,
      UUID tutorId,
      UUID needId,
      String status,
      String studentDisplayName,
      String tutorDisplayName,
      String subjectName,
      String educationLevelName,
      String scheduleStatus,
      int sessionCount,
      Instant startedAt,
      Instant endedAt
  ) {
    this(classId, studentId, tutorId, needId, status, studentDisplayName, tutorDisplayName,
        subjectName, educationLevelName, scheduleStatus, sessionCount, sessionCount, 0, sessionCount, startedAt, endedAt);
  }
}

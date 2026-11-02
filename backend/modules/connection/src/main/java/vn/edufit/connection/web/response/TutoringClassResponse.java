package vn.edufit.connection.web.response;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import vn.edufit.connection.application.dto.TutoringClassResult;

public record TutoringClassResponse(
    UUID classId,
    UUID studentId,
    UUID tutorId,
    UUID needId,
    String status,
    @JsonInclude(JsonInclude.Include.NON_NULL) String studentDisplayName,
    @JsonInclude(JsonInclude.Include.NON_NULL) String tutorDisplayName,
    @JsonInclude(JsonInclude.Include.NON_NULL) String subjectName,
    @JsonInclude(JsonInclude.Include.NON_NULL) String educationLevelName,
    String scheduleStatus,
    int sessionCount,
    int completedCount,
    int scheduledCount,
    int totalCount,
    Instant startedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) Instant endedAt
) {
  public TutoringClassResponse(
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

  public static TutoringClassResponse of(TutoringClassResult r) {
    return new TutoringClassResponse(
        r.classId(),
        r.studentId(),
        r.tutorId(),
        r.needId(),
        r.status(),
        r.studentDisplayName(),
        r.tutorDisplayName(),
        r.subjectName(),
        r.educationLevelName(),
        r.scheduleStatus(),
        r.sessionCount(),
        r.completedCount(),
        r.scheduledCount(),
        r.totalCount(),
        r.startedAt(),
        r.endedAt()
    );
  }
}

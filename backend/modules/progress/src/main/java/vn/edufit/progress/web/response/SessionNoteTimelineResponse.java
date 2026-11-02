package vn.edufit.progress.web.response;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.progress.application.dto.SessionNoteTimelineView;

public record SessionNoteTimelineResponse(
    UUID sessionId,
    UUID classId,
    Instant startAt,
    Instant endAt,
    String status,
    String noteText,
    Instant recordedAt
) {
  public static SessionNoteTimelineResponse from(SessionNoteTimelineView v) {
    if (v == null) return null;
    return new SessionNoteTimelineResponse(
        v.sessionId(),
        v.classId(),
        v.startAt(),
        v.endAt(),
        v.status(),
        v.noteText(),
        v.recordedAt()
    );
  }
}

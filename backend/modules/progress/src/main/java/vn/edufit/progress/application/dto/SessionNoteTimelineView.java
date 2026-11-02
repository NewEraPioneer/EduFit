package vn.edufit.progress.application.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionNoteTimelineView(
    UUID sessionId,
    UUID classId,
    Instant startAt,
    Instant endAt,
    String status,
    String noteText,
    Instant recordedAt
) {}

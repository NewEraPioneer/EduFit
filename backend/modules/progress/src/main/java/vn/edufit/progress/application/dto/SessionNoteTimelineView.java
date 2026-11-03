package vn.edufit.progress.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng sổ liên lạc: buổi học, nhận xét ngắn của gia sư, phản hồi có cấu trúc (null nếu chưa có)
 * và cảm nhận 1 chạm của học sinh (1–3; null nếu chưa chọn hoặc người xem là gia sư).
 */
public record SessionNoteTimelineView(
    UUID sessionId,
    UUID classId,
    Instant startAt,
    Instant endAt,
    String status,
    String noteText,
    Instant recordedAt,
    SessionFeedbackView feedback,
    Integer selfRating
) {}

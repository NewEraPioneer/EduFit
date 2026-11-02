package vn.edufit.review.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO chứa thông tin đánh giá an toàn dùng trong giao tiếp nội bộ giữa các module (ADR-002).
 */
public record ReviewDto(
    UUID reviewId,
    UUID tutorId,
    UUID studentId,
    UUID reviewerUserId,
    String reviewerType,
    int rating,
    String comment,
    boolean isEdited,
    int completedSessionsAtReview,
    Instant createdAt,
    Instant updatedAt
) {}


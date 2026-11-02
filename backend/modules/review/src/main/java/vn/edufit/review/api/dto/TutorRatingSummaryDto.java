package vn.edufit.review.api.dto;

import java.util.UUID;

/**
 * DTO đại diện cho thông tin tóm tắt điểm đánh giá của gia sư cung cấp qua Facade (ADR-002).
 */
public record TutorRatingSummaryDto(
    UUID tutorId,
    double averageRating,
    int totalReviews
) {}


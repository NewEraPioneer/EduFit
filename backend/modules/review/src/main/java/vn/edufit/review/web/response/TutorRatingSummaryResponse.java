package vn.edufit.review.web.response;

import java.util.Map;
import java.util.UUID;

/**
 * Response payload thống kê điểm đánh giá và phân bổ số sao của gia sư.
 */
public record TutorRatingSummaryResponse(
    UUID tutorId,
    double averageRating,
    int totalReviews,
    Map<Integer, Long> ratingDistribution
) {}


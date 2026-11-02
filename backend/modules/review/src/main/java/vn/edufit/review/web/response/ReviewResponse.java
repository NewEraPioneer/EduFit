package vn.edufit.review.web.response;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.review.infra.persistence.entity.ReviewEntity;

/**
 * Response payload trả về cho giao diện Client thông tin một đánh giá.
 * Bảo mật (BR-59): Ẩn hoàn toàn reviewerUserId, bổ sung cờ ngữ cảnh isAuthor cho người dùng hiện tại.
 */
public record ReviewResponse(
    UUID reviewId,
    UUID tutorId,
    UUID studentId,
    String reviewerName,
    String reviewerType,
    int rating,
    String comment,
    boolean isEdited,
    int completedSessionsAtReview,
    boolean isAuthor,
    Instant createdAt,
    Instant updatedAt
) {

  public static ReviewResponse from(ReviewEntity entity, String reviewerName, boolean isAuthor) {
    return new ReviewResponse(
        entity.getReviewId(),
        entity.getTutorId(),
        entity.getStudentId(),
        reviewerName,
        entity.getReviewerType(),
        entity.getRating(),
        entity.getComment(),
        entity.isEdited(),
        entity.getCompletedSessionsAtReview(),
        isAuthor,
        entity.getCreatedAt(),
        entity.getUpdatedAt()
    );
  }
}

package vn.edufit.progress.domain.model;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Cảm nhận 1 chạm của học sinh cho một buổi học. Mỗi buổi một bản ghi; học sinh chọn lại thì ghi đè.
 */
public class SessionSelfRating {

  private final UUID ratingId;
  private final UUID sessionId;
  private final UUID classId;
  private final UUID studentId;
  private SelfRating rating;
  private final UUID createdBy;
  private final Instant createdAt;
  private Instant updatedAt;

  public SessionSelfRating(
      UUID ratingId,
      UUID sessionId,
      UUID classId,
      UUID studentId,
      SelfRating rating,
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt
  ) {
    if (sessionId == null || classId == null || studentId == null || createdBy == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Cảm nhận thiếu buổi học, lớp học hoặc học sinh");
    }
    requireRating(rating);
    this.ratingId = ratingId != null ? ratingId : UUID.randomUUID();
    this.sessionId = sessionId;
    this.classId = classId;
    this.studentId = studentId;
    this.rating = rating;
    this.createdBy = createdBy;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
    this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
  }

  public void change(SelfRating newRating, Instant now) {
    requireRating(newRating);
    this.rating = newRating;
    this.updatedAt = now;
  }

  private static void requireRating(SelfRating rating) {
    if (rating == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Cần chọn một cảm nhận về buổi học");
    }
  }

  public UUID getRatingId() { return ratingId; }
  public UUID getSessionId() { return sessionId; }
  public UUID getClassId() { return classId; }
  public UUID getStudentId() { return studentId; }
  public SelfRating getRating() { return rating; }
  public UUID getCreatedBy() { return createdBy; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}

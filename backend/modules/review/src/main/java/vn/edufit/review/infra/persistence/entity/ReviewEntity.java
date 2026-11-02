package vn.edufit.review.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity đại diện cho bảng review (Đánh giá gia sư - UC6.1).
 * Tuân thủ ADR-002: Chỉ lưu trữ UUID của các entity thuộc module khác, không dùng @ManyToOne chéo module.
 */
@Entity
@Table(
    name = "review",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_review_tutor_student", columnNames = {"tutor_id", "student_id"})
    }
)
public class ReviewEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "review_id", updatable = false, nullable = false)
  private UUID reviewId;

  @Column(name = "tutor_id", nullable = false)
  private UUID tutorId;

  @Column(name = "student_id", nullable = false)
  private UUID studentId;

  @Column(name = "reviewer_user_id", nullable = false)
  private UUID reviewerUserId;

  @Column(name = "reviewer_type", nullable = false, length = 20)
  private String reviewerType;

  @Column(name = "rating", nullable = false)
  private Short rating;

  @Column(name = "comment", nullable = false, length = 1000)
  private String comment;

  @Column(name = "is_edited", nullable = false)
  private boolean isEdited = false;

  @Column(name = "completed_sessions_at_review", nullable = false)
  private int completedSessionsAtReview;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public ReviewEntity() {}

  public ReviewEntity(
      UUID tutorId,
      UUID studentId,
      UUID reviewerUserId,
      String reviewerType,
      Integer rating,
      String comment,
      int completedSessionsAtReview
  ) {
    this.tutorId = tutorId;
    this.studentId = studentId;
    this.reviewerUserId = reviewerUserId;
    this.reviewerType = reviewerType;
    this.rating = rating != null ? rating.shortValue() : null;
    this.comment = comment;
    this.completedSessionsAtReview = completedSessionsAtReview;
    this.isEdited = false;
  }

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    if (this.createdAt == null) {
      this.createdAt = now;
    }
    if (this.updatedAt == null) {
      this.updatedAt = now;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  // Getters & Setters
  public UUID getReviewId() {
    return reviewId;
  }

  public void setReviewId(UUID reviewId) {
    this.reviewId = reviewId;
  }

  public UUID getTutorId() {
    return tutorId;
  }

  public void setTutorId(UUID tutorId) {
    this.tutorId = tutorId;
  }

  public UUID getStudentId() {
    return studentId;
  }

  public void setStudentId(UUID studentId) {
    this.studentId = studentId;
  }

  public UUID getReviewerUserId() {
    return reviewerUserId;
  }

  public void setReviewerUserId(UUID reviewerUserId) {
    this.reviewerUserId = reviewerUserId;
  }

  public String getReviewerType() {
    return reviewerType;
  }

  public void setReviewerType(String reviewerType) {
    this.reviewerType = reviewerType;
  }

  public Integer getRating() {
    return rating != null ? rating.intValue() : null;
  }

  public void setRating(Integer rating) {
    this.rating = rating != null ? rating.shortValue() : null;
  }

  public void setRating(Short rating) {
    this.rating = rating;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public boolean isEdited() {
    return isEdited;
  }

  public void setEdited(boolean edited) {
    isEdited = edited;
  }

  public int getCompletedSessionsAtReview() {
    return completedSessionsAtReview;
  }

  public void setCompletedSessionsAtReview(int completedSessionsAtReview) {
    this.completedSessionsAtReview = completedSessionsAtReview;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}


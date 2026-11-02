package vn.edufit.verification.infra.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

@Entity
@Table(name = "verification_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationRequest {

  public enum Status {
    PENDING,
    APPROVED,
    REJECTED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "request_id")
  private UUID requestId;

  @Column(name = "tutor_id", nullable = false)
  private UUID tutorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private Status status;

  @Column(name = "rejection_reason")
  private String rejectionReason;

  @Column(name = "submitted_at", nullable = false, updatable = false)
  private Instant submittedAt;

  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @OneToMany(mappedBy = "verificationRequest", cascade = CascadeType.ALL, orphanRemoval = true)
  @org.hibernate.annotations.BatchSize(size = 20)
  private java.util.Set<Credential> credentials = new java.util.LinkedHashSet<>();

  public static VerificationRequest pending(UUID tutorId, Instant submittedAt) {
    VerificationRequest request = new VerificationRequest();
    request.tutorId = tutorId;
    request.status = Status.PENDING;
    request.submittedAt = submittedAt;
    return request;
  }

  public void addCredential(Credential credential) {
    credentials.add(credential);
    credential.attachTo(this);
  }

  public void approve(UUID adminUserId, Instant reviewedAt) {
    ensurePending();
    this.status = Status.APPROVED;
    this.rejectionReason = null;
    this.reviewedBy = adminUserId;
    this.reviewedAt = reviewedAt;
  }

  public void reject(UUID adminUserId, String reason, Instant reviewedAt) {
    ensurePending();
    if (reason == null || reason.isBlank()) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Lý do từ chối hồ sơ xác minh là bắt buộc"
      );
    }
    this.status = Status.REJECTED;
    this.rejectionReason = reason.trim();
    this.reviewedBy = adminUserId;
    this.reviewedAt = reviewedAt;
  }

  public void revoke(UUID adminUserId, String reason, Instant reviewedAt) {
    if (this.status != Status.APPROVED) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Chỉ có thể thu hồi phê duyệt đối với hồ sơ đang ở trạng thái APPROVED"
      );
    }
    if (reason == null || reason.isBlank()) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Lý do thu hồi phê duyệt hồ sơ là bắt buộc"
      );
    }
    this.status = Status.REJECTED;
    String trimmed = reason.trim();
    this.rejectionReason = trimmed.startsWith("[REVOKED] ") ? trimmed : "[REVOKED] " + trimmed;
    this.reviewedBy = adminUserId;
    this.reviewedAt = reviewedAt;
  }

  private void ensurePending() {
    if (this.status != Status.PENDING) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Chỉ có thể duyệt hồ sơ xác minh đang ở trạng thái PENDING"
      );
    }
  }
}

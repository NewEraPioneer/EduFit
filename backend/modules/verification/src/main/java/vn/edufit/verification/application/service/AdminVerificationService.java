package vn.edufit.verification.application.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.shared.audit.AuditRecord;
import vn.edufit.shared.audit.AuditSink;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.verification.infra.persistence.entity.VerificationRequest;
import vn.edufit.verification.infra.persistence.repository.VerificationRequestRepository;
import vn.edufit.verification.web.request.ReviewVerificationRequest;
import vn.edufit.verification.web.request.RevokeVerificationRequest;
import vn.edufit.verification.web.response.PendingVerificationQueueResponse;
import vn.edufit.verification.web.response.VerificationRequestDetailResponse;

@Service
public class AdminVerificationService {

  private final VerificationRequestRepository verificationRequestRepository;
  private final ProfileFacade profileFacade;
  private final AuditSink auditSink;
  private final IamFacade iamFacade;

  public AdminVerificationService(
      VerificationRequestRepository verificationRequestRepository,
      ProfileFacade profileFacade,
      AuditSink auditSink,
      IamFacade iamFacade
  ) {
    this.verificationRequestRepository = Objects.requireNonNull(verificationRequestRepository);
    this.profileFacade = Objects.requireNonNull(profileFacade);
    // Quyết định thu hồi của Admin bắt buộc phải được ghi audit, không được là tùy chọn
    this.auditSink = Objects.requireNonNull(auditSink);
    this.iamFacade = Objects.requireNonNull(iamFacade);
  }

  @Transactional(readOnly = true)
  public PendingVerificationQueueResponse getPendingQueue(CurrentUser currentUser, int page, int size) {
    return getQueue(currentUser, "PENDING", page, size);
  }

  @Transactional(readOnly = true)
  public PendingVerificationQueueResponse getQueue(
      CurrentUser currentUser,
      String status,
      int page,
      int size
  ) {
    ensureAdmin(currentUser);

    VerificationRequest.Status filterStatus = null;
    if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim())) {
      try {
        filterStatus = VerificationRequest.Status.valueOf(status.trim().toUpperCase());
      } catch (IllegalArgumentException ex) {
        throw new InvalidOperationException(
            ErrorCode.VALIDATION_FAILED,
            "Trạng thái hồ sơ không hợp lệ: " + status
        );
      }
    }

    Sort sort = (filterStatus == VerificationRequest.Status.PENDING)
        ? Sort.by(Sort.Direction.ASC, "submittedAt")
        : Sort.by(Sort.Direction.DESC, "submittedAt");

    Pageable pageable = PageRequest.of(
        Math.max(page, 0),
        Math.min(Math.max(size, 1), 100),
        sort
    );

    Page<VerificationRequest> requestPage = (filterStatus != null)
        ? verificationRequestRepository.findByStatus(filterStatus, pageable)
        : verificationRequestRepository.findAll(pageable);

    // Batch enrich tutor display names and user emails
    List<UUID> tutorIds = requestPage.getContent().stream()
        .map(VerificationRequest::getTutorId)
        .distinct()
        .toList();

    Map<UUID, TutorSummaryDto> tutorMap = new HashMap<>();
    Map<UUID, String> emailMap = new HashMap<>();

    for (UUID tutorId : tutorIds) {
      profileFacade.findTutorById(tutorId).ifPresent(tutorDto -> {
        tutorMap.put(tutorId, tutorDto);
        if (tutorDto.userId() != null) {
          iamFacade.findUserSummaryById(tutorDto.userId())
              .ifPresent(userSummary -> emailMap.put(tutorId, userSummary.email()));
        }
      });
    }

    return PendingVerificationQueueResponse.from(requestPage, tutorMap, emailMap);
  }

  @Transactional(readOnly = true)
  public VerificationRequestDetailResponse getDetail(CurrentUser currentUser, UUID requestId) {
    ensureAdmin(currentUser);
    VerificationRequest request = verificationRequestRepository.findWithCredentialsByRequestId(requestId)
        .orElseThrow(() -> EntityNotFoundException.of("VerificationRequest", requestId));
    return VerificationRequestDetailResponse.from(request);
  }

  @Transactional
  public VerificationRequestDetailResponse review(
      CurrentUser currentUser,
      UUID requestId,
      ReviewVerificationRequest reviewRequest
  ) {
    ensureAdmin(currentUser);
    VerificationRequest request = verificationRequestRepository.findWithCredentialsByRequestId(requestId)
        .orElseThrow(() -> EntityNotFoundException.of("VerificationRequest", requestId));

    if (request.getStatus() != VerificationRequest.Status.PENDING) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Hồ sơ xác minh này đã được xử lý trước đó"
      );
    }

    if (reviewRequest.action() == ReviewVerificationRequest.Action.APPROVE) {
      Instant now = Instant.now();
      request.approve(currentUser.getUserId(), now);
      profileFacade.markTutorVerified(request.getTutorId(), now);
    } else {
      if (reviewRequest.reason() == null || reviewRequest.reason().isBlank()) {
        throw new InvalidOperationException(
            ErrorCode.VALIDATION_FAILED,
            "Cần cung cấp lý do từ chối hồ sơ xác minh"
        );
      }
      Instant now = Instant.now();
      request.reject(currentUser.getUserId(), reviewRequest.reason().trim(), now);
      boolean hasOtherApproved = verificationRequestRepository.existsByTutorIdAndStatus(
          request.getTutorId(), VerificationRequest.Status.APPROVED
      );
      if (!hasOtherApproved) {
        profileFacade.markTutorRejected(request.getTutorId());
      }
    }

    VerificationRequest saved = verificationRequestRepository.save(request);
    return VerificationRequestDetailResponse.from(saved);
  }

  @Transactional
  public VerificationRequestDetailResponse revoke(
      CurrentUser currentUser,
      UUID requestId,
      RevokeVerificationRequest revokeRequest
  ) {
    ensureAdmin(currentUser);
    VerificationRequest request = verificationRequestRepository.findWithCredentialsByRequestId(requestId)
        .orElseThrow(() -> EntityNotFoundException.of("VerificationRequest", requestId));

    if (request.getStatus() != VerificationRequest.Status.APPROVED) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Chỉ có thể thu hồi phê duyệt đối với hồ sơ đang ở trạng thái APPROVED"
      );
    }

    if (revokeRequest == null || revokeRequest.reason() == null || revokeRequest.reason().trim().length() < 5) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Lý do thu hồi phê duyệt tối thiểu 5 ký tự"
      );
    }

    String reason = revokeRequest.reason().trim();
    Instant now = Instant.now();

    // 1. Chuyển trạng thái hồ sơ sang REJECTED (kèm prefix [REVOKED])
    request.revoke(currentUser.getUserId(), reason, now);
    VerificationRequest saved = verificationRequestRepository.save(request);

    // 1b. Đồng thời thu hồi tất cả các hồ sơ APPROVED khác của gia sư này (nếu có các đợt bổ sung khác)
    List<VerificationRequest> otherApprovedRequests = verificationRequestRepository
        .findByTutorIdOrderBySubmittedAtAsc(request.getTutorId())
        .stream()
        .filter(r -> r.getStatus() == VerificationRequest.Status.APPROVED && !r.getRequestId().equals(request.getRequestId()))
        .toList();
    for (VerificationRequest other : otherApprovedRequests) {
      other.revoke(currentUser.getUserId(), reason, now);
      verificationRequestRepository.save(other);
    }

    // 2. Giáng huy hiệu Profile của gia sư về UNVERIFIED (ẩn khỏi tìm kiếm mới, active classes giữ nguyên)
    profileFacade.markTutorUnverified(request.getTutorId());

    // 3. Ghi nhận sự kiện vào bảng audit_log
    auditSink.record(new AuditRecord(
        currentUser.getUserId(),
        "TUTOR_VERIFICATION_REVOKED",
        "VERIFICATION_REQUEST",
        requestId,
        reason,
        now,
        Map.of(
            "tutorId", request.getTutorId().toString(),
            "verificationId", requestId.toString(),
            "reason", reason
        )
    ));

    return VerificationRequestDetailResponse.from(saved);
  }

  private void ensureAdmin(CurrentUser currentUser) {
    if (currentUser == null || !currentUser.hasRole("ADMIN")) {
      throw new InvalidOperationException(
          ErrorCode.FORBIDDEN,
          "Chỉ quản trị viên mới được thao tác trên hồ sơ xác minh"
      );
    }
  }
}

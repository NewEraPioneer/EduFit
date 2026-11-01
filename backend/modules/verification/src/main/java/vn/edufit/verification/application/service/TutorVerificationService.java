package vn.edufit.verification.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;
import vn.edufit.storage.api.CredentialStorageService;
import vn.edufit.storage.api.StoredCredentialFile;
import vn.edufit.verification.infra.persistence.entity.Credential;
import vn.edufit.verification.infra.persistence.entity.CredentialFile;
import vn.edufit.verification.infra.persistence.entity.VerificationRequest;
import vn.edufit.verification.infra.persistence.repository.VerificationRequestRepository;
import vn.edufit.verification.web.request.SubmitVerificationRequest;
import vn.edufit.verification.web.response.VerificationRequestDetailResponse;

import org.springframework.core.io.Resource;
import vn.edufit.storage.api.StoredCredentialResource;
import vn.edufit.verification.infra.persistence.repository.CredentialFileRepository;

@Service
public class TutorVerificationService {

  public record CredentialFileDownload(
      String fileName,
      String contentType,
      long sizeBytes,
      Resource resource
  ) {}

  private final VerificationRequestRepository verificationRequestRepository;
  private final CredentialStorageService credentialStorageService;
  private final ProfileFacade profileFacade;
  private final CredentialFileRepository credentialFileRepository;

  public TutorVerificationService(
      VerificationRequestRepository verificationRequestRepository,
      CredentialStorageService credentialStorageService,
      ProfileFacade profileFacade,
      CredentialFileRepository credentialFileRepository
  ) {
    this.verificationRequestRepository = verificationRequestRepository;
    this.credentialStorageService = credentialStorageService;
    this.profileFacade = profileFacade;
    this.credentialFileRepository = java.util.Objects.requireNonNull(credentialFileRepository);
  }

  @Transactional(readOnly = true)
  public CredentialFileDownload getCredentialFile(CurrentUser currentUser, UUID fileId) {
    if (currentUser == null || currentUser.getUserId() == null) {
      throw new InvalidOperationException(
          ErrorCode.UNAUTHORIZED,
          "Phiên làm việc chưa được xác thực hoặc đã hết hạn"
      );
    }

    CredentialFile credentialFile = credentialFileRepository.findWithRequestByFileId(fileId)
        .orElseThrow(() -> EntityNotFoundException.of("CredentialFile", fileId));

    boolean isAdmin = currentUser.hasRole("ADMIN");
    if (!isAdmin) {
      if (!currentUser.hasRole("TUTOR")) {
        throw new InvalidOperationException(
            ErrorCode.FORBIDDEN,
            "Bạn không có quyền truy cập file bằng cấp này"
        );
      }
      UUID currentTutorId = profileFacade.getTutorIdByUserId(currentUser.getUserId());
      UUID fileOwnerTutorId = credentialFile.getCredential().getVerificationRequest().getTutorId();
      if (!currentTutorId.equals(fileOwnerTutorId)) {
        throw new InvalidOperationException(
            ErrorCode.FORBIDDEN,
            "Bạn không có quyền truy cập file bằng cấp của gia sư khác"
        );
      }
    }

    StoredCredentialResource stored = credentialStorageService.load(credentialFile.getStorageKey());
    return new CredentialFileDownload(
        credentialFile.getFileName(),
        credentialFile.getContentType(),
        credentialFile.getSizeBytes(),
        stored.resource()
    );
  }

  @Transactional
  public VerificationRequestDetailResponse submit(
      CurrentUser currentUser,
      SubmitVerificationRequest request,
      List<MultipartFile> files
  ) {
    ensureTutor(currentUser);
    UUID tutorId = profileFacade.getTutorIdByUserId(currentUser.getUserId());
    if (verificationRequestRepository.existsByTutorIdAndStatus(tutorId, VerificationRequest.Status.PENDING)) {
      throw new ResourceConflictException(
          ErrorCode.CONFLICT_DETECTED,
          "Gia sư đã có hồ sơ xác minh đang chờ duyệt"
      );
    }
    if (files == null || files.isEmpty()) {
      throw new InvalidOperationException(
          ErrorCode.CREDENTIAL_NOT_SUBMITTED,
          "Cần tải lên ít nhất một file bằng cấp"
      );
    }

    List<String> storedKeys = new ArrayList<>();
    try {
      VerificationRequest verificationRequest = VerificationRequest.pending(tutorId, Instant.now());
      for (SubmitVerificationRequest.CredentialItem item : request.credentials()) {
        if (item.offeringId() != null && profileFacade.findTutorOfferings(tutorId).stream()
            .noneMatch(offering -> offering.offeringId().equals(item.offeringId()))) {
          throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED,
              "Gói dạy được chọn làm bằng chứng không thuộc hồ sơ của bạn");
        }
        Credential credential = Credential.create(
            item.type(), item.institution(), item.year(), item.note(), item.offeringId());
        for (Integer fileIndex : item.fileIndexes()) {
          MultipartFile file = getFile(files, fileIndex);
          StoredCredentialFile storedFile = credentialStorageService.store(file);
          storedKeys.add(storedFile.storageKey());
          credential.addFile(CredentialFile.create(
              storedFile.fileName(),
              storedFile.storageKey(),
              storedFile.contentType(),
              storedFile.sizeBytes(),
              Instant.now()
          ));
        }
        verificationRequest.addCredential(credential);
      }

      VerificationRequest saved = verificationRequestRepository.save(verificationRequest);

      boolean isAlreadyVerified = profileFacade.findTutorById(tutorId)
          .map(t -> "VERIFIED".equalsIgnoreCase(t.status()))
          .orElse(false);

      if (!isAlreadyVerified) {
        profileFacade.markTutorPendingVerification(tutorId);
      }

      return VerificationRequestDetailResponse.from(saved);
    } catch (RuntimeException ex) {
      storedKeys.forEach(credentialStorageService::delete);
      throw ex;
    }
  }

  @Transactional(readOnly = true)
  public VerificationRequestDetailResponse getMyLatestRequest(CurrentUser currentUser) {
    ensureTutor(currentUser);
    UUID tutorId = profileFacade.getTutorIdByUserId(currentUser.getUserId());
    VerificationRequest latestRequest = verificationRequestRepository
        .findFirstByTutorIdOrderBySubmittedAtDesc(tutorId)
        .orElseThrow(() -> new EntityNotFoundException("Gia sư chưa có hồ sơ xác minh"));

    VerificationRequest fullRequest = verificationRequestRepository
        .findWithCredentialsByRequestId(latestRequest.getRequestId())
        .orElse(latestRequest);

    List<VerificationRequest> allRequests = verificationRequestRepository
        .findByTutorIdOrderBySubmittedAtAsc(tutorId);

    List<VerificationRequestDetailResponse.CredentialResponse> allCredentials = new ArrayList<>();
    if (allRequests == null || allRequests.isEmpty()) {
      allCredentials.addAll(
          fullRequest.getCredentials().stream()
              .map(VerificationRequestDetailResponse.CredentialResponse::from)
              .toList()
      );
    } else {
      java.util.Set<UUID> seenCredIds = new java.util.HashSet<>();
      for (VerificationRequest req : allRequests) {
        boolean isCurrent = req == fullRequest || (req.getRequestId() != null && req.getRequestId().equals(fullRequest.getRequestId()));
        if (isCurrent || req.getStatus() == VerificationRequest.Status.APPROVED) {
          for (Credential cred : req.getCredentials()) {
            if (cred.getCredentialId() == null || seenCredIds.add(cred.getCredentialId())) {
              allCredentials.add(VerificationRequestDetailResponse.CredentialResponse.from(cred));
            }
          }
        }
      }
    }

    return new VerificationRequestDetailResponse(
        fullRequest.getRequestId(),
        fullRequest.getTutorId(),
        fullRequest.getStatus().name(),
        fullRequest.getRejectionReason(),
        fullRequest.getSubmittedAt(),
        fullRequest.getReviewedBy(),
        fullRequest.getReviewedAt(),
        allCredentials
    );
  }

  private MultipartFile getFile(List<MultipartFile> files, Integer fileIndex) {
    if (fileIndex == null || fileIndex < 0 || fileIndex >= files.size()) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Chỉ mục file bằng cấp không hợp lệ"
      );
    }
    return files.get(fileIndex);
  }

  private void ensureTutor(CurrentUser currentUser) {
    if (currentUser == null || !currentUser.hasRole("TUTOR")) {
      throw new InvalidOperationException(
          ErrorCode.FORBIDDEN,
          "Chỉ tài khoản gia sư mới được nộp hồ sơ xác minh"
      );
    }
  }
}

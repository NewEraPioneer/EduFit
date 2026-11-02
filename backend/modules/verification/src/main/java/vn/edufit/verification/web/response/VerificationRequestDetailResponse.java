package vn.edufit.verification.web.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.edufit.verification.infra.persistence.entity.Credential;
import vn.edufit.verification.infra.persistence.entity.CredentialFile;
import vn.edufit.verification.infra.persistence.entity.VerificationRequest;

public record VerificationRequestDetailResponse(
    UUID requestId,
    UUID tutorId,
    String status,
    String rejectionReason,
    Instant submittedAt,
    UUID reviewedBy,
    Instant reviewedAt,
    List<CredentialResponse> credentials
) {

  public static VerificationRequestDetailResponse from(VerificationRequest request) {
    return new VerificationRequestDetailResponse(
        request.getRequestId(),
        request.getTutorId(),
        request.getStatus().name(),
        request.getRejectionReason(),
        request.getSubmittedAt(),
        request.getReviewedBy(),
        request.getReviewedAt(),
        request.getCredentials().stream()
            .map(CredentialResponse::from)
            .toList()
    );
  }

  public record CredentialResponse(
      UUID credentialId,
      String type,
      String institution,
      Short year,
      String note,
      UUID offeringId,
      List<FileResponse> files
  ) {

    public static CredentialResponse from(Credential credential) {
      return new CredentialResponse(
          credential.getCredentialId(),
          credential.getType().name(),
          credential.getInstitution(),
          credential.getYear(),
          credential.getNote(),
          credential.getOfferingId(),
          credential.getFiles().stream()
              .map(FileResponse::from)
              .toList()
      );
    }
  }

  public record FileResponse(
      UUID fileId,
      String fileName,
      String storageKey,
      String contentType,
      long sizeBytes,
      Instant uploadedAt
  ) {

    public static FileResponse from(CredentialFile file) {
      return new FileResponse(
          file.getFileId(),
          file.getFileName(),
          file.getStorageKey(),
          file.getContentType(),
          file.getSizeBytes(),
          file.getUploadedAt()
      );
    }
  }
}

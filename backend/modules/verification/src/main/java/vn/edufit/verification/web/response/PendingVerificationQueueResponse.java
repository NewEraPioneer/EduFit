package vn.edufit.verification.web.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.verification.infra.persistence.entity.Credential;
import vn.edufit.verification.infra.persistence.entity.CredentialFile;
import vn.edufit.verification.infra.persistence.entity.VerificationRequest;

public record PendingVerificationQueueResponse(
    List<Item> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

  public static PendingVerificationQueueResponse from(Page<VerificationRequest> pendingPage) {
    return new PendingVerificationQueueResponse(
        pendingPage.getContent().stream().map(Item::from).toList(),
        pendingPage.getNumber(),
        pendingPage.getSize(),
        pendingPage.getTotalElements(),
        pendingPage.getTotalPages()
    );
  }

  public static PendingVerificationQueueResponse from(
      Page<VerificationRequest> page,
      Map<UUID, TutorSummaryDto> tutorMap,
      Map<UUID, String> emailMap
  ) {
    List<Item> items = page.getContent().stream()
        .map(req -> Item.from(
            req,
            tutorMap != null ? tutorMap.get(req.getTutorId()) : null,
            emailMap != null ? emailMap.get(req.getTutorId()) : null
        ))
        .toList();
    return new PendingVerificationQueueResponse(
        items,
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages()
    );
  }

  public record Item(
      UUID requestId,
      UUID tutorId,
      String status,
      Instant submittedAt,
      String tutorName,
      String tutorEmail,
      String credentialType,
      String institution,
      Short year,
      List<String> documentUrls,
      String rejectionReason,
      Instant reviewedAt,
      UUID reviewerId
  ) {

    @JsonProperty("id")
    public UUID id() {
      return requestId;
    }

    @JsonProperty("email")
    public String email() {
      return tutorEmail;
    }

    @JsonProperty("reviewedBy")
    public UUID reviewedBy() {
      return reviewerId;
    }

    public Item(UUID requestId, UUID tutorId, String status, Instant submittedAt) {
      this(requestId, tutorId, status, submittedAt, null, null, null, null, null, List.of(), null, null, null);
    }

    public static Item from(VerificationRequest request) {
      return from(request, null, null);
    }

    public static Item from(VerificationRequest request, TutorSummaryDto tutor, String tutorEmail) {
      String credentialType = null;
      String institution = null;
      Short year = null;
      List<String> documentUrls = new ArrayList<>();

      if (request.getCredentials() != null && !request.getCredentials().isEmpty()) {
        Credential first = request.getCredentials().iterator().next();
        if (first.getType() != null) {
          credentialType = first.getType().name();
        }
        institution = first.getInstitution();
        year = first.getYear();

        for (Credential cred : request.getCredentials()) {
          if (cred.getFiles() != null) {
            for (CredentialFile f : cred.getFiles()) {
              documentUrls.add("/api/v1/verifications/credentials/files/" + f.getFileId());
            }
          }
        }
      }

      String tutorName = (tutor != null && tutor.displayName() != null) ? tutor.displayName() : null;

      return new Item(
          request.getRequestId(),
          request.getTutorId(),
          request.getStatus().name(),
          request.getSubmittedAt(),
          tutorName,
          tutorEmail,
          credentialType,
          institution,
          year,
          documentUrls,
          request.getRejectionReason(),
          request.getReviewedAt(),
          request.getReviewedBy()
      );
    }
  }
}

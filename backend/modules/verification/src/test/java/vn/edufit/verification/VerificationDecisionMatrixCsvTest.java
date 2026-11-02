package vn.edufit.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.BaseBusinessException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;
import vn.edufit.storage.api.CredentialStorageService;
import vn.edufit.storage.api.StoredCredentialFile;
import vn.edufit.verification.application.service.AdminVerificationService;
import vn.edufit.verification.application.service.TutorVerificationService;
import vn.edufit.verification.infra.persistence.entity.Credential;
import vn.edufit.verification.infra.persistence.entity.VerificationRequest;
import vn.edufit.verification.infra.persistence.repository.CredentialFileRepository;
import vn.edufit.verification.infra.persistence.repository.VerificationRequestRepository;
import vn.edufit.verification.web.request.ReviewVerificationRequest;
import vn.edufit.verification.web.request.SubmitVerificationRequest;
import vn.edufit.verification.web.response.VerificationRequestDetailResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("Kiểm thử ma trận quyết định duyệt hồ sơ & nộp bằng cấp qua dữ liệu CSV")
class VerificationDecisionMatrixCsvTest {

  @Mock
  private VerificationRequestRepository verificationRequestRepository;

  @Mock
  private CredentialFileRepository credentialFileRepository;

  @Mock
  private CredentialStorageService credentialStorageService;

  @Mock
  private ProfileFacade profileFacade;

  private AdminVerificationService adminVerificationService;
  private TutorVerificationService tutorVerificationService;

  private CurrentUser adminUser;
  private CurrentUser tutorUser;
  private UUID adminId;
  private UUID tutorUserId;
  private UUID tutorProfileId;

  @BeforeEach
  void setUp() {
    adminVerificationService = new AdminVerificationService(
        verificationRequestRepository,
        profileFacade,
        org.mockito.Mockito.mock(vn.edufit.shared.audit.AuditSink.class),
        org.mockito.Mockito.mock(vn.edufit.iam.api.IamFacade.class)
    );

    tutorVerificationService = new TutorVerificationService(
        verificationRequestRepository,
        credentialStorageService,
        profileFacade,
        credentialFileRepository
    );

    adminId = UUID.randomUUID();
    adminUser = new CurrentUser() {
      @Override
      public UUID getUserId() {
        return adminId;
      }

      @Override
      public String getEmail() {
        return "admin@edufit.vn";
      }

      @Override
      public Set<String> getRoles() {
        return Set.of("ADMIN");
      }

      @Override
      public boolean hasRole(String role) {
        return "ADMIN".equalsIgnoreCase(role);
      }
    };

    tutorUserId = UUID.randomUUID();
    tutorProfileId = UUID.randomUUID();
    tutorUser = new CurrentUser() {
      @Override
      public UUID getUserId() {
        return tutorUserId;
      }

      @Override
      public String getEmail() {
        return "tutor@edufit.vn";
      }

      @Override
      public Set<String> getRoles() {
        return Set.of("TUTOR");
      }

      @Override
      public boolean hasRole(String role) {
        return "TUTOR".equalsIgnoreCase(role);
      }
    };
  }

  @ParameterizedTest(name = "[{index}] {0}: initial={1}, action={2}, expected={4}")
  @CsvFileSource(resources = "/testdata/verification-decision-matrix.csv", numLinesToSkip = 1)
  @DisplayName("Kiểm thử ma trận duyệt KYC từ file CSV theo đặc tả testing specification")
  void shouldEvaluateDecisionMatrixFromCsv(
      String testCaseId,
      String initialStatus,
      String adminAction,
      String rejectionReason,
      String expectedFinalStatus,
      boolean shouldUpdateTutorStatus,
      String expectedErrorCode
  ) {
    UUID requestId = UUID.randomUUID();
    UUID targetTutorId = UUID.randomUUID();

    VerificationRequest request = VerificationRequest.pending(targetTutorId, Instant.now().minusSeconds(3600));
    if ("APPROVED".equals(initialStatus)) {
      request.approve(UUID.randomUUID(), Instant.now().minusSeconds(1800));
    } else if ("REJECTED".equals(initialStatus)) {
      request.reject(UUID.randomUUID(), "Lý do cũ", Instant.now().minusSeconds(1800));
    }

    when(verificationRequestRepository.findWithCredentialsByRequestId(requestId))
        .thenReturn(Optional.of(request));

    ReviewVerificationRequest.Action action = ReviewVerificationRequest.Action.valueOf(adminAction);
    ReviewVerificationRequest reviewRequest = new ReviewVerificationRequest(action, rejectionReason, null);

    if ("NONE".equals(expectedErrorCode)) {
      when(verificationRequestRepository.save(any(VerificationRequest.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      VerificationRequestDetailResponse response = adminVerificationService.review(
          adminUser, requestId, reviewRequest
      );

      assertEquals(expectedFinalStatus, response.status());
      if (shouldUpdateTutorStatus) {
        verify(profileFacade).markTutorVerified(eq(targetTutorId), any(Instant.class));
      } else if (action == ReviewVerificationRequest.Action.REJECT) {
        verify(profileFacade).markTutorRejected(targetTutorId);
      }
    } else {
      BaseBusinessException ex = assertThrows(
          BaseBusinessException.class,
          () -> adminVerificationService.review(adminUser, requestId, reviewRequest)
      );
      assertEquals(ErrorCode.valueOf(expectedErrorCode), ex.getErrorCode());
    }
  }

  @ParameterizedTest(name = "[{index}] {0}: hasPending={1}, files={2}, expectedError={5}")
  @CsvFileSource(resources = "/testdata/credential-submission-rules.csv", numLinesToSkip = 1)
  @DisplayName("Kiểm thử quy tắc nộp hồ sơ bằng cấp từ file CSV (BR-15)")
  void shouldEvaluateSubmissionRulesFromCsv(
      String testCaseId,
      boolean hasExistingPending,
      int fileCount,
      String institution,
      int gradYear,
      String expectedErrorCode
  ) {
    when(profileFacade.getTutorIdByUserId(tutorUserId)).thenReturn(tutorProfileId);
    when(verificationRequestRepository.existsByTutorIdAndStatus(tutorProfileId, VerificationRequest.Status.PENDING))
        .thenReturn(hasExistingPending);

    List<MultipartFile> files = fileCount > 0
        ? List.of(new MockMultipartFile("files", "degree.pdf", "application/pdf", "test".getBytes()))
        : List.of();

    SubmitVerificationRequest request = new SubmitVerificationRequest(List.of(
        new SubmitVerificationRequest.CredentialItem(
            Credential.Type.DEGREE,
            institution,
            (short) gradYear,
            "Ghi chú",
            List.of(0), null
        )
    ));

    if ("NONE".equals(expectedErrorCode)) {
      when(credentialStorageService.store(any())).thenReturn(
          new StoredCredentialFile("key-1", "degree.pdf", "application/pdf", 4L)
      );
      when(verificationRequestRepository.save(any(VerificationRequest.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      VerificationRequestDetailResponse response = tutorVerificationService.submit(
          tutorUser, request, files
      );

      assertEquals("PENDING", response.status());
      verify(profileFacade).markTutorPendingVerification(tutorProfileId);
    } else {
      BaseBusinessException ex = assertThrows(
          BaseBusinessException.class,
          () -> tutorVerificationService.submit(tutorUser, request, files)
      );
      assertEquals(ErrorCode.valueOf(expectedErrorCode), ex.getErrorCode());
    }
  }
}

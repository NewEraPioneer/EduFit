package vn.edufit.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;
import vn.edufit.storage.api.CredentialStorageService;
import vn.edufit.storage.api.StoredCredentialFile;
import vn.edufit.verification.application.service.TutorVerificationService;
import vn.edufit.verification.infra.persistence.entity.Credential;
import vn.edufit.verification.infra.persistence.entity.CredentialFile;
import vn.edufit.verification.infra.persistence.entity.VerificationRequest;
import vn.edufit.verification.infra.persistence.repository.VerificationRequestRepository;
import vn.edufit.verification.web.request.SubmitVerificationRequest;
import vn.edufit.verification.web.response.VerificationRequestDetailResponse;

@ExtendWith(MockitoExtension.class)
class TutorVerificationServiceTest {

  @Mock
  private VerificationRequestRepository verificationRequestRepository;

  @Mock
  private CredentialStorageService credentialStorageService;

  @Mock
  private ProfileFacade profileFacade;

  @Mock
  private vn.edufit.verification.infra.persistence.repository.CredentialFileRepository credentialFileRepository;

  private TutorVerificationService tutorVerificationService;

  private CurrentUser tutorUser;
  private UUID userId;
  private UUID tutorId;

  @BeforeEach
  void setUp() {
    tutorVerificationService = new TutorVerificationService(
        verificationRequestRepository,
        credentialStorageService,
        profileFacade,
        credentialFileRepository
    );

    userId = UUID.randomUUID();
    tutorId = UUID.randomUUID();
    tutorUser = new CurrentUser() {
      @Override
      public UUID getUserId() {
        return userId;
      }

      @Override
      public String getEmail() {
        return "tutor@test.com";
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

  @Test
  @DisplayName("Nộp hồ sơ xác minh thành công và cập nhật trạng thái gia sư")
  void shouldSubmitVerificationSuccessfully() {
    when(profileFacade.getTutorIdByUserId(userId)).thenReturn(tutorId);
    when(verificationRequestRepository.existsByTutorIdAndStatus(tutorId, VerificationRequest.Status.PENDING))
        .thenReturn(false);

    MockMultipartFile file = new MockMultipartFile(
        "file", "degree.pdf", "application/pdf", "content".getBytes()
    );
    List<MultipartFile> files = List.of(file);

    StoredCredentialFile storedFile = new StoredCredentialFile(
        "key-123", "degree.pdf", "application/pdf", 7L
    );
    when(credentialStorageService.store(file)).thenReturn(storedFile);

    when(verificationRequestRepository.save(any(VerificationRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    SubmitVerificationRequest request = new SubmitVerificationRequest(List.of(
        new SubmitVerificationRequest.CredentialItem(
            Credential.Type.DEGREE,
            "Đại học Bách Khoa",
            (short) 2024,
            "Bằng cử nhân xuất sắc",
            List.of(0), null
        )
    ));

    VerificationRequestDetailResponse response = tutorVerificationService.submit(
        tutorUser, request, files
    );

    assertNotNull(response);
    assertEquals("PENDING", response.status());
    assertEquals(1, response.credentials().size());
    verify(profileFacade).markTutorPendingVerification(tutorId);
  }

  @Test
  @DisplayName("Từ chối khi gia sư đã có hồ sơ PENDING (BR-15)")
  void shouldRejectWhenAlreadyHasPendingRequest() {
    when(profileFacade.getTutorIdByUserId(userId)).thenReturn(tutorId);
    when(verificationRequestRepository.existsByTutorIdAndStatus(tutorId, VerificationRequest.Status.PENDING))
        .thenReturn(true);

    SubmitVerificationRequest request = new SubmitVerificationRequest(List.of(
        new SubmitVerificationRequest.CredentialItem(
            Credential.Type.DEGREE, "Đại học Bách Khoa", (short) 2024, "Ghi chú", List.of(0), null
        )
    ));

    ResourceConflictException ex = assertThrows(
        ResourceConflictException.class,
        () -> tutorVerificationService.submit(tutorUser, request, List.of())
    );
    assertEquals(ErrorCode.CONFLICT_DETECTED, ex.getErrorCode());
  }

  @Test
  @DisplayName("Từ chối khi không có file tệp bằng cấp đính kèm")
  void shouldRejectWhenNoFilesProvided() {
    when(profileFacade.getTutorIdByUserId(userId)).thenReturn(tutorId);
    when(verificationRequestRepository.existsByTutorIdAndStatus(tutorId, VerificationRequest.Status.PENDING))
        .thenReturn(false);

    SubmitVerificationRequest request = new SubmitVerificationRequest(List.of(
        new SubmitVerificationRequest.CredentialItem(
            Credential.Type.DEGREE, "Đại học Bách Khoa", (short) 2024, "Ghi chú", List.of(0), null
        )
    ));

    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> tutorVerificationService.submit(tutorUser, request, List.of())
    );
    assertEquals(ErrorCode.CREDENTIAL_NOT_SUBMITTED, ex.getErrorCode());
  }

  @Test
  @DisplayName("Rollback xóa file lưu trữ khi lưu CSDL xảy ra lỗi")
  void shouldRollbackStoredFilesWhenSaveFails() {
    when(profileFacade.getTutorIdByUserId(userId)).thenReturn(tutorId);
    when(verificationRequestRepository.existsByTutorIdAndStatus(tutorId, VerificationRequest.Status.PENDING))
        .thenReturn(false);

    MockMultipartFile file = new MockMultipartFile(
        "file", "cert.pdf", "application/pdf", "cert content".getBytes()
    );
    StoredCredentialFile storedFile = new StoredCredentialFile(
        "rollback-key-999", "cert.pdf", "application/pdf", 12L
    );
    when(credentialStorageService.store(file)).thenReturn(storedFile);
    when(verificationRequestRepository.save(any())).thenThrow(new RuntimeException("DB Connection down"));

    SubmitVerificationRequest request = new SubmitVerificationRequest(List.of(
        new SubmitVerificationRequest.CredentialItem(
            Credential.Type.CERTIFICATE, "IELTS 8.0", (short) 2023, "IELTS IDP", List.of(0), null
        )
    ));

    assertThrows(
        RuntimeException.class,
        () -> tutorVerificationService.submit(tutorUser, request, List.of(file))
    );

    verify(credentialStorageService).delete("rollback-key-999");
  }

  @Test
  @DisplayName("Lấy trạng thái yêu cầu xác minh gần nhất thành công")
  void shouldGetMyLatestRequestSuccessfully() {
    when(profileFacade.getTutorIdByUserId(userId)).thenReturn(tutorId);

    VerificationRequest req = VerificationRequest.pending(tutorId, Instant.now());
    when(verificationRequestRepository.findFirstByTutorIdOrderBySubmittedAtDesc(tutorId))
        .thenReturn(Optional.of(req));
    when(verificationRequestRepository.findWithCredentialsByRequestId(req.getRequestId()))
        .thenReturn(Optional.of(req));

    VerificationRequestDetailResponse response = tutorVerificationService.getMyLatestRequest(tutorUser);
    assertNotNull(response);
    assertEquals("PENDING", response.status());
  }

  @Test
  @DisplayName("Ném EntityNotFoundException khi gia sư chưa từng nộp hồ sơ xác minh")
  void shouldThrowWhenNoVerificationRequestFound() {
    when(profileFacade.getTutorIdByUserId(userId)).thenReturn(tutorId);
    when(verificationRequestRepository.findFirstByTutorIdOrderBySubmittedAtDesc(tutorId))
        .thenReturn(Optional.empty());

    assertThrows(
        EntityNotFoundException.class,
        () -> tutorVerificationService.getMyLatestRequest(tutorUser)
    );
  }

  @Test
  @DisplayName("Gia sư chính chủ tải file bằng cấp thành công")
  void shouldGetCredentialFileForOwnerTutor() {
    UUID fileId = UUID.randomUUID();
    VerificationRequest request = VerificationRequest.pending(tutorId, Instant.now());
    Credential cred = Credential.create(Credential.Type.DEGREE, "ĐHBK", (short) 2023, null, null);
    request.addCredential(cred);
    CredentialFile credFile = CredentialFile.create("degree.pdf", "key-xyz", "application/pdf", 100L, Instant.now());
    cred.addFile(credFile);

    when(credentialFileRepository.findWithRequestByFileId(fileId)).thenReturn(Optional.of(credFile));
    when(profileFacade.getTutorIdByUserId(userId)).thenReturn(tutorId);
    org.springframework.core.io.Resource resource = new org.springframework.core.io.ByteArrayResource("content".getBytes());
    when(credentialStorageService.load("key-xyz")).thenReturn(new vn.edufit.storage.api.StoredCredentialResource("key-xyz", resource));

    TutorVerificationService.CredentialFileDownload download = tutorVerificationService.getCredentialFile(tutorUser, fileId);
    assertNotNull(download);
    assertEquals("degree.pdf", download.fileName());
    assertEquals("application/pdf", download.contentType());
  }

  @Test
  @DisplayName("Từ chối khi gia sư truy cập file bằng cấp của gia sư khác")
  void shouldThrowWhenTutorAccessesOtherTutorsFile() {
    UUID fileId = UUID.randomUUID();
    UUID otherTutorId = UUID.randomUUID();
    VerificationRequest request = VerificationRequest.pending(otherTutorId, Instant.now());
    Credential cred = Credential.create(Credential.Type.DEGREE, "ĐHBK", (short) 2023, null, null);
    request.addCredential(cred);
    CredentialFile credFile = CredentialFile.create("degree.pdf", "key-xyz", "application/pdf", 100L, Instant.now());
    cred.addFile(credFile);

    when(credentialFileRepository.findWithRequestByFileId(fileId)).thenReturn(Optional.of(credFile));
    when(profileFacade.getTutorIdByUserId(userId)).thenReturn(tutorId);

    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> tutorVerificationService.getCredentialFile(tutorUser, fileId)
    );
    assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
  }

  @Test
  @DisplayName("Nộp hồ sơ xác minh với giấy tờ loại OTHER (CCCD) và năm tốt nghiệp rỗng (null)")
  void shouldSubmitVerificationWithOtherTypeAndNullYearSuccessfully() {
    when(profileFacade.getTutorIdByUserId(userId)).thenReturn(tutorId);
    when(verificationRequestRepository.existsByTutorIdAndStatus(tutorId, VerificationRequest.Status.PENDING))
        .thenReturn(false);

    MockMultipartFile file = new MockMultipartFile(
        "file", "cccd.jpg", "image/jpeg", "image-content".getBytes()
    );
    List<MultipartFile> files = List.of(file);

    StoredCredentialFile storedFile = new StoredCredentialFile(
        "key-cccd", "cccd.jpg", "image/jpeg", 13L
    );
    when(credentialStorageService.store(file)).thenReturn(storedFile);

    when(verificationRequestRepository.save(any(VerificationRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    SubmitVerificationRequest request = new SubmitVerificationRequest(List.of(
        new SubmitVerificationRequest.CredentialItem(
            Credential.Type.OTHER,
            "Cục Cảnh sát QLHC về TTXH",
            null,
            "Căn cước công dân gắn chip",
            List.of(0), null
        )
    ));

    VerificationRequestDetailResponse response = tutorVerificationService.submit(
        tutorUser, request, files
    );

    assertNotNull(response);
    assertEquals("PENDING", response.status());
    assertEquals(1, response.credentials().size());
    assertEquals("OTHER", response.credentials().getFirst().type());
    org.junit.jupiter.api.Assertions.assertNull(response.credentials().getFirst().year());
    verify(profileFacade).markTutorPendingVerification(tutorId);
  }

  @Test
  @DisplayName("Gia sư đã VERIFIED khi nộp thêm bằng cấp thì BẢO TOÀN HUY HIỆU VERIFIED (không gọi markPending)")
  void shouldPreserveVerifiedBadgeWhenTutorSubmitsAdditionalCredentials() {
    when(profileFacade.getTutorIdByUserId(userId)).thenReturn(tutorId);
    when(verificationRequestRepository.existsByTutorIdAndStatus(tutorId, VerificationRequest.Status.PENDING))
        .thenReturn(false);

    TutorSummaryDto verifiedTutor = new TutorSummaryDto(tutorId, userId, "Gia sư Bách Khoa", "Thạc sĩ CNTT", null, "ONLINE", null, false, null, "Hà Nội", (short) 5, null, "VERIFIED", Instant.now(), null, 10);
    when(profileFacade.findTutorById(tutorId)).thenReturn(Optional.of(verifiedTutor));

    MockMultipartFile file = new MockMultipartFile(
        "file", "ielts.pdf", "application/pdf", "ielts-content".getBytes()
    );
    List<MultipartFile> files = List.of(file);

    StoredCredentialFile storedFile = new StoredCredentialFile(
        "key-ielts-85", "ielts.pdf", "application/pdf", 100L
    );
    when(credentialStorageService.store(file)).thenReturn(storedFile);

    when(verificationRequestRepository.save(any(VerificationRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    SubmitVerificationRequest request = new SubmitVerificationRequest(List.of(
        new SubmitVerificationRequest.CredentialItem(
            Credential.Type.CERTIFICATE,
            "IDP Education",
            (short) 2024,
            "IELTS 8.5 Certificate",
            List.of(0), null
        )
    ));

    VerificationRequestDetailResponse response = tutorVerificationService.submit(
        tutorUser, request, files
    );

    assertNotNull(response);
    assertEquals("PENDING", response.status());
    // QUAN TRỌNG: Tuyệt đối không giáng cấp Profile về PENDING
    verify(profileFacade, never()).markTutorPendingVerification(any());
  }

  @Test
  @DisplayName("Gia sư ở trạng thái UNVERIFIED khi nộp bằng cấp thì chuyển profile sang PENDING_VERIFICATION")
  void shouldMarkPendingWhenTutorIsUnverified() {
    when(profileFacade.getTutorIdByUserId(userId)).thenReturn(tutorId);
    when(verificationRequestRepository.existsByTutorIdAndStatus(tutorId, VerificationRequest.Status.PENDING))
        .thenReturn(false);

    TutorSummaryDto unverifiedTutor = new TutorSummaryDto(tutorId, userId, "Gia sư mới", null, null, "ONLINE", null, false, null, "Hà Nội", (short) 1, null, "UNVERIFIED", null, null, 0);
    when(profileFacade.findTutorById(tutorId)).thenReturn(Optional.of(unverifiedTutor));

    MockMultipartFile file = new MockMultipartFile(
        "file", "degree.pdf", "application/pdf", "degree-content".getBytes()
    );
    List<MultipartFile> files = List.of(file);

    StoredCredentialFile storedFile = new StoredCredentialFile(
        "key-deg", "degree.pdf", "application/pdf", 100L
    );
    when(credentialStorageService.store(file)).thenReturn(storedFile);

    when(verificationRequestRepository.save(any(VerificationRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    SubmitVerificationRequest request = new SubmitVerificationRequest(List.of(
        new SubmitVerificationRequest.CredentialItem(
            Credential.Type.DEGREE,
            "Đại học Quốc Gia Hà Nội",
            (short) 2023,
            "Cử nhân Sư phạm",
            List.of(0), null
        )
    ));

    VerificationRequestDetailResponse response = tutorVerificationService.submit(
        tutorUser, request, files
    );

    assertNotNull(response);
    assertEquals("PENDING", response.status());
    verify(profileFacade).markTutorPendingVerification(tutorId);
  }

  @Test
  @DisplayName("Khi gia sư có hồ sơ APPROVED trước đó và đang nộp bổ sung PENDING thì getMyLatestRequest tổng hợp cả 2 bằng cấp")
  void shouldAggregateApprovedCredentialsAndPendingSupplementaryInGetMyLatestRequest() {
    when(profileFacade.getTutorIdByUserId(userId)).thenReturn(tutorId);

    // Hồ sơ 1 đã duyệt (APPROVED) với bằng cử nhân
    VerificationRequest req1 = VerificationRequest.pending(tutorId, Instant.now().minusSeconds(86400));
    req1.approve(UUID.randomUUID(), Instant.now().minusSeconds(43200));
    org.springframework.test.util.ReflectionTestUtils.setField(req1, "requestId", UUID.randomUUID());
    Credential cred1 = Credential.create(Credential.Type.DEGREE, "Đại học Bách Khoa Hà Nội", (short) 2022, "Cử nhân", null);
    req1.addCredential(cred1);

    // Hồ sơ 2 bổ sung đang chờ duyệt (PENDING) với chứng chỉ IELTS
    VerificationRequest req2 = VerificationRequest.pending(tutorId, Instant.now());
    UUID req2Id = UUID.randomUUID();
    org.springframework.test.util.ReflectionTestUtils.setField(req2, "requestId", req2Id);
    Credential cred2 = Credential.create(Credential.Type.CERTIFICATE, "British Council", (short) 2024, "IELTS 8.0", null);
    req2.addCredential(cred2);

    when(verificationRequestRepository.findFirstByTutorIdOrderBySubmittedAtDesc(tutorId))
        .thenReturn(Optional.of(req2));
    when(verificationRequestRepository.findWithCredentialsByRequestId(req2Id))
        .thenReturn(Optional.of(req2));
    when(verificationRequestRepository.findByTutorIdOrderBySubmittedAtAsc(tutorId))
        .thenReturn(List.of(req1, req2));

    VerificationRequestDetailResponse response = tutorVerificationService.getMyLatestRequest(tutorUser);

    assertNotNull(response);
    assertEquals("PENDING", response.status());
    // Danh sách bằng cấp phải tổng hợp cả bằng đã APPROVED trước đó và bằng đang bổ sung
    assertEquals(2, response.credentials().size());
    assertEquals("Đại học Bách Khoa Hà Nội", response.credentials().get(0).institution());
    assertEquals("British Council", response.credentials().get(1).institution());
  }
}

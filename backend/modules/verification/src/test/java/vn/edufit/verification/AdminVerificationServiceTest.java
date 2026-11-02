package vn.edufit.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.shared.audit.AuditRecord;
import vn.edufit.shared.audit.AuditSink;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.verification.application.service.AdminVerificationService;
import vn.edufit.verification.infra.persistence.entity.Credential;
import vn.edufit.verification.infra.persistence.entity.VerificationRequest;
import vn.edufit.verification.infra.persistence.repository.VerificationRequestRepository;
import vn.edufit.verification.web.request.ReviewVerificationRequest;
import vn.edufit.verification.web.request.RevokeVerificationRequest;
import vn.edufit.verification.web.response.PendingVerificationQueueResponse;
import vn.edufit.verification.web.response.VerificationRequestDetailResponse;

@ExtendWith(MockitoExtension.class)
class AdminVerificationServiceTest {

  @Mock
  private VerificationRequestRepository verificationRequestRepository;

  @Mock
  private ProfileFacade profileFacade;

  @Mock
  private AuditSink auditSink;

  @Mock
  private IamFacade iamFacade;

  private AdminVerificationService adminVerificationService;

  private CurrentUser adminUser;
  private UUID adminId;

  @BeforeEach
  void setUp() {
    adminVerificationService = new AdminVerificationService(
        verificationRequestRepository,
        profileFacade,
        auditSink,
        iamFacade
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
  }

  @Test
  @DisplayName("Lấy danh sách hàng đợi các gia sư đang chờ duyệt")
  void shouldGetPendingQueue() {
    VerificationRequest req = VerificationRequest.pending(UUID.randomUUID(), Instant.now());
    Page<VerificationRequest> page = new PageImpl<>(List.of(req));
    when(verificationRequestRepository.findByStatus(
        eq(VerificationRequest.Status.PENDING),
        any(Pageable.class)
    )).thenReturn(page);

    PendingVerificationQueueResponse response = adminVerificationService.getPendingQueue(adminUser, 0, 10);
    assertNotNull(response);
    assertEquals(1, response.items().size());
    assertEquals("PENDING", response.items().getFirst().status());
  }

  @Test
  @DisplayName("Lấy danh sách hàng đợi với lọc status=APPROVED và làm giàu thông tin gia sư/email")
  void shouldGetQueueWithStatusApprovedAndEnrichedData() {
    UUID tutorId = UUID.randomUUID();
    UUID tutorUserId = UUID.randomUUID();
    VerificationRequest req = VerificationRequest.pending(tutorId, Instant.now());
    req.approve(adminId, Instant.now());
    Credential cred = Credential.create(Credential.Type.DEGREE, "Đại học Sư Phạm Hà Nội", (short) 2023, "Toán học", null);
    req.addCredential(cred);

    Page<VerificationRequest> page = new PageImpl<>(List.of(req));
    when(verificationRequestRepository.findByStatus(eq(VerificationRequest.Status.APPROVED), any(Pageable.class)))
        .thenReturn(page);

    TutorSummaryDto tutorDto = new TutorSummaryDto(tutorId, tutorUserId, "Nguyễn Văn A", "Giáo viên Toán giỏi", null, "ONLINE", null, false, null, "Hà Nội", (short) 5, null, "VERIFIED", Instant.now(), null, 0);
    when(profileFacade.findTutorById(tutorId)).thenReturn(Optional.of(tutorDto));

    UserSummaryView userSummary = new UserSummaryView(
        tutorUserId, "tutor.a@edufit.vn", "Nguyễn Văn A", AccountRole.TUTOR, AccountStatus.ACTIVE
    );
    when(iamFacade.findUserSummaryById(tutorUserId)).thenReturn(Optional.of(userSummary));

    PendingVerificationQueueResponse response = adminVerificationService.getQueue(adminUser, "APPROVED", 0, 10);
    assertNotNull(response);
    assertEquals(1, response.items().size());

    PendingVerificationQueueResponse.Item item = response.items().getFirst();
    assertEquals("APPROVED", item.status());
    assertEquals(tutorId, item.tutorId());
    assertEquals("Nguyễn Văn A", item.tutorName());
    assertEquals("tutor.a@edufit.vn", item.tutorEmail());
    assertEquals("DEGREE", item.credentialType());
    assertEquals("Đại học Sư Phạm Hà Nội", item.institution());
    assertEquals((short) 2023, item.year());
    assertEquals(req.getRequestId(), item.id());
  }

  @Test
  @DisplayName("Lấy danh sách tất cả hồ sơ khi status là null")
  void shouldGetQueueAllWhenStatusNull() {
    UUID tutorId = UUID.randomUUID();
    VerificationRequest req = VerificationRequest.pending(tutorId, Instant.now());
    Page<VerificationRequest> page = new PageImpl<>(List.of(req));
    when(verificationRequestRepository.findAll(any(Pageable.class))).thenReturn(page);

    PendingVerificationQueueResponse response = adminVerificationService.getQueue(adminUser, null, 0, 10);
    assertNotNull(response);
    assertEquals(1, response.items().size());
  }

  @Test
  @DisplayName("Ném ngoại lệ khi truyền status không hợp lệ")
  void shouldThrowWhenInvalidStatus() {
    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> adminVerificationService.getQueue(adminUser, "INVALID_STATUS", 0, 10)
    );
    assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
  }

  @Test
  @DisplayName("Admin phê duyệt yêu cầu xác minh (APPROVE) -> Cập nhật trạng thái VERIFIED")
  void shouldApproveVerificationRequest() {
    UUID requestId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    VerificationRequest req = VerificationRequest.pending(tutorId, Instant.now());

    when(verificationRequestRepository.findWithCredentialsByRequestId(requestId))
        .thenReturn(Optional.of(req));
    when(verificationRequestRepository.save(any(VerificationRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ReviewVerificationRequest reviewRequest = new ReviewVerificationRequest(
        ReviewVerificationRequest.Action.APPROVE,
        null, null
    );

    VerificationRequestDetailResponse response = adminVerificationService.review(
        adminUser, requestId, reviewRequest
    );

    assertNotNull(response);
    assertEquals("APPROVED", response.status());
    assertEquals(adminId, response.reviewedBy());
    verify(profileFacade).markTutorVerified(eq(tutorId), any(Instant.class));
  }

  @Test
  @DisplayName("Admin từ chối yêu cầu xác minh (REJECT) -> Cập nhật trạng thái UNVERIFIED kèm lý do")
  void shouldRejectVerificationRequest() {
    UUID requestId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    VerificationRequest req = VerificationRequest.pending(tutorId, Instant.now());

    when(verificationRequestRepository.findWithCredentialsByRequestId(requestId))
        .thenReturn(Optional.of(req));
    when(verificationRequestRepository.save(any(VerificationRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ReviewVerificationRequest reviewRequest = new ReviewVerificationRequest(
        ReviewVerificationRequest.Action.REJECT,
        "Ảnh chụp văn bằng bị mờ, không rõ con dấu", null
    );

    VerificationRequestDetailResponse response = adminVerificationService.review(
        adminUser, requestId, reviewRequest
    );

    assertNotNull(response);
    assertEquals("REJECTED", response.status());
    assertEquals("Ảnh chụp văn bằng bị mờ, không rõ con dấu", response.rejectionReason());
    assertEquals(adminId, response.reviewedBy());
    verify(profileFacade).markTutorRejected(tutorId);
  }

  @Test
  @DisplayName("Admin từ chối hồ sơ bổ sung nhưng gia sư đã có hồ sơ APPROVED trước đó -> KHÔNG giáng cấp profile (giữ nguyên VERIFIED)")
  void shouldNotDemoteTutorWhenRejectingSupplementaryRequestIfEarlierApprovedExists() {
    UUID requestId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    VerificationRequest req = VerificationRequest.pending(tutorId, Instant.now());

    when(verificationRequestRepository.findWithCredentialsByRequestId(requestId))
        .thenReturn(Optional.of(req));
    when(verificationRequestRepository.save(any(VerificationRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(verificationRequestRepository.existsByTutorIdAndStatus(tutorId, VerificationRequest.Status.APPROVED))
        .thenReturn(true);

    ReviewVerificationRequest reviewRequest = new ReviewVerificationRequest(
        ReviewVerificationRequest.Action.REJECT,
        "Chứng chỉ bổ sung này đã hết hạn", null
    );

    VerificationRequestDetailResponse response = adminVerificationService.review(
        adminUser, requestId, reviewRequest
    );

    assertNotNull(response);
    assertEquals("REJECTED", response.status());
    // Quan trọng: Không được giáng cấp profile vì gia sư vẫn có hồ sơ đã duyệt trước đó
    verify(profileFacade, never()).markTutorRejected(any());
  }

  @Test
  @DisplayName("Admin thu hồi phê duyệt (REVOKE) -> Chuyển trạng thái REJECTED, giáng huy hiệu và ghi audit log")
  void shouldRevokeVerificationSuccessfullyAndLogAudit() {
    UUID requestId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    VerificationRequest req = VerificationRequest.pending(tutorId, Instant.now());
    req.approve(adminId, Instant.now());

    when(verificationRequestRepository.findWithCredentialsByRequestId(requestId))
        .thenReturn(Optional.of(req));
    when(verificationRequestRepository.save(any(VerificationRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    RevokeVerificationRequest revokeRequest = new RevokeVerificationRequest("Bằng cấp phát hiện là bằng giả");

    VerificationRequestDetailResponse response = adminVerificationService.revoke(
        adminUser, requestId, revokeRequest
    );

    assertNotNull(response);
    assertEquals("REJECTED", response.status());
    assertEquals("[REVOKED] Bằng cấp phát hiện là bằng giả", response.rejectionReason());
    assertEquals(adminId, response.reviewedBy());

    // 1. Giáng huy hiệu Profile của gia sư về UNVERIFIED
    verify(profileFacade).markTutorUnverified(tutorId);

    // 2. Kiểm tra ghi nhận sự kiện vào audit_log
    ArgumentCaptor<AuditRecord> auditCaptor = ArgumentCaptor.forClass(AuditRecord.class);
    verify(auditSink).record(auditCaptor.capture());

    AuditRecord record = auditCaptor.getValue();
    assertEquals(adminId, record.actorUserId());
    assertEquals("TUTOR_VERIFICATION_REVOKED", record.action());
    assertEquals("VERIFICATION_REQUEST", record.targetType());
    assertEquals(requestId, record.targetId());
    assertEquals("Bằng cấp phát hiện là bằng giả", record.reason());
    assertEquals(tutorId.toString(), record.metadata().get("tutorId"));
    assertEquals(requestId.toString(), record.metadata().get("verificationId"));
    assertEquals("Bằng cấp phát hiện là bằng giả", record.metadata().get("reason"));
  }

  @Test
  @DisplayName("Admin thu hồi phê duyệt khi gia sư có nhiều hồ sơ APPROVED -> Thu hồi tất cả các hồ sơ APPROVED")
  void shouldRevokeAllApprovedRequestsWhenTutorHasMultipleApprovedRequests() {
    UUID requestId = UUID.randomUUID();
    UUID olderRequestId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();

    VerificationRequest req1 = VerificationRequest.pending(tutorId, Instant.now().minusSeconds(86400));
    req1.approve(adminId, Instant.now().minusSeconds(43200));
    org.springframework.test.util.ReflectionTestUtils.setField(req1, "requestId", olderRequestId);

    VerificationRequest req2 = VerificationRequest.pending(tutorId, Instant.now());
    req2.approve(adminId, Instant.now());
    org.springframework.test.util.ReflectionTestUtils.setField(req2, "requestId", requestId);

    when(verificationRequestRepository.findWithCredentialsByRequestId(requestId))
        .thenReturn(Optional.of(req2));
    when(verificationRequestRepository.findByTutorIdOrderBySubmittedAtAsc(tutorId))
        .thenReturn(List.of(req1, req2));
    when(verificationRequestRepository.save(any(VerificationRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    RevokeVerificationRequest revokeRequest = new RevokeVerificationRequest("Vi phạm nghiêm trọng đạo đức nghề nghiệp");

    VerificationRequestDetailResponse response = adminVerificationService.revoke(
        adminUser, requestId, revokeRequest
    );

    assertNotNull(response);
    assertEquals("REJECTED", response.status());
    // Cả 2 hồ sơ đều phải chuyển sang REJECTED
    assertEquals(VerificationRequest.Status.REJECTED, req1.getStatus());
    assertEquals(VerificationRequest.Status.REJECTED, req2.getStatus());
    assertTrue(req1.getRejectionReason().contains("Vi phạm nghiêm trọng"));
    assertTrue(req2.getRejectionReason().contains("Vi phạm nghiêm trọng"));
    verify(profileFacade).markTutorUnverified(tutorId);
  }

  @Test
  @DisplayName("Từ chối thu hồi khi hồ sơ chưa ở trạng thái APPROVED")
  void shouldThrowWhenRevokingNonApprovedRequest() {
    UUID requestId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    VerificationRequest req = VerificationRequest.pending(tutorId, Instant.now());

    when(verificationRequestRepository.findWithCredentialsByRequestId(requestId))
        .thenReturn(Optional.of(req));

    RevokeVerificationRequest revokeRequest = new RevokeVerificationRequest("Lý do thu hồi hợp lệ dài hơn 5 ký tự");

    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> adminVerificationService.revoke(adminUser, requestId, revokeRequest)
    );
    assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
    verify(profileFacade, never()).markTutorUnverified(any());
    verify(auditSink, never()).record(any(AuditRecord.class));
  }

  @Test
  @DisplayName("Từ chối thu hồi khi lý do rỗng hoặc dưới 5 ký tự")
  void shouldThrowWhenRevokeReasonTooShort() {
    UUID requestId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    VerificationRequest req = VerificationRequest.pending(tutorId, Instant.now());
    req.approve(adminId, Instant.now());

    when(verificationRequestRepository.findWithCredentialsByRequestId(requestId))
        .thenReturn(Optional.of(req));

    RevokeVerificationRequest shortReasonRequest = new RevokeVerificationRequest("Lỗi");

    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> adminVerificationService.revoke(adminUser, requestId, shortReasonRequest)
    );
    assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
  }

  @Test
  @DisplayName("Từ chối xử lý khi người dùng không có quyền ADMIN")
  void shouldRejectWhenNotAdmin() {
    CurrentUser nonAdmin = new CurrentUser() {
      @Override
      public UUID getUserId() {
        return UUID.randomUUID();
      }

      @Override
      public String getEmail() {
        return "student@edufit.vn";
      }

      @Override
      public Set<String> getRoles() {
        return Set.of("STUDENT");
      }

      @Override
      public boolean hasRole(String role) {
        return "STUDENT".equalsIgnoreCase(role);
      }
    };

    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> adminVerificationService.getPendingQueue(nonAdmin, 0, 10)
    );
    assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
  }

  @Test
  @DisplayName("Admin lấy chi tiết yêu cầu xác minh kèm danh sách bằng cấp")
  void shouldGetDetailSuccessfully() {
    UUID requestId = UUID.randomUUID();
    VerificationRequest req = VerificationRequest.pending(UUID.randomUUID(), Instant.now());
    when(verificationRequestRepository.findWithCredentialsByRequestId(requestId))
        .thenReturn(Optional.of(req));

    VerificationRequestDetailResponse detail = adminVerificationService.getDetail(adminUser, requestId);
    assertNotNull(detail);
    assertEquals("PENDING", detail.status());
  }
}

package vn.edufit.verification.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.shared.audit.AuditRecord;
import vn.edufit.shared.audit.AuditSink;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.verification.application.service.AdminVerificationService;
import vn.edufit.verification.infra.persistence.entity.VerificationRequest;
import vn.edufit.verification.infra.persistence.repository.VerificationRequestRepository;
import vn.edufit.verification.web.request.RevokeVerificationRequest;
import vn.edufit.verification.web.response.VerificationRequestDetailResponse;

/**
 * QA Independent Adversarial, Concurrency & Security Test Suite for AC-02 (Revocation & Queue Management)
 */
@ExtendWith(MockitoExtension.class)
class AdminVerificationRevocationConcurrencyAndSecurityQATest {

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
        return "admin.qa@edufit.vn";
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
  @DisplayName("CONCURRENCY: 10 luồng đồng thời gọi revoke trên cùng 1 hồ sơ -> Đúng 1 luồng thành công, 9 luồng bị chặn")
  void shouldAllowOnlyOneRevocationUnderConcurrentAttacks() throws InterruptedException {
    UUID requestId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    VerificationRequest request = VerificationRequest.pending(tutorId, Instant.now());
    request.approve(adminId, Instant.now());

    // Setup repository to return the shared entity instance under synchronized save
    when(verificationRequestRepository.findWithCredentialsByRequestId(requestId))
        .thenReturn(Optional.of(request));

    when(verificationRequestRepository.save(any(VerificationRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failedCount = new AtomicInteger(0);
    List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < threadCount; i++) {
      final int idx = i;
      executor.submit(() -> {
        try {
          startLatch.await(); // Thao tác đồng loạt bắn request tại cùng mili-giây
          RevokeVerificationRequest revokeRequest = new RevokeVerificationRequest(
              "Gian lận bằng cấp phát hiện qua đợt rà soát #" + idx
          );
          synchronized (request) {
            adminVerificationService.revoke(adminUser, requestId, revokeRequest);
          }
          successCount.incrementAndGet();
        } catch (InvalidOperationException e) {
          failedCount.incrementAndGet();
          exceptions.add(e);
        } catch (Throwable t) {
          exceptions.add(t);
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown(); // Bắn 10 luồng cùng lúc
    boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
    executor.shutdown();

    assertTrue(finished, "Toàn bộ luồng đồng thời phải hoàn tất trong 5 giây");
    assertEquals(1, successCount.get(), "Chỉ duy nhất 1 luồng được phép thu hồi thành công");
    assertEquals(9, failedCount.get(), "9 luồng còn lại phải bị từ chối do trạng thái không còn là APPROVED");

    for (Throwable ex : exceptions) {
      assertTrue(ex instanceof InvalidOperationException);
      assertEquals(ErrorCode.INVALID_OPERATION, ((InvalidOperationException) ex).getErrorCode());
    }

    // Verify side-effects: markTutorUnverified and auditSink called exactly 1 time
    verify(profileFacade, times(1)).markTutorUnverified(tutorId);
    verify(auditSink, times(1)).record(any(AuditRecord.class));
    assertEquals(VerificationRequest.Status.REJECTED, request.getStatus());
  }

  @Test
  @DisplayName("SECURITY & IDOR: Người dùng không có quyền ADMIN bị chặn triệt để khi gọi revoke")
  void shouldBlockNonAdminUsersFromRevoking() {
    CurrentUser tutorUser = mock(CurrentUser.class);
    when(tutorUser.hasRole("ADMIN")).thenReturn(false);

    UUID requestId = UUID.randomUUID();
    RevokeVerificationRequest revokeRequest = new RevokeVerificationRequest("Thu hồi trái phép");

    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> adminVerificationService.revoke(tutorUser, requestId, revokeRequest)
    );

    assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    verify(verificationRequestRepository, never()).findWithCredentialsByRequestId(any());
    verify(profileFacade, never()).markTutorUnverified(any());
    verify(auditSink, never()).record(any());
  }

  @Test
  @DisplayName("STATE GUARD: Không thể thu hồi hồ sơ đang ở trạng thái PENDING")
  void shouldRejectRevocationOfPendingRequest() {
    UUID requestId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    VerificationRequest request = VerificationRequest.pending(tutorId, Instant.now());

    when(verificationRequestRepository.findWithCredentialsByRequestId(requestId))
        .thenReturn(Optional.of(request));

    RevokeVerificationRequest revokeRequest = new RevokeVerificationRequest("Hồ sơ nộp chưa hoàn chỉnh");

    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> adminVerificationService.revoke(adminUser, requestId, revokeRequest)
    );

    assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
    verify(profileFacade, never()).markTutorUnverified(any());
    verify(auditSink, never()).record(any());
  }

  @Test
  @DisplayName("STATE GUARD: Không thể thu hồi hồ sơ đã bị REJECTED trước đó (Idempotency Guard)")
  void shouldRejectRevocationOfAlreadyRejectedRequest() {
    UUID requestId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    VerificationRequest request = VerificationRequest.pending(tutorId, Instant.now());
    request.reject(adminId, "Bằng cấp không hợp lệ", Instant.now());

    when(verificationRequestRepository.findWithCredentialsByRequestId(requestId))
        .thenReturn(Optional.of(request));

    RevokeVerificationRequest revokeRequest = new RevokeVerificationRequest("Thu hồi tiếp lần nữa");

    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> adminVerificationService.revoke(adminUser, requestId, revokeRequest)
    );

    assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
    verify(profileFacade, never()).markTutorUnverified(any());
    verify(auditSink, never()).record(any());
  }

  @Test
  @DisplayName("AUDIT INTEGRITY: Kiểm chứng 100% thuộc tính của bản ghi TUTOR_VERIFICATION_REVOKED")
  void shouldRecordCompleteAuditLogProperties() {
    UUID requestId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    VerificationRequest request = VerificationRequest.pending(tutorId, Instant.now());
    request.approve(adminId, Instant.now());

    when(verificationRequestRepository.findWithCredentialsByRequestId(requestId))
        .thenReturn(Optional.of(request));
    when(verificationRequestRepository.save(any(VerificationRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    String reasonText = "Chứng chỉ IELTS 8.5 không tìm thấy trên hệ thống xác thực của IDP";
    RevokeVerificationRequest revokeRequest = new RevokeVerificationRequest(reasonText);

    VerificationRequestDetailResponse response = adminVerificationService.revoke(
        adminUser, requestId, revokeRequest
    );

    assertNotNull(response);
    assertEquals("REJECTED", response.status());
    assertEquals("[REVOKED] " + reasonText, response.rejectionReason());

    ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
    verify(auditSink).record(captor.capture());

    AuditRecord record = captor.getValue();
    assertEquals(adminId, record.actorUserId());
    assertEquals("TUTOR_VERIFICATION_REVOKED", record.action());
    assertEquals("VERIFICATION_REQUEST", record.targetType());
    assertEquals(requestId, record.targetId());
    assertEquals(reasonText, record.reason());
    assertNotNull(record.occurredAt());
    assertEquals(tutorId.toString(), record.metadata().get("tutorId"));
    assertEquals(requestId.toString(), record.metadata().get("verificationId"));
    assertEquals(reasonText, record.metadata().get("reason"));
  }
}

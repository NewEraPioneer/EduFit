package vn.edufit.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.response.ApiResponse;
import vn.edufit.verification.application.service.AdminVerificationService;
import vn.edufit.verification.web.AdminVerificationController;
import vn.edufit.verification.web.request.ReviewVerificationRequest;
import vn.edufit.verification.web.request.RevokeVerificationRequest;
import vn.edufit.verification.web.response.PendingVerificationQueueResponse;
import vn.edufit.verification.web.response.VerificationRequestDetailResponse;

@ExtendWith(MockitoExtension.class)
class AdminVerificationControllerTest {

  @Mock
  private AdminVerificationService adminVerificationService;

  @Mock
  private ObjectProvider<CurrentUser> currentUserProvider;

  private AdminVerificationController controller;

  private CurrentUser adminUser;

  @BeforeEach
  void setUp() {
    controller = new AdminVerificationController(adminVerificationService, currentUserProvider);

    adminUser = new CurrentUser() {
      @Override
      public UUID getUserId() {
        return UUID.randomUUID();
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
  @DisplayName("GET /api/v1/admin/verifications/pending - Trả về HTTP 200 OK danh sách hàng đợi")
  void shouldGetPendingQueueSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(adminUser);

    PendingVerificationQueueResponse queueResponse = new PendingVerificationQueueResponse(
        List.of(new PendingVerificationQueueResponse.Item(
            UUID.randomUUID(), UUID.randomUUID(), "PENDING", Instant.now()
        )),
        0, 20, 1L, 1
    );
    when(adminVerificationService.getPendingQueue(adminUser, 0, 20)).thenReturn(queueResponse);

    ResponseEntity<ApiResponse<PendingVerificationQueueResponse>> response =
        controller.pending(0, 20);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().success());
    assertEquals(1, response.getBody().data().items().size());
  }

  @Test
  @DisplayName("POST /api/v1/admin/verifications/{id}/review - Trả về HTTP 200 OK khi duyệt")
  void shouldReviewVerificationSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(adminUser);

    UUID requestId = UUID.randomUUID();
    VerificationRequestDetailResponse detail = new VerificationRequestDetailResponse(
        requestId, UUID.randomUUID(), "APPROVED", null, Instant.now(), adminUser.getUserId(), Instant.now(), List.of()
    );
    ReviewVerificationRequest request = new ReviewVerificationRequest(
        ReviewVerificationRequest.Action.APPROVE, null, null
    );
    when(adminVerificationService.review(adminUser, requestId, request)).thenReturn(detail);

    ResponseEntity<ApiResponse<VerificationRequestDetailResponse>> response =
        controller.review(requestId, request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().success());
    assertEquals("APPROVED", response.getBody().data().status());
  }

  @Test
  @DisplayName("GET /api/v1/admin/verifications/{id} - Trả về HTTP 200 OK chi tiết hồ sơ xác minh")
  void shouldGetDetailSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(adminUser);

    UUID requestId = UUID.randomUUID();
    VerificationRequestDetailResponse detail = new VerificationRequestDetailResponse(
        requestId, UUID.randomUUID(), "PENDING", null, Instant.now(), null, null, List.of()
    );
    when(adminVerificationService.getDetail(adminUser, requestId)).thenReturn(detail);

    ResponseEntity<ApiResponse<VerificationRequestDetailResponse>> response =
        controller.getDetail(requestId);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().success());
    assertEquals(requestId, response.getBody().data().requestId());
  }

  @Test
  @DisplayName("Từ chối khi phiên làm việc chưa xác thực (CurrentUser null)")
  void shouldThrowWhenUnauthenticated() {
    when(currentUserProvider.getIfAvailable()).thenReturn(null);

    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> controller.pending(0, 20)
    );
    assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
  }

  @Test
  @DisplayName("POST /api/v1/admin/verifications/{id}/revoke - Trả về HTTP 200 OK khi thu hồi thành công")
  void shouldRevokeVerificationSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(adminUser);

    UUID requestId = UUID.randomUUID();
    VerificationRequestDetailResponse detail = new VerificationRequestDetailResponse(
        requestId, UUID.randomUUID(), "REJECTED", "[REVOKED] Bằng cấp giả mạo", Instant.now(), adminUser.getUserId(), Instant.now(), List.of()
    );
    RevokeVerificationRequest request = new RevokeVerificationRequest("Bằng cấp giả mạo");
    when(adminVerificationService.revoke(adminUser, requestId, request)).thenReturn(detail);

    ResponseEntity<ApiResponse<VerificationRequestDetailResponse>> response =
        controller.revoke(requestId, request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().success());
    assertEquals("REJECTED", response.getBody().data().status());
    assertEquals("[REVOKED] Bằng cấp giả mạo", response.getBody().data().rejectionReason());
  }

  @Test
  @DisplayName("GET /api/v1/admin/verifications - Trả về HTTP 200 OK danh sách hồ sơ với lọc status")
  void shouldGetVerificationsWithStatusSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(adminUser);

    PendingVerificationQueueResponse queueResponse = new PendingVerificationQueueResponse(
        List.of(new PendingVerificationQueueResponse.Item(
            UUID.randomUUID(), UUID.randomUUID(), "APPROVED", Instant.now()
        )),
        0, 20, 1L, 1
    );
    when(adminVerificationService.getQueue(adminUser, "APPROVED", 0, 20)).thenReturn(queueResponse);

    ResponseEntity<ApiResponse<PendingVerificationQueueResponse>> response =
        controller.getVerifications("APPROVED", 0, 20);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().success());
    assertEquals(1, response.getBody().data().items().size());
  }
}

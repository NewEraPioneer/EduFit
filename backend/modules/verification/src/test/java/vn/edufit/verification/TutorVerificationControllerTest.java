package vn.edufit.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.response.ApiResponse;
import vn.edufit.verification.application.service.TutorVerificationService;
import vn.edufit.verification.infra.persistence.entity.Credential;
import vn.edufit.verification.web.TutorVerificationController;
import vn.edufit.verification.web.request.SubmitVerificationRequest;
import vn.edufit.verification.web.response.VerificationRequestDetailResponse;

@ExtendWith(MockitoExtension.class)
class TutorVerificationControllerTest {

  @Mock
  private TutorVerificationService tutorVerificationService;

  @Mock
  private ObjectProvider<CurrentUser> currentUserProvider;

  private TutorVerificationController controller;

  private CurrentUser tutorUser;

  @BeforeEach
  void setUp() {
    controller = new TutorVerificationController(tutorVerificationService, currentUserProvider);

    tutorUser = new CurrentUser() {
      @Override
      public UUID getUserId() {
        return UUID.randomUUID();
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

  @Test
  @DisplayName("POST /api/v1/verifications - Trả về HTTP 201 CREATED khi nộp hồ sơ thành công")
  void shouldSubmitVerificationSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(tutorUser);

    UUID requestId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    VerificationRequestDetailResponse detail = new VerificationRequestDetailResponse(
        requestId, tutorId, "PENDING", null, Instant.now(), null, null, List.of()
    );
    when(tutorVerificationService.submit(eq(tutorUser), any(), any())).thenReturn(detail);

    SubmitVerificationRequest request = new SubmitVerificationRequest(List.of(
        new SubmitVerificationRequest.CredentialItem(
            Credential.Type.DEGREE, "ĐH Bách Khoa", (short) 2024, "Bằng cử nhân", List.of(0), null
        )
    ));
    List<MultipartFile> files = List.of(
        new MockMultipartFile("files", "degree.pdf", "application/pdf", "content".getBytes())
    );

    ResponseEntity<ApiResponse<VerificationRequestDetailResponse>> response =
        controller.submit(request, files);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().success());
    assertEquals("PENDING", response.getBody().data().status());
  }

  @Test
  @DisplayName("GET /api/v1/verifications/my-request - Trả về HTTP 200 OK khi lấy yêu cầu gần nhất")
  void shouldGetMyRequestSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(tutorUser);

    UUID requestId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    VerificationRequestDetailResponse detail = new VerificationRequestDetailResponse(
        requestId, tutorId, "PENDING", null, Instant.now(), null, null, List.of()
    );
    when(tutorVerificationService.getMyLatestRequest(tutorUser)).thenReturn(detail);

    ResponseEntity<ApiResponse<VerificationRequestDetailResponse>> response = controller.myRequest();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().success());
    assertEquals(requestId, response.getBody().data().requestId());
  }

  @Test
  @DisplayName("GET /api/v1/verifications/credentials/files/{fileId} - Trả về HTTP 200 OK kèm byte resource")
  void shouldDownloadFileSuccessfully() {
    when(currentUserProvider.getIfAvailable()).thenReturn(tutorUser);

    UUID fileId = UUID.randomUUID();
    org.springframework.core.io.Resource resource = new org.springframework.core.io.ByteArrayResource("dummy content".getBytes());
    TutorVerificationService.CredentialFileDownload download = new TutorVerificationService.CredentialFileDownload(
        "degree.pdf", "application/pdf", 13L, resource
    );
    when(tutorVerificationService.getCredentialFile(tutorUser, fileId)).thenReturn(download);

    ResponseEntity<org.springframework.core.io.Resource> response = controller.downloadFile(fileId);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("application/pdf", response.getHeaders().getContentType().toString());
    assertEquals(13L, response.getHeaders().getContentLength());
    assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("inline"));
  }

  @Test
  @DisplayName("Từ chối khi phiên làm việc chưa xác thực (CurrentUser null)")
  void shouldThrowWhenUnauthenticated() {
    when(currentUserProvider.getIfAvailable()).thenReturn(null);

    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> controller.myRequest()
    );
    assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
  }
}

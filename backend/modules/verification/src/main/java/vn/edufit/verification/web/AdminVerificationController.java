package vn.edufit.verification.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.response.ApiResponse;
import vn.edufit.verification.application.service.AdminVerificationService;
import vn.edufit.verification.web.request.ReviewVerificationRequest;
import vn.edufit.verification.web.request.RevokeVerificationRequest;
import vn.edufit.verification.web.response.PendingVerificationQueueResponse;
import vn.edufit.verification.web.response.VerificationRequestDetailResponse;

@RestController
@RequestMapping("/api/v1/admin/verifications")
public class AdminVerificationController {

  private final AdminVerificationService adminVerificationService;
  private final ObjectProvider<CurrentUser> currentUserProvider;

  public AdminVerificationController(
      AdminVerificationService adminVerificationService,
      ObjectProvider<CurrentUser> currentUserProvider
  ) {
    this.adminVerificationService = adminVerificationService;
    this.currentUserProvider = currentUserProvider;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<PendingVerificationQueueResponse>> getVerifications(
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size
  ) {
    PendingVerificationQueueResponse response = adminVerificationService.getQueue(
        currentUser(),
        status,
        page,
        size
    );
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy danh sách hồ sơ xác minh thành công"));
  }

  @GetMapping("/pending")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<PendingVerificationQueueResponse>> pending(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size
  ) {
    PendingVerificationQueueResponse response = adminVerificationService.getPendingQueue(
        currentUser(),
        page,
        size
    );
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy hàng đợi hồ sơ xác minh thành công"));
  }

  @PostMapping("/{id}/review")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<VerificationRequestDetailResponse>> review(
      @PathVariable("id") UUID requestId,
      @Valid @RequestBody ReviewVerificationRequest request
  ) {
    VerificationRequestDetailResponse response = adminVerificationService.review(
        currentUser(),
        requestId,
        request
    );
    return ResponseEntity.ok(ApiResponse.success(response, "Duyệt hồ sơ xác minh thành công"));
  }

  @PostMapping("/{id}/revoke")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<VerificationRequestDetailResponse>> revoke(
      @PathVariable("id") UUID requestId,
      @Valid @RequestBody RevokeVerificationRequest request
  ) {
    VerificationRequestDetailResponse response = adminVerificationService.revoke(
        currentUser(),
        requestId,
        request
    );
    return ResponseEntity.ok(ApiResponse.success(response, "Thu hồi phê duyệt hồ sơ xác minh thành công"));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<VerificationRequestDetailResponse>> getDetail(
      @PathVariable("id") UUID requestId
  ) {
    VerificationRequestDetailResponse response = adminVerificationService.getDetail(
        currentUser(),
        requestId
    );
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy chi tiết hồ sơ xác minh thành công"));
  }

  private CurrentUser currentUser() {
    CurrentUser currentUser = currentUserProvider.getIfAvailable();
    if (currentUser == null || currentUser.getUserId() == null) {
      throw new InvalidOperationException(
          ErrorCode.UNAUTHORIZED,
          "Phiên làm việc chưa được xác thực hoặc đã hết hạn"
      );
    }
    return currentUser;
  }
}

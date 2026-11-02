package vn.edufit.iam.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.application.AdminUserService;
import vn.edufit.iam.web.dto.AdminUserItemResponse;
import vn.edufit.iam.web.dto.AdminUserListResponse;
import vn.edufit.iam.web.dto.AdminUserStatsResponse;
import vn.edufit.iam.web.dto.UpdateUserStatusRequest;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.response.ApiResponse;

/**
 * REST API Controller phụ trách Quản trị người dùng hệ thống (UC Quản trị viên).
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

  private final AdminUserService adminUserService;
  private final ObjectProvider<CurrentUser> currentUserProvider;

  public AdminUserController(
      AdminUserService adminUserService,
      ObjectProvider<CurrentUser> currentUserProvider
  ) {
    this.adminUserService = adminUserService;
    this.currentUserProvider = currentUserProvider;
  }

  /**
   * Lấy danh sách người dùng có hỗ trợ lọc theo vai trò, trạng thái và tìm kiếm từ khóa.
   */
  @GetMapping
  public ResponseEntity<ApiResponse<AdminUserListResponse>> getUsers(
      @RequestParam(name = "role", required = false) AccountRole role,
      @RequestParam(name = "status", required = false) AccountStatus status,
      @RequestParam(name = "search", required = false) String search,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size
  ) {
    AdminUserListResponse response = adminUserService.getUsers(role, status, search, page, size);
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy danh sách người dùng thành công"));
  }

  /**
   * Thống kê tổng số lượng người dùng theo từng nhóm và trạng thái.
   */
  @GetMapping("/stats")
  public ResponseEntity<ApiResponse<AdminUserStatsResponse>> getUserStats() {
    AdminUserStatsResponse stats = adminUserService.getUserStats();
    return ResponseEntity.ok(ApiResponse.success(stats, "Lấy thống kê người dùng thành công"));
  }

  /**
   * Lấy thông tin chi tiết một tài khoản người dùng theo ID.
   */
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<AdminUserItemResponse>> getUserById(@PathVariable("id") UUID id) {
    AdminUserItemResponse user = adminUserService.getUserById(id);
    return ResponseEntity.ok(ApiResponse.success(user, "Lấy thông tin người dùng thành công"));
  }

  /**
   * Cập nhật trạng thái hoạt động của tài khoản (ACTIVE / DEACTIVATED).
   */
  @PatchMapping("/{id}/status")
  public ResponseEntity<ApiResponse<AdminUserItemResponse>> updateStatus(
      @PathVariable("id") UUID id,
      @Valid @RequestBody UpdateUserStatusRequest request
  ) {
    CurrentUser currentAdmin = requireCurrentAdmin();
    AdminUserItemResponse updated = adminUserService.updateUserStatus(
        id,
        request.status(),
        currentAdmin.getUserId(),
        request.reason()
    );
    String message = updated.status() == AccountStatus.ACTIVE
        ? "Đã mở khóa tài khoản thành công"
        : "Đã khóa tài khoản người dùng thành công";
    return ResponseEntity.ok(ApiResponse.success(updated, message));
  }

  /**
   * Khóa nhanh tài khoản người dùng (DEACTIVATED).
   */
  @PostMapping("/{id}/lock")
  public ResponseEntity<ApiResponse<AdminUserItemResponse>> lockUser(
      @PathVariable("id") UUID id,
      @RequestParam(name = "reason", defaultValue = "Khóa tài khoản bởi Quản trị viên") String reason
  ) {
    CurrentUser currentAdmin = requireCurrentAdmin();
    AdminUserItemResponse updated = adminUserService.lockUser(id, currentAdmin.getUserId(), reason);
    return ResponseEntity.ok(ApiResponse.success(updated, "Đã khóa tài khoản người dùng thành công"));
  }

  /**
   * Mở khóa nhanh tài khoản người dùng (ACTIVE).
   */
  @PostMapping("/{id}/unlock")
  public ResponseEntity<ApiResponse<AdminUserItemResponse>> unlockUser(@PathVariable("id") UUID id) {
    CurrentUser currentAdmin = requireCurrentAdmin();
    AdminUserItemResponse updated = adminUserService.unlockUser(id, currentAdmin.getUserId());
    return ResponseEntity.ok(ApiResponse.success(updated, "Đã mở khóa tài khoản người dùng thành công"));
  }

  private CurrentUser requireCurrentAdmin() {
    CurrentUser currentUser = currentUserProvider.getIfAvailable();
    if (currentUser == null || currentUser.getUserId() == null) {
      throw new InvalidOperationException(ErrorCode.UNAUTHORIZED, "Phiên làm việc chưa được xác thực hoặc đã hết hạn");
    }
    return currentUser;
  }
}

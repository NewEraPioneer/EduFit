package vn.edufit.web.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.shared.response.ApiResponse;
import vn.edufit.verification.api.VerificationFacade;
import vn.edufit.web.admin.dto.AdminDashboardStatsResponse;

/**
 * REST Controller cung cấp số liệu thống kê tổng thể hệ thống cho Bảng điều hành Quản trị viên (Admin Dashboard).
 *
 * <p>Theo quy chuẩn kiến trúc:
 * <ul>
 *   <li>Đặt tại module runner {@code backend/app} để tổng hợp số liệu từ các Public Facades.</li>
 *   <li>Chỉ truy cập thông qua {@link IamFacade}, {@link ConnectionFacade}, {@link SessionFacade}, {@link VerificationFacade}.</li>
 *   <li>Bảo vệ nghiêm ngặt bằng {@code ROLE_ADMIN}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

  private final IamFacade iamFacade;
  private final ConnectionFacade connectionFacade;
  private final SessionFacade sessionFacade;
  private final VerificationFacade verificationFacade;

  public AdminDashboardController(
      IamFacade iamFacade,
      ConnectionFacade connectionFacade,
      SessionFacade sessionFacade,
      VerificationFacade verificationFacade
  ) {
    this.iamFacade = iamFacade;
    this.connectionFacade = connectionFacade;
    this.sessionFacade = sessionFacade;
    this.verificationFacade = verificationFacade;
  }

  @GetMapping("/stats")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<AdminDashboardStatsResponse>> getDashboardStats() {
    long totalTutors = iamFacade.countUsersByRole(AccountRole.TUTOR);
    long totalStudents = iamFacade.countUsersByRole(AccountRole.STUDENT);
    long totalParents = iamFacade.countUsersByRole(AccountRole.PARENT);
    long totalUsers = totalTutors + totalStudents + totalParents; // Không tính quản trị viên (ADMIN) theo yêu cầu nghiệp vụ
    long activeClasses = connectionFacade.countActiveClasses();
    long completedSessions = sessionFacade.countCompletedSessions();
    long pendingKyc = verificationFacade.countPendingRequests();

    AdminDashboardStatsResponse stats = new AdminDashboardStatsResponse(
        totalUsers,
        totalTutors,
        totalStudents,
        totalParents,
        activeClasses,
        completedSessions,
        pendingKyc
    );

    return ResponseEntity.ok(ApiResponse.success(stats, "Lấy số liệu thống kê quản trị thành công"));
  }
}

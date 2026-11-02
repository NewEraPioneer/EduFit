package vn.edufit.progress.web;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.progress.application.service.ProgressDashboardService;
import vn.edufit.progress.web.response.ProgressDashboardResponses.ParentDashboardResponse;
import vn.edufit.progress.web.response.ProgressDashboardResponses.StudentDashboardResponse;
import vn.edufit.progress.web.response.ProgressDashboardResponses.TeachingHistoryItem;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.response.ApiResponse;

/**
 * REST Controller cho Dashboard Tiến độ học tập & Lịch sử giảng dạy (/api/v1/progress).
 */
@RestController
@RequestMapping("/api/v1/progress")
@RequiredArgsConstructor
public class ProgressDashboardController {

  private final ProgressDashboardService dashboardService;
  private final ObjectProvider<CurrentUser> currentUserProvider;

  @GetMapping("/dashboard/student/{studentId}")
  public ResponseEntity<ApiResponse<StudentDashboardResponse>> getStudentDashboard(
      @PathVariable("studentId") UUID studentId,
      @RequestParam(value = "classId", required = false) UUID classId,
      @RequestParam(value = "range", defaultValue = "30D") String range
  ) {
    StudentDashboardResponse response = StudentDashboardResponse.from(
        dashboardService.getStudentDashboard(currentUser(), studentId, classId, range)
    );
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy dữ liệu dashboard tiến độ học tập thành công"));
  }

  @GetMapping("/dashboard/parent")
  public ResponseEntity<ApiResponse<ParentDashboardResponse>> getParentDashboard() {
    ParentDashboardResponse response = ParentDashboardResponse.from(
        dashboardService.getParentDashboard(currentUser())
    );
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy dữ liệu dashboard phụ huynh thành công"));
  }

  @GetMapping("/teaching-history")
  public ResponseEntity<ApiResponse<Page<TeachingHistoryItem>>> getTeachingHistory(
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "10") int size,
      @RequestParam(value = "studentName", required = false) String studentName,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "from", required = false) Instant from,
      @RequestParam(value = "to", required = false) Instant to
  ) {
    Page<TeachingHistoryItem> response = dashboardService.getTeachingHistory(
        currentUser(), page, size, studentName, status, from, to
    ).map(TeachingHistoryItem::from);
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy lịch sử giảng dạy thành công"));
  }

  private CurrentUser currentUser() {
    CurrentUser user = currentUserProvider.getIfAvailable();
    if (user == null || user.getUserId() == null) {
      throw new InvalidOperationException(ErrorCode.UNAUTHORIZED, "Phiên làm việc chưa được xác thực hoặc đã hết hạn");
    }
    return user;
  }
}

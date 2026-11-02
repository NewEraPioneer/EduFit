package vn.edufit.progress.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.progress.application.dto.PeriodReportView;
import vn.edufit.progress.application.service.PeriodReportService;
import vn.edufit.progress.web.request.PeriodReportSummaryRequest;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.response.ApiResponse;

/** REST Controller cho báo cáo kỳ 30 ngày. */
@RestController
@RequestMapping("/api/v1/progress")
@RequiredArgsConstructor
public class PeriodReportController {

  private final PeriodReportService reportService;
  private final ObjectProvider<CurrentUser> currentUserProvider;

  @GetMapping("/classes/{classId}/reports")
  public ResponseEntity<ApiResponse<List<PeriodReportView>>> list(@PathVariable("classId") UUID classId) {
    return ResponseEntity.ok(ApiResponse.success(
        reportService.listForClass(currentUser(), classId), "Lấy báo cáo kỳ thành công"));
  }

  @PutMapping("/reports/{reportId}/summary")
  public ResponseEntity<ApiResponse<PeriodReportView>> editSummary(
      @PathVariable("reportId") UUID reportId,
      @Valid @RequestBody PeriodReportSummaryRequest req
  ) {
    return ResponseEntity.ok(ApiResponse.success(
        reportService.editSummary(currentUser(), reportId, req.summary(), req.version()), "Đã lưu câu tổng kết"));
  }

  /** Gửi ngay kèm câu tổng kết trong body ({@code summary} null = giữ câu đã lưu). */
  @PostMapping("/reports/{reportId}/send")
  public ResponseEntity<ApiResponse<PeriodReportView>> send(
      @PathVariable("reportId") UUID reportId,
      @Valid @RequestBody PeriodReportSummaryRequest req
  ) {
    return ResponseEntity.ok(ApiResponse.success(
        reportService.sendNow(currentUser(), reportId, req.summary(), req.version()), "Đã gửi báo cáo kỳ"));
  }

  private CurrentUser currentUser() {
    return ProgressWebSupport.currentUser(currentUserProvider);
  }
}

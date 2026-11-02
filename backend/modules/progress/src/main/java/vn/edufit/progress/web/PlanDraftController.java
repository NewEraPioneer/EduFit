package vn.edufit.progress.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.progress.application.dto.PlanDraftView;
import vn.edufit.progress.application.service.PlanDraftService;
import vn.edufit.progress.web.request.PlanDraftRequest;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.response.ApiResponse;

/**
 * Bản nháp lộ trình bằng AI: POST /api/v1/progress/plans/draft. Luôn trả 200; không có bản nháp thì
 * {@code unavailableReason} cho biết lý do (AI lỗi, hết lượt, đầu ra sai định dạng).
 */
@RestController
@RequestMapping("/api/v1/progress/plans/draft")
@RequiredArgsConstructor
public class PlanDraftController {

  private final PlanDraftService draftService;
  private final ObjectProvider<CurrentUser> currentUserProvider;

  @PostMapping
  public ResponseEntity<ApiResponse<PlanDraftView>> draft(@Valid @RequestBody PlanDraftRequest req) {
    PlanDraftView draft = draftService.draft(ProgressWebSupport.currentUser(currentUserProvider), req.classId());
    return ResponseEntity.ok(ApiResponse.success(
        draft, draft.milestones().isEmpty() ? draft.unavailableReason() : "Đã tạo bản nháp lộ trình"));
  }
}

package vn.edufit.progress.web;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.progress.application.service.LearningPlanService;
import vn.edufit.progress.application.service.SessionNoteService;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.web.request.CreatePlanRequest;
import vn.edufit.progress.web.request.UpdateMilestoneStatusRequest;
import vn.edufit.progress.web.request.UpdatePlanRequest;
import vn.edufit.progress.web.response.PlanResponse;
import vn.edufit.progress.web.response.SessionNoteTimelineResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.response.ApiResponse;

/**
 * REST Controller cho Kế hoạch học tập (/api/v1/progress).
 */
@RestController
@RequestMapping("/api/v1/progress")
@RequiredArgsConstructor
public class ProgressPlanController {

  private final LearningPlanService planService;
  private final SessionNoteService sessionNoteService;
  private final ObjectProvider<CurrentUser> currentUserProvider;

  @PostMapping("/plans")
  public ResponseEntity<ApiResponse<PlanResponse>> createPlan(
      @Valid @RequestBody CreatePlanRequest req
  ) {
    LearningPlan plan = planService.createPlan(currentUser(), req.toCommand());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(PlanResponse.from(plan), "Tạo kế hoạch học tập thành công"));
  }

  @GetMapping("/plans/{classId}")
  public ResponseEntity<ApiResponse<PlanResponse>> getPlanByClassId(
      @PathVariable("classId") UUID classId
  ) {
    LearningPlan plan = planService.getPlanByClassId(currentUser(), classId);
    return ResponseEntity.ok(ApiResponse.success(PlanResponse.from(plan), "Lấy kế hoạch học tập thành công"));
  }

  @PutMapping("/plans/{planId}")
  public ResponseEntity<ApiResponse<PlanResponse>> updatePlan(
      @PathVariable("planId") UUID planId,
      @Valid @RequestBody UpdatePlanRequest req
  ) {
    LearningPlan plan = planService.updatePlan(currentUser(), planId, req.toCommand());
    return ResponseEntity.ok(ApiResponse.success(PlanResponse.from(plan), "Cập nhật kế hoạch học tập thành công"));
  }

  @PatchMapping("/milestones/{milestoneId}/status")
  public ResponseEntity<ApiResponse<PlanResponse>> updateMilestoneStatus(
      @PathVariable("milestoneId") UUID milestoneId,
      @Valid @RequestBody UpdateMilestoneStatusRequest req
  ) {
    LearningPlan plan = planService.updateMilestoneStatus(currentUser(), milestoneId, req.toCommand());
    return ResponseEntity.ok(ApiResponse.success(PlanResponse.from(plan), "Cập nhật trạng thái cột mốc thành công"));
  }

  @GetMapping("/classes/{classId}/notes")
  public ResponseEntity<ApiResponse<Page<SessionNoteTimelineResponse>>> getClassNotes(
      @PathVariable("classId") UUID classId,
      @RequestParam(value = "from", required = false) Instant from,
      @RequestParam(value = "to", required = false) Instant to,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size
  ) {
    Page<SessionNoteTimelineResponse> result = sessionNoteService.getClassNotesTimeline(
        currentUser(), classId, from, to, page, size
    ).map(SessionNoteTimelineResponse::from);
    return ResponseEntity.ok(ApiResponse.success(result, "Lấy danh sách ghi chú buổi học thành công"));
  }

  private CurrentUser currentUser() {
    CurrentUser user = currentUserProvider.getIfAvailable();
    if (user == null || user.getUserId() == null) {
      throw new InvalidOperationException(ErrorCode.UNAUTHORIZED, "Phiên làm việc chưa được xác thực hoặc đã hết hạn");
    }
    return user;
  }
}

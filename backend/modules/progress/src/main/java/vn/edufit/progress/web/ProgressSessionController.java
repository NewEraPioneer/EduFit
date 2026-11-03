package vn.edufit.progress.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.progress.application.dto.SessionFeedbackView;
import vn.edufit.progress.application.dto.SessionMilestonesView;
import vn.edufit.progress.application.service.SessionFeedbackService;
import vn.edufit.progress.application.service.SessionMilestonePlanningService;
import vn.edufit.progress.application.service.SessionSelfRatingService;
import vn.edufit.progress.web.request.PlanSessionMilestonesRequest;
import vn.edufit.progress.web.request.SelfRatingRequest;
import vn.edufit.progress.web.request.SubmitSessionFeedbackRequest;
import vn.edufit.progress.web.response.SelfRatingResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.response.ApiResponse;

/**
 * REST Controller cho dữ liệu tiến độ gắn với buổi học (/api/v1/progress/sessions).
 * Module Scheduling không biết cột mốc; UI lịch và màn phản hồi gọi các endpoint này.
 */
@RestController
@RequestMapping("/api/v1/progress/sessions")
@RequiredArgsConstructor
public class ProgressSessionController {

  private final SessionFeedbackService sessionFeedbackService;
  private final SessionSelfRatingService selfRatingService;
  private final SessionMilestonePlanningService planningService;
  private final ObjectProvider<CurrentUser> currentUserProvider;

  /** Phản hồi 10 giây của gia sư (mức tự lập theo cột mốc được dạy, việc về nhà, thái độ, lời nhắn riêng). */
  @PostMapping("/{sessionId}/feedback")
  public ResponseEntity<ApiResponse<SessionFeedbackView>> submitFeedback(
      @PathVariable("sessionId") UUID sessionId,
      @Valid @RequestBody SubmitSessionFeedbackRequest req
  ) {
    SessionFeedbackView feedback = sessionFeedbackService.submit(currentUser(), sessionId, req.toCommand());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(feedback, "Ghi phản hồi buổi học thành công"));
  }

  /** Cảm nhận 1 chạm của học sinh; chọn lại thì ghi đè. */
  @PutMapping("/{sessionId}/self-rating")
  public ResponseEntity<ApiResponse<SelfRatingResponse>> rate(
      @PathVariable("sessionId") UUID sessionId,
      @Valid @RequestBody SelfRatingRequest req
  ) {
    int rating = selfRatingService.rate(currentUser(), sessionId, req.rating());
    return ResponseEntity.ok(ApiResponse.success(new SelfRatingResponse(sessionId, rating), "Đã ghi cảm nhận buổi học"));
  }

  /** Gắn cột mốc dự kiến (tùy chọn) cho buổi học chưa diễn ra; danh sách rỗng = bỏ dự kiến. */
  @PutMapping("/{sessionId}/planned-milestones")
  public ResponseEntity<ApiResponse<SessionMilestonesView>> planMilestones(
      @PathVariable("sessionId") UUID sessionId,
      @Valid @RequestBody PlanSessionMilestonesRequest req
  ) {
    SessionMilestonesView view = planningService.planMilestones(currentUser(), sessionId, req.milestoneIds());
    return ResponseEntity.ok(ApiResponse.success(view, "Đã cập nhật cột mốc dự kiến của buổi học"));
  }

  /** Cột mốc hiệu lực của các buổi học trên lịch (thực tế thắng dự kiến). */
  @GetMapping("/milestones")
  public ResponseEntity<ApiResponse<List<SessionMilestonesView>>> findSessionMilestones(
      @RequestParam("sessionIds") List<UUID> sessionIds
  ) {
    return ResponseEntity.ok(ApiResponse.success(
        planningService.findSessionMilestones(currentUser(), sessionIds), "Lấy cột mốc của buổi học thành công"));
  }

  private CurrentUser currentUser() {
    return ProgressWebSupport.currentUser(currentUserProvider);
  }
}

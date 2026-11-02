package vn.edufit.review.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.review.application.service.ReviewService;
import vn.edufit.review.web.request.CreateReviewRequest;
import vn.edufit.review.web.request.UpdateReviewRequest;
import vn.edufit.review.web.response.ReviewResponse;
import vn.edufit.review.web.response.TutorRatingSummaryResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.response.ApiResponse;

/**
 * REST API Controller quản lý đánh giá gia sư (UC6.1, UC6.2, FR-28).
 */
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

  private final ReviewService reviewService;
  private final ObjectProvider<CurrentUser> currentUserProvider;

  public ReviewController(
      ReviewService reviewService,
      ObjectProvider<CurrentUser> currentUserProvider
  ) {
    this.reviewService = reviewService;
    this.currentUserProvider = currentUserProvider;
  }

  /**
   * Tạo đánh giá mới cho gia sư (UC6.1).
   * Phân quyền: Học sinh (STUDENT) hoặc Phụ huynh (PARENT).
   */
  @PostMapping
  public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
      @Valid @RequestBody CreateReviewRequest request
  ) {
    CurrentUser currentUser = getCurrentUser();
    ReviewResponse response = reviewService.createReview(currentUser, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "Gửi đánh giá gia sư thành công"));
  }

  /**
   * Chỉnh sửa đánh giá của chính mình (BR-57, BR-58).
   * Phân quyền: Tác giả tạo đánh giá.
   */
  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<ReviewResponse>> updateReview(
      @PathVariable("id") UUID id,
      @Valid @RequestBody UpdateReviewRequest request
  ) {
    CurrentUser currentUser = getCurrentUser();
    ReviewResponse response = reviewService.updateReview(currentUser, id, request);
    return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật đánh giá thành công"));
  }

  /**
   * Lấy đánh giá của chính người dùng hiện tại đối với gia sư (nếu có).
   * Phân quyền: Cần đăng nhập (STUDENT hoặc PARENT).
   */
  @GetMapping("/tutors/{tutorId}/my-review")
  public ResponseEntity<ApiResponse<ReviewResponse>> getMyReview(
      @PathVariable("tutorId") UUID tutorId
  ) {
    CurrentUser currentUser = getCurrentUser();
    ReviewResponse response = reviewService.getMyReview(currentUser, tutorId);
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy đánh giá của bạn thành công"));
  }

  /**
   * Lấy danh sách đánh giá của gia sư với phân trang (UC6.2).
   * Phân quyền: Public (tất cả người dùng).
   */
  @GetMapping("/tutors/{tutorId}")
  public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getTutorReviews(
      @PathVariable("tutorId") UUID tutorId,
      @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
  ) {
    CurrentUser currentUser = currentUserProvider.getIfAvailable();
    Page<ReviewResponse> response = reviewService.getTutorReviews(tutorId, pageable, currentUser);
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy danh sách đánh giá thành công"));
  }

  /**
   * Lấy thống kê điểm đánh giá và phân bổ sao của gia sư.
   * Phân quyền: Public.
   */
  @GetMapping("/tutors/{tutorId}/summary")
  public ResponseEntity<ApiResponse<TutorRatingSummaryResponse>> getTutorRatingSummary(
      @PathVariable("tutorId") UUID tutorId
  ) {
    TutorRatingSummaryResponse response = reviewService.getTutorRatingSummary(tutorId);
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy tóm tắt điểm đánh giá thành công"));
  }

  private CurrentUser getCurrentUser() {
    CurrentUser user = currentUserProvider.getIfAvailable();
    if (user == null || user.getUserId() == null) {
      throw new ForbiddenOperationException("Yêu cầu cần được xác thực hoặc phiên đăng nhập đã hết hạn.");
    }
    return user;
  }
}


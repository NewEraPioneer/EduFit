package vn.edufit.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.edufit.review.application.service.ReviewService;
import vn.edufit.review.web.ReviewController;
import vn.edufit.review.web.request.CreateReviewRequest;
import vn.edufit.review.web.request.UpdateReviewRequest;
import vn.edufit.review.web.response.ReviewResponse;
import vn.edufit.review.web.response.TutorRatingSummaryResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.response.ApiResponse;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

  @Mock
  private ReviewService reviewService;

  @Mock
  private ObjectProvider<CurrentUser> currentUserProvider;

  private ReviewController reviewController;

  private CurrentUser testUser;
  private UUID testUserId;
  private UUID testTutorId;
  private UUID testStudentId;

  private record TestCurrentUser(UUID userId, String email, Set<String> roles) implements CurrentUser {
    @Override public UUID getUserId() { return userId; }
    @Override public String getEmail() { return email; }
    @Override public Set<String> getRoles() { return roles; }
    @Override public boolean hasRole(String role) { return roles != null && roles.contains(role); }
  }

  @BeforeEach
  void setUp() {
    reviewController = new ReviewController(reviewService, currentUserProvider);
    testUserId = UUID.randomUUID();
    testTutorId = UUID.randomUUID();
    testStudentId = UUID.randomUUID();
    testUser = new TestCurrentUser(testUserId, "test@edufit.vn", Set.of("STUDENT"));
  }

  @Test
  @DisplayName("POST /api/v1/reviews gọi ReviewService và trả về HTTP 201 Created")
  void createReview_returnsCreated() {
    CreateReviewRequest request = new CreateReviewRequest(
        testTutorId,
        testStudentId,
        5,
        "Gia sư dạy rất nhiệt tình."
    );

    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    ReviewResponse mockResponse = new ReviewResponse(
        UUID.randomUUID(),
        testTutorId,
        testStudentId,
        "Nguyễn Văn A",
        "STUDENT",
        5,
        request.comment(),
        false,
        1,
        true,
        Instant.now(),
        Instant.now()
    );
    when(reviewService.createReview(eq(testUser), any(CreateReviewRequest.class))).thenReturn(mockResponse);

    ResponseEntity<ApiResponse<ReviewResponse>> responseEntity = reviewController.createReview(request);

    assertNotNull(responseEntity);
    assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
    assertNotNull(responseEntity.getBody());
    assertEquals(5, responseEntity.getBody().data().rating());
    assertTrue(responseEntity.getBody().data().isAuthor());
  }

  @Test
  @DisplayName("PUT /api/v1/reviews/{id} gọi ReviewService và trả về HTTP 200 OK")
  void updateReview_returnsOk() {
    UUID reviewId = UUID.randomUUID();
    UpdateReviewRequest request = new UpdateReviewRequest(4, "Cập nhật nhận xét chi tiết hơn.");

    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    ReviewResponse mockResponse = new ReviewResponse(
        reviewId,
        testTutorId,
        testStudentId,
        "Nguyễn Văn A",
        "STUDENT",
        4,
        request.comment(),
        true,
        1,
        true,
        Instant.now(),
        Instant.now()
    );
    when(reviewService.updateReview(eq(testUser), eq(reviewId), any(UpdateReviewRequest.class)))
        .thenReturn(mockResponse);

    ResponseEntity<ApiResponse<ReviewResponse>> responseEntity = reviewController.updateReview(reviewId, request);

    assertNotNull(responseEntity);
    assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    assertNotNull(responseEntity.getBody());
    assertEquals(4, responseEntity.getBody().data().rating());
  }

  @Test
  @DisplayName("GET /api/v1/reviews/tutors/{tutorId}/my-review trả về đánh giá của người dùng hiện tại")
  void getMyReview_returnsReview() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    ReviewResponse mockResponse = new ReviewResponse(
        UUID.randomUUID(),
        testTutorId,
        testStudentId,
        "Nguyễn Văn A",
        "STUDENT",
        5,
        "Đánh giá của tôi",
        false,
        1,
        true,
        Instant.now(),
        Instant.now()
    );
    when(reviewService.getMyReview(testUser, testTutorId)).thenReturn(mockResponse);

    ResponseEntity<ApiResponse<ReviewResponse>> responseEntity = reviewController.getMyReview(testTutorId);

    assertNotNull(responseEntity);
    assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    assertNotNull(responseEntity.getBody());
    assertEquals(5, responseEntity.getBody().data().rating());
    assertTrue(responseEntity.getBody().data().isAuthor());
  }

  @Test
  @DisplayName("GET /api/v1/reviews/tutors/{tutorId} trả về danh sách đánh giá phân trang")
  void getTutorReviews_returnsPage() {
    Pageable pageable = PageRequest.of(0, 10);
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);

    ReviewResponse reviewResponse = new ReviewResponse(
        UUID.randomUUID(),
        testTutorId,
        testStudentId,
        "Nguyễn Văn B",
        "STUDENT",
        5,
        "Tuyệt vời",
        false,
        1,
        false,
        Instant.now(),
        Instant.now()
    );
    Page<ReviewResponse> page = new PageImpl<>(List.of(reviewResponse), pageable, 1);
    when(reviewService.getTutorReviews(eq(testTutorId), eq(pageable), eq(testUser))).thenReturn(page);

    ResponseEntity<ApiResponse<Page<ReviewResponse>>> responseEntity =
        reviewController.getTutorReviews(testTutorId, pageable);

    assertNotNull(responseEntity);
    assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    assertNotNull(responseEntity.getBody());
    assertEquals(1, responseEntity.getBody().data().getTotalElements());
  }

  @Test
  @DisplayName("GET /api/v1/reviews/tutors/{tutorId}/summary trả về tóm tắt thống kê điểm")
  void getTutorRatingSummary_returnsSummary() {
    TutorRatingSummaryResponse mockSummary = new TutorRatingSummaryResponse(
        testTutorId,
        4.9,
        8,
        Map.of(5, 7L, 4, 1L, 3, 0L, 2, 0L, 1, 0L)
    );
    when(reviewService.getTutorRatingSummary(testTutorId)).thenReturn(mockSummary);

    ResponseEntity<ApiResponse<TutorRatingSummaryResponse>> responseEntity =
        reviewController.getTutorRatingSummary(testTutorId);

    assertNotNull(responseEntity);
    assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    assertEquals(4.9, responseEntity.getBody().data().averageRating());
  }

  @Test
  @DisplayName("Gọi endpoint yêu cầu đăng nhập khi chưa có CurrentUser sẽ ném ForbiddenOperationException")
  void createReview_unauthenticated_throwsException() {
    CreateReviewRequest request = new CreateReviewRequest(
        testTutorId,
        testStudentId,
        5,
        "Gia sư dạy tốt."
    );

    when(currentUserProvider.getIfAvailable()).thenReturn(null);

    assertThrows(ForbiddenOperationException.class, () ->
        reviewController.createReview(request));
  }
}

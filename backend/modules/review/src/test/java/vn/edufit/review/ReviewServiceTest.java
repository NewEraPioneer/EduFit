package vn.edufit.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.review.api.event.ReviewSubmittedEvent;
import vn.edufit.review.api.event.ReviewUpdatedEvent;
import vn.edufit.review.application.service.ReviewService;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.review.infra.persistence.entity.ReviewEntity;
import vn.edufit.review.infra.persistence.repository.ReviewRepository;
import vn.edufit.review.web.request.CreateReviewRequest;
import vn.edufit.review.web.request.UpdateReviewRequest;
import vn.edufit.review.web.response.ReviewResponse;
import vn.edufit.review.web.response.TutorRatingSummaryResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

  @Mock
  private ReviewRepository reviewRepository;

  @Mock
  private ProfileFacade profileFacade;

  @Mock
  private ConnectionFacade connectionFacade;

  @Mock
  private SessionFacade sessionFacade;

  @Mock
  private IamFacade iamFacade;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private ReviewService reviewService;

  private UUID testTutorId;
  private UUID testStudentId;
  private UUID testUserId;
  private CurrentUser studentUser;
  private CurrentUser parentUser;

  private record TestCurrentUser(UUID userId, String email, Set<String> roles) implements CurrentUser {
    @Override public UUID getUserId() { return userId; }
    @Override public String getEmail() { return email; }
    @Override public Set<String> getRoles() { return roles; }
    @Override public boolean hasRole(String role) { return roles != null && roles.contains(role); }
  }

  private TutorSummaryDto createDummyTutor(UUID tutorId) {
    return new TutorSummaryDto(tutorId, UUID.randomUUID(), "Gia sư A", "Gia sư Toán", "Tiểu sử mẫu", "ONLINE", null, false, null, "Hà Nội", (short) 2, "Phương pháp sư phạm", "VERIFIED", Instant.now(), BigDecimal.valueOf(4.8), 10);
  }

  private UserSummaryView createDummyUser(UUID id, String fullName, AccountRole role) {
    return new UserSummaryView(id, "user@edufit.vn", fullName, role, AccountStatus.ACTIVE);
  }

  @BeforeEach
  void setUp() {
    reviewService = new ReviewService(reviewRepository, profileFacade, connectionFacade, sessionFacade, iamFacade, eventPublisher);
    lenient().when(sessionFacade.countCompletedSessionsBetween(any(), any())).thenReturn(3);

    testTutorId = UUID.randomUUID();
    testStudentId = UUID.randomUUID();
    testUserId = UUID.randomUUID();

    studentUser = new TestCurrentUser(testUserId, "student@edufit.vn", Set.of("STUDENT"));
    parentUser = new TestCurrentUser(UUID.randomUUID(), "parent@edufit.vn", Set.of("PARENT"));
  }

  @Test
  void studentMayReviewEndedClassAfterRealLessons() {
    when(profileFacade.findTutorById(testTutorId)).thenReturn(Optional.of(createDummyTutor(testTutorId)));
    when(profileFacade.findStudentById(testStudentId)).thenReturn(Optional.of(
        new StudentSummaryDto(testStudentId, testUserId, 1, null, null, true)));
    when(profileFacade.getStudentIdByUserId(testUserId)).thenReturn(testStudentId);
    when(connectionFacade.findClassesByStudent(testStudentId)).thenReturn(List.of(new ConnectionFacade.ClassDetailView(
        UUID.randomUUID(), testStudentId, testTutorId, UUID.randomUUID(), "ENDED", Instant.now().minusSeconds(86400), Instant.now(), 250000)));
    when(reviewRepository.saveAndFlush(any())).thenAnswer(call -> {
      ReviewEntity row = call.getArgument(0); row.setReviewId(UUID.randomUUID()); return row;
    });
    var result = reviewService.createReview(studentUser,
        new CreateReviewRequest(testTutorId, testStudentId, 5, "Hoàn thành khóa học và tiến bộ rõ rệt."));
    assertEquals(5, result.rating());
  }

  @Test
  @DisplayName("Học sinh tạo đánh giá gia sư thành công (UC6.1, FR-28, BR-59)")
  void createReview_success_asStudent() {
    CreateReviewRequest request = new CreateReviewRequest(
        testTutorId,
        testStudentId,
        5,
        "Gia sư giảng dạy rất nhiệt tình và dễ hiểu."
    );

    TutorSummaryDto tutorDto = createDummyTutor(testTutorId);
    StudentSummaryDto studentDto = new StudentSummaryDto(testStudentId, testUserId, 1, null, null, true);

    when(profileFacade.findTutorById(testTutorId)).thenReturn(Optional.of(tutorDto));
    when(profileFacade.findStudentById(testStudentId)).thenReturn(Optional.of(studentDto));
    when(profileFacade.getStudentIdByUserId(testUserId)).thenReturn(testStudentId);
    when(connectionFacade.findActiveClass(testStudentId, testTutorId))
        .thenReturn(Optional.of(new ConnectionFacade.ActiveClassView(UUID.randomUUID(), testStudentId, testTutorId, UUID.randomUUID())));
    when(reviewRepository.existsByTutorIdAndStudentId(testTutorId, testStudentId)).thenReturn(false);

    ReviewEntity savedEntity = new ReviewEntity(testTutorId, testStudentId, testUserId, "STUDENT", 5, request.comment(), 1);
    savedEntity.setReviewId(UUID.randomUUID());
    when(reviewRepository.saveAndFlush(any(ReviewEntity.class))).thenReturn(savedEntity);
    when(iamFacade.findUserSummaryById(testUserId))
        .thenReturn(Optional.of(createDummyUser(testUserId, "Nguyễn Văn Học Sinh", AccountRole.STUDENT)));

    when(reviewRepository.findAverageRatingByTutorId(testTutorId)).thenReturn(5.0);
    when(reviewRepository.countByTutorId(testTutorId)).thenReturn(1);

    ReviewResponse response = reviewService.createReview(studentUser, request);

    assertNotNull(response);
    assertEquals(5, response.rating());
    assertEquals("Nguyễn Văn Học Sinh", response.reviewerName());
    assertEquals("STUDENT", response.reviewerType());
    assertTrue(response.isAuthor());
    assertFalse(response.isEdited());

    // Verify rating stats synchronized with tutor profile (FR-29)
    verify(profileFacade).updateTutorRatingStats(testTutorId, 5.0, 1);

    // Verify ReviewSubmittedEvent published
    ArgumentCaptor<ReviewSubmittedEvent> eventCaptor = ArgumentCaptor.forClass(ReviewSubmittedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    ReviewSubmittedEvent publishedEvent = eventCaptor.getValue();
    assertEquals(testTutorId, publishedEvent.tutorId());
    assertEquals(5, publishedEvent.rating());
  }

  @Test
  @DisplayName("Phụ huynh có liên kết tạo đánh giá thay cho con thành công")
  void createReview_success_asParent() {
    CreateReviewRequest request = new CreateReviewRequest(
        testTutorId,
        testStudentId,
        4,
        "Thầy dạy tốt, con tôi tiến bộ rất nhiều."
    );

    TutorSummaryDto tutorDto = createDummyTutor(testTutorId);
    StudentSummaryDto studentDto = new StudentSummaryDto(testStudentId, UUID.randomUUID(), 1, null, null, true);

    when(profileFacade.findTutorById(testTutorId)).thenReturn(Optional.of(tutorDto));
    when(profileFacade.findStudentById(testStudentId)).thenReturn(Optional.of(studentDto));
    when(connectionFacade.hasConfirmedParentLink(parentUser.getUserId(), testStudentId)).thenReturn(true);
    when(connectionFacade.findActiveClass(testStudentId, testTutorId))
        .thenReturn(Optional.of(new ConnectionFacade.ActiveClassView(UUID.randomUUID(), testStudentId, testTutorId, UUID.randomUUID())));
    when(reviewRepository.existsByTutorIdAndStudentId(testTutorId, testStudentId)).thenReturn(false);

    ReviewEntity savedEntity = new ReviewEntity(testTutorId, testStudentId, parentUser.getUserId(), "PARENT", 4, request.comment(), 1);
    savedEntity.setReviewId(UUID.randomUUID());
    when(reviewRepository.saveAndFlush(any(ReviewEntity.class))).thenReturn(savedEntity);
    when(iamFacade.findUserSummaryById(parentUser.getUserId()))
        .thenReturn(Optional.of(createDummyUser(parentUser.getUserId(), "Trần Thị Phụ Huynh", AccountRole.PARENT)));

    ReviewResponse response = reviewService.createReview(parentUser, request);

    assertNotNull(response);
    assertEquals(4, response.rating());
    assertEquals("Trần Thị Phụ Huynh", response.reviewerName());
    assertEquals("PARENT", response.reviewerType());
    assertTrue(response.isAuthor());
  }

  @ParameterizedTest(name = "Kiểm tra giá trị biên rating: {0} (hợp lệ: {1}) - {2}")
  @CsvFileSource(resources = "/testdata/review-rating-validation.csv", numLinesToSkip = 1)
  @DisplayName("Kiểm thử giá trị biên (BVA) cho rating 1..5 sao từ CSV dataset")
  void createReview_ratingBoundaryValidation(int rating, boolean expectedValid, String description) {
    CreateReviewRequest request = new CreateReviewRequest(
        testTutorId,
        testStudentId,
        rating,
        "Đánh giá kiểm thử giá trị biên."
    );

    if (expectedValid) {
      TutorSummaryDto tutorDto = createDummyTutor(testTutorId);
      StudentSummaryDto studentDto = new StudentSummaryDto(testStudentId, testUserId, 1, null, null, true);

      when(profileFacade.findTutorById(testTutorId)).thenReturn(Optional.of(tutorDto));
      when(profileFacade.findStudentById(testStudentId)).thenReturn(Optional.of(studentDto));
      when(profileFacade.getStudentIdByUserId(testUserId)).thenReturn(testStudentId);
      when(connectionFacade.findActiveClass(testStudentId, testTutorId))
          .thenReturn(Optional.of(new ConnectionFacade.ActiveClassView(UUID.randomUUID(), testStudentId, testTutorId, UUID.randomUUID())));
      when(reviewRepository.existsByTutorIdAndStudentId(testTutorId, testStudentId)).thenReturn(false);

      ReviewEntity savedEntity = new ReviewEntity(testTutorId, testStudentId, testUserId, "STUDENT", rating, request.comment(), 1);
      savedEntity.setReviewId(UUID.randomUUID());
      when(reviewRepository.saveAndFlush(any(ReviewEntity.class))).thenReturn(savedEntity);
      when(iamFacade.findUserSummaryById(testUserId))
          .thenReturn(Optional.of(createDummyUser(testUserId, "Học sinh A", AccountRole.STUDENT)));

      ReviewResponse response = reviewService.createReview(studentUser, request);
      assertNotNull(response);
      assertEquals(rating, response.rating());
    } else {
      InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
          reviewService.createReview(studentUser, request));
      assertEquals(ErrorCode.INVALID_RATING, ex.getErrorCode());
    }
  }

  @Test
  @DisplayName("Đánh giá gia sư không tồn tại báo lỗi 404 NOT_FOUND")
  void createReview_tutorNotFound_throwsException() {
    CreateReviewRequest request = new CreateReviewRequest(
        testTutorId,
        testStudentId,
        5,
        "Đánh giá gia sư không tồn tại."
    );

    when(profileFacade.findTutorById(testTutorId)).thenReturn(Optional.empty());

    assertThrows(EntityNotFoundException.class, () ->
        reviewService.createReview(studentUser, request));
  }

  @Test
  @DisplayName("Đánh giá khi chưa có lớp học / chưa học buổi nào báo lỗi NOT_ENOUGH_SESSIONS")
  void createReview_noActiveClass_throwsNotEnoughSessions() {
    CreateReviewRequest request = new CreateReviewRequest(
        testTutorId,
        testStudentId,
        5,
        "Chưa học nhưng đã đánh giá."
    );

    TutorSummaryDto tutorDto = createDummyTutor(testTutorId);
    StudentSummaryDto studentDto = new StudentSummaryDto(testStudentId, testUserId, 1, null, null, true);

    when(profileFacade.findTutorById(testTutorId)).thenReturn(Optional.of(tutorDto));
    when(profileFacade.findStudentById(testStudentId)).thenReturn(Optional.of(studentDto));
    when(profileFacade.getStudentIdByUserId(testUserId)).thenReturn(testStudentId);
    when(connectionFacade.findActiveClass(testStudentId, testTutorId)).thenReturn(Optional.empty());

    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
        reviewService.createReview(studentUser, request));

    assertEquals(ErrorCode.NOT_ENOUGH_SESSIONS, ex.getErrorCode());
  }

  @Test
  @DisplayName("Tạo đánh giá thất bại khi chưa hoàn thành buổi học nào (BR-61)")
  void createReview_notEnoughCompletedSessions_throwsNotEnoughSessions() {
    CreateReviewRequest request = new CreateReviewRequest(
        testTutorId,
        testStudentId,
        5,
        "Chưa hoàn thành buổi học nào nhưng gửi đánh giá."
    );

    TutorSummaryDto tutorDto = createDummyTutor(testTutorId);
    StudentSummaryDto studentDto = new StudentSummaryDto(testStudentId, testUserId, 1, null, null, true);

    when(profileFacade.findTutorById(testTutorId)).thenReturn(Optional.of(tutorDto));
    when(profileFacade.findStudentById(testStudentId)).thenReturn(Optional.of(studentDto));
    when(profileFacade.getStudentIdByUserId(testUserId)).thenReturn(testStudentId);
    when(connectionFacade.findActiveClass(testStudentId, testTutorId))
        .thenReturn(Optional.of(new ConnectionFacade.ActiveClassView(UUID.randomUUID(), testStudentId, testTutorId, UUID.randomUUID())));
    when(reviewRepository.existsByTutorIdAndStudentId(testTutorId, testStudentId)).thenReturn(false);
    when(sessionFacade.countCompletedSessionsBetween(testTutorId, testStudentId)).thenReturn(0);

    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
        reviewService.createReview(studentUser, request));

    assertEquals(ErrorCode.NOT_ENOUGH_SESSIONS, ex.getErrorCode());
  }

  @Test
  @DisplayName("Học sinh đã đánh giá rồi không được đánh giá lại (BR-61, NFR-24)")
  void createReview_alreadyReviewed_throwsConflict() {
    CreateReviewRequest request = new CreateReviewRequest(
        testTutorId,
        testStudentId,
        5,
        "Đánh giá lần hai."
    );

    TutorSummaryDto tutorDto = createDummyTutor(testTutorId);
    StudentSummaryDto studentDto = new StudentSummaryDto(testStudentId, testUserId, 1, null, null, true);

    when(profileFacade.findTutorById(testTutorId)).thenReturn(Optional.of(tutorDto));
    when(profileFacade.findStudentById(testStudentId)).thenReturn(Optional.of(studentDto));
    when(profileFacade.getStudentIdByUserId(testUserId)).thenReturn(testStudentId);
    when(connectionFacade.findActiveClass(testStudentId, testTutorId))
        .thenReturn(Optional.of(new ConnectionFacade.ActiveClassView(UUID.randomUUID(), testStudentId, testTutorId, UUID.randomUUID())));
    when(reviewRepository.existsByTutorIdAndStudentId(testTutorId, testStudentId)).thenReturn(true);

    ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
        reviewService.createReview(studentUser, request));

    assertEquals(ErrorCode.REVIEW_ALREADY_EXISTS, ex.getErrorCode());
  }

  @Test
  @DisplayName("Tác giả chỉnh sửa đánh giá thành công và cập nhật thống kê hồ sơ (BR-57, FR-28, FR-29)")
  void updateReview_success() {
    UUID reviewId = UUID.randomUUID();
    UpdateReviewRequest request = new UpdateReviewRequest(4, "Cập nhật nhận xét sau một tháng học tập.");

    ReviewEntity existingReview = new ReviewEntity(testTutorId, testStudentId, testUserId, "STUDENT", 5, "Nhận xét cũ.", 1);
    existingReview.setReviewId(reviewId);

    when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(existingReview));
    when(reviewRepository.save(any(ReviewEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(iamFacade.findUserSummaryById(testUserId))
        .thenReturn(Optional.of(createDummyUser(testUserId, "Nguyễn Văn Học Sinh", AccountRole.STUDENT)));

    when(reviewRepository.findAverageRatingByTutorId(testTutorId)).thenReturn(4.0);
    when(reviewRepository.countByTutorId(testTutorId)).thenReturn(1);

    ReviewResponse response = reviewService.updateReview(studentUser, reviewId, request);

    assertNotNull(response);
    assertEquals(4, response.rating());
    assertEquals("Cập nhật nhận xét sau một tháng học tập.", response.comment());
    assertTrue(response.isEdited());
    assertTrue(response.isAuthor());

    // Verify rating stats synchronized with tutor profile
    verify(profileFacade).updateTutorRatingStats(testTutorId, 4.0, 1);

    // Verify ReviewUpdatedEvent published
    ArgumentCaptor<ReviewUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(ReviewUpdatedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    ReviewUpdatedEvent updatedEvent = eventCaptor.getValue();
    assertEquals(reviewId, updatedEvent.reviewId());
    assertEquals(testTutorId, updatedEvent.tutorId());
    assertEquals(4, updatedEvent.rating());
  }

  @Test
  @DisplayName("Người khác không thể chỉnh sửa đánh giá của tác giả (BR-57)")
  void updateReview_notAuthor_throwsForbidden() {
    UUID reviewId = UUID.randomUUID();
    UpdateReviewRequest request = new UpdateReviewRequest(4, "Cố tình sửa review của người khác.");

    UUID anotherUserId = UUID.randomUUID();
    ReviewEntity existingReview = new ReviewEntity(testTutorId, testStudentId, anotherUserId, "STUDENT", 5, "Nhận xét cũ.", 1);
    existingReview.setReviewId(reviewId);

    when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(existingReview));

    ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () ->
        reviewService.updateReview(studentUser, reviewId, request));

    assertEquals(ErrorCode.ONLY_AUTHOR_CAN_EDIT, ex.getErrorCode());
  }

  @Test
  @DisplayName("Lấy đánh giá của chính người dùng hiện tại (getMyReview) thành công")
  void getMyReview_success() {
    ReviewEntity existingReview = new ReviewEntity(testTutorId, testStudentId, testUserId, "STUDENT", 5, "Bài đánh giá của tôi", 1);
    existingReview.setReviewId(UUID.randomUUID());

    when(reviewRepository.findByTutorIdAndReviewerUserId(testTutorId, testUserId))
        .thenReturn(Optional.of(existingReview));
    when(iamFacade.findUserSummaryById(testUserId))
        .thenReturn(Optional.of(createDummyUser(testUserId, "Nguyễn Văn A", AccountRole.STUDENT)));

    ReviewResponse response = reviewService.getMyReview(studentUser, testTutorId);

    assertNotNull(response);
    assertEquals(5, response.rating());
    assertEquals("Nguyễn Văn A", response.reviewerName());
    assertTrue(response.isAuthor());
  }

  @Test
  @DisplayName("Lấy đánh giá của chính mình khi chưa từng đánh giá sẽ trả về null")
  void getMyReview_notFound_returnsNull() {
    when(reviewRepository.findByTutorIdAndReviewerUserId(testTutorId, testUserId))
        .thenReturn(Optional.empty());

    ReviewResponse response = reviewService.getMyReview(studentUser, testTutorId);
    assertNull(response);
  }

  @Test
  @DisplayName("Xem danh sách đánh giá của gia sư phân trang với context người dùng (UC6.2, BR-59)")
  void getTutorReviews_success() {
    Pageable pageable = PageRequest.of(0, 10);
    TutorSummaryDto tutorDto = createDummyTutor(testTutorId);
    when(profileFacade.findTutorById(testTutorId)).thenReturn(Optional.of(tutorDto));

    ReviewEntity review1 = new ReviewEntity(testTutorId, testStudentId, testUserId, "STUDENT", 5, "Rất tốt và đúng giờ.", 1);
    review1.setReviewId(UUID.randomUUID());
    Page<ReviewEntity> page = new PageImpl<>(List.of(review1), pageable, 1);
    when(reviewRepository.findByTutorId(testTutorId, pageable)).thenReturn(page);
    when(iamFacade.findUserSummaryById(testUserId))
        .thenReturn(Optional.of(createDummyUser(testUserId, "Người Học Sinh", AccountRole.STUDENT)));

    Page<ReviewResponse> result = reviewService.getTutorReviews(testTutorId, pageable, studentUser);

    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    ReviewResponse item = result.getContent().get(0);
    assertEquals(5, item.rating());
    assertEquals("Người Học Sinh", item.reviewerName());
    assertTrue(item.isAuthor());
  }

  @Test
  @DisplayName("Lấy tóm tắt điểm và phân bổ sao của gia sư")
  void getTutorRatingSummary_success() {
    TutorSummaryDto tutorDto = createDummyTutor(testTutorId);
    when(profileFacade.findTutorById(testTutorId)).thenReturn(Optional.of(tutorDto));
    when(reviewRepository.findAverageRatingByTutorId(testTutorId)).thenReturn(4.7);
    when(reviewRepository.countByTutorId(testTutorId)).thenReturn(3);
    when(reviewRepository.countRatingsByStar(testTutorId)).thenReturn(List.of(
        new Object[]{5, 2L},
        new Object[]{4, 1L}
    ));

    TutorRatingSummaryResponse response = reviewService.getTutorRatingSummary(testTutorId);

    assertNotNull(response);
    assertEquals(4.7, response.averageRating());
    assertEquals(3, response.totalReviews());
    assertEquals(2L, response.ratingDistribution().get(5));
    assertEquals(1L, response.ratingDistribution().get(4));
    assertEquals(0L, response.ratingDistribution().get(1));
  }
}

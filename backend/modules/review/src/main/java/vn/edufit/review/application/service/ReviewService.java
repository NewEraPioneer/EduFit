package vn.edufit.review.application.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.review.api.event.ReviewSubmittedEvent;
import vn.edufit.review.api.event.ReviewUpdatedEvent;
import vn.edufit.review.infra.persistence.entity.ReviewEntity;
import vn.edufit.review.infra.persistence.repository.ReviewRepository;
import vn.edufit.review.web.request.CreateReviewRequest;
import vn.edufit.review.web.request.UpdateReviewRequest;
import vn.edufit.review.web.response.ReviewResponse;
import vn.edufit.review.web.response.TutorRatingSummaryResponse;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Service xử lý các nghiệp vụ đánh giá gia sư (UC6.1, UC6.2, FR-28, FR-29).
 */
@Service
@Transactional
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final ProfileFacade profileFacade;
  private final ConnectionFacade connectionFacade;
  private final SessionFacade sessionFacade;
  private final IamFacade iamFacade;
  private final ApplicationEventPublisher eventPublisher;

  public ReviewService(
      ReviewRepository reviewRepository,
      ProfileFacade profileFacade,
      ConnectionFacade connectionFacade,
      SessionFacade sessionFacade,
      IamFacade iamFacade,
      ApplicationEventPublisher eventPublisher
  ) {
    this.reviewRepository = reviewRepository;
    this.profileFacade = profileFacade;
    this.connectionFacade = connectionFacade;
    this.sessionFacade = sessionFacade;
    this.iamFacade = iamFacade;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Tạo đánh giá mới cho gia sư sau khi hoàn thành buổi học (UC6.1, BR-57, BR-58, BR-59, NFR-24).
   */
  public ReviewResponse createReview(CurrentUser currentUser, CreateReviewRequest request) {
    if (currentUser == null || currentUser.getUserId() == null) {
      throw new ForbiddenOperationException("Yêu cầu cần được xác thực hoặc phiên đăng nhập đã hết hạn.");
    }

    // 1. Kiểm tra điểm đánh giá hợp lệ (BR-58: 1..5)
    if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
      throw new InvalidOperationException(ErrorCode.INVALID_RATING, "Điểm đánh giá phải là số nguyên từ 1 đến 5 sao.");
    }

    // 2. Xác minh gia sư tồn tại
    TutorSummaryDto tutor = profileFacade.findTutorById(request.tutorId())
        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin gia sư với ID: " + request.tutorId()));

    // 3. Xác minh học sinh tồn tại
    StudentSummaryDto student = profileFacade.findStudentById(request.studentId())
        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin học sinh với ID: " + request.studentId()));

    // 4. Xác minh quyền đánh giá của người dùng hiện tại (Học sinh hoặc Phụ huynh)
    String reviewerType;
    if (currentUser.hasRole("STUDENT")) {
      UUID studentProfileId = profileFacade.getStudentIdByUserId(currentUser.getUserId());
      if (studentProfileId == null || !studentProfileId.equals(request.studentId())) {
        throw new ForbiddenOperationException("Học sinh chỉ có quyền đánh giá gia sư cho chính mình.");
      }
      reviewerType = "STUDENT";
    } else if (currentUser.hasRole("PARENT")) {
      boolean hasConfirmedLink = connectionFacade.hasConfirmedParentLink(currentUser.getUserId(), request.studentId());
      if (!hasConfirmedLink) {
        throw new ForbiddenOperationException("Phụ huynh chưa có liên kết xác nhận với học sinh này.");
      }
      reviewerType = "PARENT";
    } else if (currentUser.hasRole("ADMIN")) {
      reviewerType = "STUDENT";
    } else {
      throw new ForbiddenOperationException("Chỉ học sinh hoặc phụ huynh mới có quyền đánh giá gia sư.");
    }

    // 5. Kiểm tra lớp học / mối quan hệ kết nối giữa học sinh và gia sư (BR-56)
    var activeClass = connectionFacade.findActiveClass(request.studentId(), request.tutorId());
    if (activeClass.isEmpty()) {
      throw new InvalidOperationException(ErrorCode.NOT_ENOUGH_SESSIONS, "Chưa hoàn thành đủ số buổi học hoặc chưa có lớp học với gia sư này.");
    }

    // 6. Kiểm tra ràng buộc độc nhất 1 review/cặp (BR-57, NFR-24)
    if (reviewRepository.existsByTutorIdAndStudentId(request.tutorId(), request.studentId())) {
      throw new ResourceConflictException(ErrorCode.REVIEW_ALREADY_EXISTS, "Học sinh đã đánh giá gia sư này rồi, mỗi cặp chỉ được phép đánh giá một lần.");
    }

    // 7. Lấy số lượng buổi học đã hoàn thành thực tế từ Scheduling (BR-56, BR-61)
    int completedSessions = 1;
    if (sessionFacade != null) {
      completedSessions = sessionFacade.countCompletedSessionsBetween(request.tutorId(), request.studentId());
      if (completedSessions < 1) {
        throw new InvalidOperationException(ErrorCode.NOT_ENOUGH_SESSIONS, "Bạn cần hoàn thành ít nhất 1 buổi học với gia sư trước khi gửi đánh giá.");
      }
    }
    ReviewEntity entity = new ReviewEntity(
        request.tutorId(),
        request.studentId(),
        currentUser.getUserId(),
        reviewerType,
        request.rating(),
        request.comment().trim(),
        completedSessions
    );

    ReviewEntity saved;
    try {
      saved = reviewRepository.saveAndFlush(entity);
    } catch (DataIntegrityViolationException ex) {
      throw new ResourceConflictException(ErrorCode.REVIEW_ALREADY_EXISTS, "Học sinh đã đánh giá gia sư này rồi.");
    }

    // 8. Đồng bộ điểm trung bình sang hồ sơ gia sư (FR-29)
    syncTutorRatingStats(saved.getTutorId());

    // 9. Bắn sự kiện miền cho module notification lắng nghe (FR-28)
    eventPublisher.publishEvent(ReviewSubmittedEvent.of(
        saved.getReviewId(),
        saved.getTutorId(),
        saved.getStudentId(),
        saved.getReviewerUserId(),
        saved.getReviewerType(),
        saved.getRating()
    ));

    String reviewerName = resolveReviewerName(saved.getReviewerUserId(), reviewerType);
    return ReviewResponse.from(saved, reviewerName, true);
  }

  /**
   * Chỉnh sửa đánh giá của chính mình (BR-57, BR-58).
   */
  public ReviewResponse updateReview(CurrentUser currentUser, UUID reviewId, UpdateReviewRequest request) {
    if (currentUser == null || currentUser.getUserId() == null) {
      throw new ForbiddenOperationException("Yêu cầu cần được xác thực hoặc phiên đăng nhập đã hết hạn.");
    }

    // 1. Kiểm tra điểm đánh giá hợp lệ
    if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
      throw new InvalidOperationException(ErrorCode.INVALID_RATING, "Điểm đánh giá phải là số nguyên từ 1 đến 5 sao.");
    }

    // 2. Tìm đánh giá theo ID
    ReviewEntity review = reviewRepository.findById(reviewId)
        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đánh giá với ID: " + reviewId));

    // 3. Chỉ tác giả mới được sửa (BR-57, BR-60 đơn giản hóa)
    if (!review.getReviewerUserId().equals(currentUser.getUserId())) {
      throw new ForbiddenOperationException(ErrorCode.ONLY_AUTHOR_CAN_EDIT, "Chỉ tác giả mới có quyền chỉnh sửa đánh giá này.");
    }

    // 4. Cập nhật thông tin
    review.setRating(request.rating());
    review.setComment(request.comment().trim());
    review.setEdited(true);
    review.setUpdatedAt(Instant.now());

    ReviewEntity updated = reviewRepository.save(review);

    // 5. Đồng bộ lại điểm đánh giá sang hồ sơ gia sư (FR-29)
    syncTutorRatingStats(updated.getTutorId());

    // 6. Phát sự kiện cập nhật đánh giá cho notification (FR-28)
    eventPublisher.publishEvent(ReviewUpdatedEvent.of(
        updated.getReviewId(),
        updated.getTutorId(),
        updated.getStudentId(),
        updated.getReviewerUserId(),
        updated.getReviewerType(),
        updated.getRating()
    ));

    String reviewerName = resolveReviewerName(updated.getReviewerUserId(), updated.getReviewerType());
    return ReviewResponse.from(updated, reviewerName, true);
  }

  /**
   * Lấy đánh giá của chính người dùng hiện tại đối với gia sư (nếu có) để ghim lên đầu (Float-to-Top UX).
   */
  @Transactional(readOnly = true)
  public ReviewResponse getMyReview(CurrentUser currentUser, UUID tutorId) {
    if (currentUser == null || currentUser.getUserId() == null || tutorId == null) {
      return null;
    }

    return reviewRepository.findByTutorIdAndReviewerUserId(tutorId, currentUser.getUserId())
        .map(entity -> {
          String reviewerName = resolveReviewerName(entity.getReviewerUserId(), entity.getReviewerType());
          return ReviewResponse.from(entity, reviewerName, true);
        })
        .orElse(null);
  }

  /**
   * Lấy danh sách đánh giá của gia sư với phân trang (UC6.2 - Public).
   */
  @Transactional(readOnly = true)
  public Page<ReviewResponse> getTutorReviews(UUID tutorId, Pageable pageable, CurrentUser currentUser) {
    profileFacade.findTutorById(tutorId)
        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin gia sư với ID: " + tutorId));

    Page<ReviewEntity> page = reviewRepository.findByTutorId(tutorId, pageable);
    UUID currentUserId = (currentUser != null) ? currentUser.getUserId() : null;

    return page.map(entity -> {
      boolean isAuthor = currentUserId != null && currentUserId.equals(entity.getReviewerUserId());
      String reviewerName = resolveReviewerName(entity.getReviewerUserId(), entity.getReviewerType());
      return ReviewResponse.from(entity, reviewerName, isAuthor);
    });
  }

  /**
   * Phương thức tương thích ngược khi không có context CurrentUser.
   */
  @Transactional(readOnly = true)
  public Page<ReviewResponse> getTutorReviews(UUID tutorId, Pageable pageable) {
    return getTutorReviews(tutorId, pageable, null);
  }

  /**
   * Lấy tóm tắt điểm đánh giá và phân bổ sao của gia sư (Public).
   */
  @Transactional(readOnly = true)
  public TutorRatingSummaryResponse getTutorRatingSummary(UUID tutorId) {
    profileFacade.findTutorById(tutorId)
        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin gia sư với ID: " + tutorId));

    Double avg = reviewRepository.findAverageRatingByTutorId(tutorId);
    double averageRating = avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;
    int totalReviews = reviewRepository.countByTutorId(tutorId);

    Map<Integer, Long> distribution = new HashMap<>();
    for (int star = 1; star <= 5; star++) {
      distribution.put(star, 0L);
    }

    List<Object[]> rows = reviewRepository.countRatingsByStar(tutorId);
    for (Object[] row : rows) {
      if (row.length >= 2 && row[0] instanceof Number starNum && row[1] instanceof Number countNum) {
        distribution.put(starNum.intValue(), countNum.longValue());
      }
    }

    return new TutorRatingSummaryResponse(tutorId, averageRating, totalReviews, distribution);
  }

  private void syncTutorRatingStats(UUID tutorId) {
    Double avg = reviewRepository.findAverageRatingByTutorId(tutorId);
    double roundedAvg = avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;
    int count = reviewRepository.countByTutorId(tutorId);
    profileFacade.updateTutorRatingStats(tutorId, roundedAvg, count);
  }

  private String resolveReviewerName(UUID reviewerUserId, String reviewerType) {
    if (reviewerUserId != null && iamFacade != null) {
      Optional<UserSummaryView> userOpt = iamFacade.findUserSummaryById(reviewerUserId);
      if (userOpt.isPresent() && userOpt.get().fullName() != null && !userOpt.get().fullName().isBlank()) {
        return userOpt.get().fullName().trim();
      }
    }
    return "STUDENT".equals(reviewerType) ? "Học sinh" : "Phụ huynh";
  }
}

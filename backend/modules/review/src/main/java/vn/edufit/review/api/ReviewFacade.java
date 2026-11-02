package vn.edufit.review.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.edufit.review.api.dto.TutorRatingSummaryDto;

/**
 * Public Facade Interface của module Review cung cấp cho các module khác (Discovery, Profile...) tương tác (ADR-002).
 */
public interface ReviewFacade {

  /**
   * Lấy điểm đánh giá trung bình của gia sư (1.0 đến 5.0).
   * Nếu gia sư chưa có đánh giá nào, trả về 0.0.
   */
  double getAverageRatingByTutorId(UUID tutorId);

  /**
   * Đếm tổng số lượt đánh giá của gia sư.
   */
  int countReviewsByTutorId(UUID tutorId);

  /**
   * Lấy thông tin tóm tắt điểm và số lượng đánh giá của một gia sư.
   */
  Optional<TutorRatingSummaryDto> getTutorRatingSummary(UUID tutorId);

  /**
   * Lấy hàng loạt tóm tắt đánh giá cho danh sách gia sư (phục vụ Discovery/Matching).
   */
  Map<UUID, TutorRatingSummaryDto> getTutorRatingSummaries(Collection<UUID> tutorIds);

  /**
   * Kiểm tra học sinh đã đánh giá gia sư này hay chưa (BR-61, NFR-24).
   */
  boolean hasReviewed(UUID tutorId, UUID studentId);
}


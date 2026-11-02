package vn.edufit.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.review.api.dto.TutorRatingSummaryDto;
import vn.edufit.review.application.service.ReviewFacadeImpl;
import vn.edufit.review.infra.persistence.entity.ReviewEntity;
import vn.edufit.review.infra.persistence.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class ReviewFacadeImplTest {

  @Mock
  private ReviewRepository reviewRepository;

  private ReviewFacadeImpl reviewFacade;

  @BeforeEach
  void setUp() {
    reviewFacade = new ReviewFacadeImpl(reviewRepository);
  }

  @Test
  @DisplayName("Lấy điểm trung bình và số lượng đánh giá của gia sư qua Facade")
  void getTutorRatingSummary_success() {
    UUID tutorId = UUID.randomUUID();
    when(reviewRepository.findAverageRatingByTutorId(tutorId)).thenReturn(4.8);
    when(reviewRepository.countByTutorId(tutorId)).thenReturn(10);

    Optional<TutorRatingSummaryDto> summaryOpt = reviewFacade.getTutorRatingSummary(tutorId);

    assertTrue(summaryOpt.isPresent());
    assertEquals(4.8, summaryOpt.get().averageRating());
    assertEquals(10, summaryOpt.get().totalReviews());
  }

  @Test
  @DisplayName("Lấy hàng loạt tóm tắt đánh giá cho danh sách gia sư")
  void getTutorRatingSummaries_batchSuccess() {
    UUID tutor1 = UUID.randomUUID();
    UUID tutor2 = UUID.randomUUID();
    UUID student1 = UUID.randomUUID();

    List<Object[]> rows = List.of(
        new Object[]{tutor1, 4.5, 2L},
        new Object[]{tutor2, 3.0, 1L}
    );
    when(reviewRepository.findRatingSummariesByTutorIds(List.of(tutor1, tutor2))).thenReturn(rows);

    Map<UUID, TutorRatingSummaryDto> summaries = reviewFacade.getTutorRatingSummaries(List.of(tutor1, tutor2));

    assertNotNull(summaries);
    assertEquals(2, summaries.size());
    assertEquals(4.5, summaries.get(tutor1).averageRating());
    assertEquals(2, summaries.get(tutor1).totalReviews());
    assertEquals(3.0, summaries.get(tutor2).averageRating());
    assertEquals(1, summaries.get(tutor2).totalReviews());
  }

  @Test
  @DisplayName("Kiểm tra học sinh đã đánh giá gia sư chưa")
  void hasReviewed_checksRepository() {
    UUID tutorId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();

    when(reviewRepository.existsByTutorIdAndStudentId(tutorId, studentId)).thenReturn(true);

    assertTrue(reviewFacade.hasReviewed(tutorId, studentId));
    assertFalse(reviewFacade.hasReviewed(null, studentId));
  }
}


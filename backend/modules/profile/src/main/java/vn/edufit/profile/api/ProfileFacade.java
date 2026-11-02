package vn.edufit.profile.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.edufit.profile.api.dto.EducationLevelOrderDto;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.TutorOfferingDto;
import vn.edufit.profile.api.dto.TutorSearchCriteria;
import vn.edufit.profile.api.dto.TutorSummaryDto;

/**
 * Public Facade Interface của module Profile cung cấp cho các module khác (Verification, Discovery, IAM...) tương tác (ADR-002).
 */
public interface ProfileFacade {

  Optional<TutorSummaryDto> findTutorByUserId(UUID userId);

  Optional<TutorSummaryDto> findTutorById(UUID tutorId);

  /**
   * Lọc cứng theo tiêu chí người dùng tự chọn, trả tối đa {@code limit} gia sư đã xác minh
   * (đầy đủ gói dạy) theo thứ tự đánh giá, số lượt đánh giá, thời điểm đăng ký và ID.
   */
  List<TutorDiscoveryProfileDto> searchVerifiedTutors(TutorSearchCriteria criteria, int limit);

  Optional<TutorDiscoveryProfileDto> findVerifiedTutorDetail(UUID tutorId);

  List<TutorDiscoveryProfileDto> findVerifiedTutorDetails(List<UUID> tutorIds);

  /**
   * Ứng viên ghép đôi chỉ theo điều kiện tư cách: đã xác minh và có gói dạy môn này.
   * Mọi tiêu chí còn lại (lớp, mục tiêu, bộ sách, khu vực, giá, lịch, đánh giá) là điểm xếp hạng.
   * Trả tập ứng viên giới hạn theo đánh giá, số lượt đánh giá, thời điểm đăng ký và ID.
   */
  List<TutorDiscoveryProfileDto> findVerifiedCandidatesBySubject(Integer subjectId);

  /**
   * Returns the complete level catalog in business order, including retired levels so
   * deactivation does not make previously non-adjacent levels adjacent.
   * Missing or duplicate sort orders are rejected as ambiguous catalog data.
   */
  List<EducationLevelOrderDto> findEducationLevelsForMatching();

  /** Đọc nội bộ; nơi gọi phải tự kiểm tra quyền sở hữu hoặc liên kết phụ huynh. */
  Optional<LearningNeedView> findLearningNeed(UUID needId);

  Map<UUID, LearningNeedView> findLearningNeeds(Collection<UUID> needIds);

  /** Đọc nội bộ; gồm cả nhu cầu đã lưu trữ. Nơi gọi phải tự kiểm tra quyền trước. */
  List<LearningNeedView> findLearningNeedsForStudent(UUID studentId);

  List<TutorOfferingDto> findTutorOfferings(UUID tutorId);

  UUID getTutorIdByUserId(UUID userId);

  void markTutorPendingVerification(UUID tutorId);

  void markTutorVerified(UUID tutorId, Instant verifiedAt);

  void markTutorRejected(UUID tutorId);

  void markTutorUnverified(UUID tutorId);

  /**
   * Áp kết quả duyệt hồ sơ: xác nhận loại gia sư (nếu admin chọn) và xác minh mục tiêu premium
   * của các gói dạy có bằng chứng được duyệt.
   */
  void applyVerificationApproval(UUID tutorId, String confirmedTutorType, Collection<UUID> evidencedOfferingIds);

  /** Thu hồi xác minh: bỏ xác nhận bằng chứng mục tiêu premium của mọi gói dạy. */
  void revokeOfferingEvidence(UUID tutorId);

  Optional<StudentSummaryDto> findStudentByUserId(UUID userId);

  Optional<StudentSummaryDto> findStudentById(UUID studentId);

  UUID getStudentIdByUserId(UUID userId);

  boolean existsTutorByUserId(UUID userId);

  boolean existsStudentByUserId(UUID userId);

  void updateTutorRatingStats(UUID tutorId, double ratingAvg, int reviewCount);
}

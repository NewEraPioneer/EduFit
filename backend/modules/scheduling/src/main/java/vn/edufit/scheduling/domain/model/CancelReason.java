package vn.edufit.scheduling.domain.model;

/**
 * Phân loại lý do hủy buổi học (BR-45).
 *
 * <p>Khớp với ràng buộc DB {@code chk_session_cancel_reason CHECK (cancel_reason IS NULL OR
 * cancel_reason IN ('SCHEDULE_CONFLICT', 'PERSONAL_REASON', 'OTHER_PARTY_UNAVAILABLE', 'OTHER'))}.
 */
public enum CancelReason {

  /** Trùng lịch đột xuất với việc học/làm việc khác. */
  SCHEDULE_CONFLICT,

  /** Lý do cá nhân / gia đình / sức khỏe. */
  PERSONAL_REASON,

  /** Đối tác (Gia sư hoặc Học sinh) không thể tham gia. */
  OTHER_PARTY_UNAVAILABLE,

  /** Lý do khác (kèm văn bản giải thích chi tiết trong cancelComment). */
  OTHER
}

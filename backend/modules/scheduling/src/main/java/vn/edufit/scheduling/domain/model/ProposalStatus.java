package vn.edufit.scheduling.domain.model;

/**
 * Trạng thái của một đề xuất đổi thời gian buổi học (Reschedule Proposal).
 *
 * <p>Khớp với ràng buộc DB {@code chk_reschedule_status CHECK (status IN ('PENDING', 'ACCEPTED',
 * 'REJECTED', 'WITHDRAWN', 'EXPIRED'))}.
 */
public enum ProposalStatus {

  /** Đề xuất đổi lịch đang chờ đối tác phản hồi. */
  PENDING,

  /** Đề xuất đổi lịch đã được đối tác chấp nhận (giờ học mới được cập nhật). */
  ACCEPTED,

  /** Đề xuất đổi lịch bị từ chối (buổi học giữ nguyên khung giờ ban đầu). */
  REJECTED,

  /** Người yêu cầu tự rút lại đề xuất đổi lịch. */
  WITHDRAWN,

  /** Đề xuất đổi lịch đã quá hạn phản hồi. */
  EXPIRED
}

package vn.edufit.progress.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import vn.edufit.progress.domain.model.MilestoneAssessment;
import vn.edufit.progress.domain.model.SessionFeedback;

/**
 * Cổng giao tiếp kho dữ liệu cho {@link SessionFeedback}, kèm điểm chấm và liên kết "thực tế" buổi học ↔ cột mốc.
 */
public interface SessionFeedbackDomainRepository {

  /** Lưu phản hồi, các điểm chấm và liên kết ACTUAL giữa buổi học và các cột mốc được dạy. */
  SessionFeedback save(SessionFeedback feedback);

  boolean existsBySessionId(UUID sessionId);

  List<SessionFeedback> findBySessionIdIn(Collection<UUID> sessionIds);

  /** Các buổi học trong danh sách đã có phản hồi (truy vấn nhẹ, không nạp điểm chấm). */
  Set<UUID> findSessionIdsWithFeedback(Collection<UUID> sessionIds);

  /** Toàn bộ điểm chấm (mọi buổi) của các cột mốc, sắp xếp theo thời điểm chấm tăng dần. */
  List<MilestoneAssessment> findAssessmentsByMilestoneIdIn(Collection<UUID> milestoneIds);
}

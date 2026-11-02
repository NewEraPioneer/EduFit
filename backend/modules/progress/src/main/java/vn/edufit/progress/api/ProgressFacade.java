package vn.edufit.progress.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Bề mặt công khai duy nhất (Public Facade Interface) của module Progress (ADR-002).
 */
public interface ProgressFacade {

  /**
   * Tra cứu tóm tắt Kế hoạch học tập và tiến độ của một lớp học.
   */
  Optional<LearningPlanSummaryView> findPlanSummaryByClassId(UUID classId);

  /**
   * Tính phần trăm tiến độ của một lớp học (0-100, hoặc null nếu chưa có plan/milestone).
   */
  Integer getProgressPercentageByClassId(UUID classId);

  record LearningPlanSummaryView(
      UUID planId,
      UUID classId,
      String summary,
      int version,
      Integer progressPercentage,
      int totalActiveMilestones,
      int achievedMilestones,
      Instant updatedAt
  ) {}
}

package vn.edufit.progress.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.edufit.progress.domain.model.LearningPlan;

/**
 * Cổng giao tiếp kho dữ liệu (Repository Port) cho Aggregate Root {@link LearningPlan}.
 */
public interface LearningPlanDomainRepository {

  Optional<LearningPlan> findByClassId(UUID classId);

  Optional<LearningPlan> findById(UUID planId);

  Optional<LearningPlan> findByMilestoneId(UUID milestoneId);

  boolean existsByClassId(UUID classId);

  LearningPlan save(LearningPlan plan);

  /**
   * Batch query kế hoạch học tập theo danh sách mã lớp học (chống N+1 query).
   */
  List<LearningPlan> findByClassIdIn(Collection<UUID> classIds);
}

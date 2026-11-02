package vn.edufit.progress.infra.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edufit.progress.infra.persistence.entity.LearningPlanJpaEntity;

@Repository
public interface SpringDataLearningPlanRepository extends JpaRepository<LearningPlanJpaEntity, UUID> {

  @Query("SELECT p FROM LearningPlanJpaEntity p LEFT JOIN FETCH p.milestones WHERE p.classId = :classId")
  Optional<LearningPlanJpaEntity> findByClassIdWithMilestones(@Param("classId") UUID classId);

  @Query("SELECT p FROM LearningPlanJpaEntity p LEFT JOIN FETCH p.milestones WHERE p.planId = :planId")
  Optional<LearningPlanJpaEntity> findByIdWithMilestones(@Param("planId") UUID planId);

  @Query("SELECT p FROM LearningPlanJpaEntity p LEFT JOIN FETCH p.milestones WHERE p.planId = (SELECT m.plan.planId FROM MilestoneJpaEntity m WHERE m.milestoneId = :milestoneId)")
  Optional<LearningPlanJpaEntity> findByMilestoneIdWithMilestones(@Param("milestoneId") UUID milestoneId);

  @Query("SELECT DISTINCT p FROM LearningPlanJpaEntity p LEFT JOIN FETCH p.milestones WHERE p.classId IN :classIds")
  List<LearningPlanJpaEntity> findByClassIdInWithMilestones(@Param("classIds") Collection<UUID> classIds);

  boolean existsByClassId(UUID classId);
}

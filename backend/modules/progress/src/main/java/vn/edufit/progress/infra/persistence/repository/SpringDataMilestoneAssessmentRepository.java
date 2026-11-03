package vn.edufit.progress.infra.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edufit.progress.infra.persistence.entity.MilestoneAssessmentJpaEntity;

@Repository
public interface SpringDataMilestoneAssessmentRepository extends JpaRepository<MilestoneAssessmentJpaEntity, UUID> {

  List<MilestoneAssessmentJpaEntity> findByMilestoneIdInOrderByCreatedAtAsc(Collection<UUID> milestoneIds);

  List<MilestoneAssessmentJpaEntity> findBySessionIdIn(Collection<UUID> sessionIds);
}

package vn.edufit.progress.infra.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edufit.progress.infra.persistence.entity.PlanHistoryJpaEntity;

@Repository
public interface SpringDataPlanHistoryRepository extends JpaRepository<PlanHistoryJpaEntity, UUID> {

  List<PlanHistoryJpaEntity> findByPlanIdOrderByCreatedAtDesc(UUID planId);
}

package vn.edufit.progress.infra.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edufit.progress.infra.persistence.entity.PlanTemplateJpaEntity;

@Repository
public interface SpringDataPlanTemplateRepository extends JpaRepository<PlanTemplateJpaEntity, UUID> {

  @Query("""
      SELECT t FROM PlanTemplateJpaEntity t
      WHERE t.ownerTutorId IS NULL OR t.ownerTutorId = :ownerTutorId
      ORDER BY t.title ASC
  """)
  List<PlanTemplateJpaEntity> findVisibleTo(@Param("ownerTutorId") UUID ownerTutorId);
}

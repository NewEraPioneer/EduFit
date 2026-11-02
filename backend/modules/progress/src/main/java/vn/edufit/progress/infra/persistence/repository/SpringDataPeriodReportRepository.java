package vn.edufit.progress.infra.persistence.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edufit.progress.infra.persistence.entity.PeriodReportJpaEntity;

@Repository
public interface SpringDataPeriodReportRepository extends JpaRepository<PeriodReportJpaEntity, UUID> {

  boolean existsByClassIdAndPeriodIndex(UUID classId, int periodIndex);

  List<PeriodReportJpaEntity> findByClassIdOrderByPeriodIndexDesc(UUID classId);

  List<PeriodReportJpaEntity> findByClassIdInAndStatusOrderByAutoSendAtAsc(Collection<UUID> classIds, String status);

  List<PeriodReportJpaEntity> findByStatusAndAutoSendAtLessThanEqualOrderByAutoSendAtAsc(String status, Instant now);
}

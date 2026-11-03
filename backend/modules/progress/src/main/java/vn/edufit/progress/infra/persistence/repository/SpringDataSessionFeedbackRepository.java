package vn.edufit.progress.infra.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edufit.progress.infra.persistence.entity.SessionFeedbackJpaEntity;

@Repository
public interface SpringDataSessionFeedbackRepository extends JpaRepository<SessionFeedbackJpaEntity, UUID> {

  boolean existsBySessionId(UUID sessionId);

  List<SessionFeedbackJpaEntity> findBySessionIdIn(Collection<UUID> sessionIds);

  @Query("SELECT f.sessionId FROM SessionFeedbackJpaEntity f WHERE f.sessionId IN :sessionIds")
  List<UUID> findSessionIdsIn(@Param("sessionIds") Collection<UUID> sessionIds);
}

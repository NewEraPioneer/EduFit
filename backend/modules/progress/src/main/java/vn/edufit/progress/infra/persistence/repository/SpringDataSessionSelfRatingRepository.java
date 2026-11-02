package vn.edufit.progress.infra.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edufit.progress.infra.persistence.entity.SessionSelfRatingJpaEntity;

@Repository
public interface SpringDataSessionSelfRatingRepository extends JpaRepository<SessionSelfRatingJpaEntity, UUID> {

  Optional<SessionSelfRatingJpaEntity> findBySessionId(UUID sessionId);

  List<SessionSelfRatingJpaEntity> findBySessionIdIn(Collection<UUID> sessionIds);
}

package vn.edufit.progress.infra.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edufit.progress.infra.persistence.entity.SessionNoteJpaEntity;

@Repository
public interface SpringDataSessionNoteRepository extends JpaRepository<SessionNoteJpaEntity, UUID> {

  Optional<SessionNoteJpaEntity> findBySessionId(UUID sessionId);

  boolean existsBySessionId(UUID sessionId);

  List<SessionNoteJpaEntity> findBySessionIdIn(Collection<UUID> sessionIds);
}

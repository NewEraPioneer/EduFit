package vn.edufit.progress.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.edufit.progress.domain.model.SessionNote;

/**
 * Cổng giao tiếp kho dữ liệu (Repository Port) cho Biên bản ghi nhớ buổi học {@link SessionNote}.
 */
public interface SessionNoteDomainRepository {

  SessionNote save(SessionNote sessionNote);

  Optional<SessionNote> findBySessionId(UUID sessionId);

  List<SessionNote> findBySessionIdIn(Collection<UUID> sessionIds);

  boolean existsBySessionId(UUID sessionId);
}

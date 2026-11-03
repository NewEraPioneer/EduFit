package vn.edufit.progress.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.edufit.progress.domain.model.SessionSelfRating;

/** Cổng kho dữ liệu cho cảm nhận 1 chạm của học sinh. */
public interface SessionSelfRatingDomainRepository {

  SessionSelfRating save(SessionSelfRating rating);

  Optional<SessionSelfRating> findBySessionId(UUID sessionId);

  List<SessionSelfRating> findBySessionIdIn(Collection<UUID> sessionIds);
}

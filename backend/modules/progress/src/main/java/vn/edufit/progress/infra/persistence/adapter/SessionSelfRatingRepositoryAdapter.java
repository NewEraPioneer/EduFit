package vn.edufit.progress.infra.persistence.adapter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edufit.progress.domain.model.SelfRating;
import vn.edufit.progress.domain.model.SessionSelfRating;
import vn.edufit.progress.domain.repository.SessionSelfRatingDomainRepository;
import vn.edufit.progress.infra.persistence.entity.SessionSelfRatingJpaEntity;
import vn.edufit.progress.infra.persistence.repository.SpringDataSessionSelfRatingRepository;

@Component
@RequiredArgsConstructor
public class SessionSelfRatingRepositoryAdapter implements SessionSelfRatingDomainRepository {

  private final SpringDataSessionSelfRatingRepository repository;

  @Override
  public SessionSelfRating save(SessionSelfRating rating) {
    return toDomain(repository.saveAndFlush(SessionSelfRatingJpaEntity.builder()
        .ratingId(rating.getRatingId())
        .sessionId(rating.getSessionId())
        .classId(rating.getClassId())
        .studentId(rating.getStudentId())
        .rating((short) rating.getRating().value())
        .createdBy(rating.getCreatedBy())
        .createdAt(rating.getCreatedAt())
        .updatedAt(rating.getUpdatedAt())
        .build()));
  }

  @Override
  public Optional<SessionSelfRating> findBySessionId(UUID sessionId) {
    return repository.findBySessionId(sessionId).map(SessionSelfRatingRepositoryAdapter::toDomain);
  }

  @Override
  public List<SessionSelfRating> findBySessionIdIn(Collection<UUID> sessionIds) {
    if (sessionIds == null || sessionIds.isEmpty()) return List.of();
    return repository.findBySessionIdIn(sessionIds).stream().map(SessionSelfRatingRepositoryAdapter::toDomain).toList();
  }

  private static SessionSelfRating toDomain(SessionSelfRatingJpaEntity e) {
    return new SessionSelfRating(
        e.getRatingId(), e.getSessionId(), e.getClassId(), e.getStudentId(),
        SelfRating.fromValue(e.getRating()), e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt());
  }
}

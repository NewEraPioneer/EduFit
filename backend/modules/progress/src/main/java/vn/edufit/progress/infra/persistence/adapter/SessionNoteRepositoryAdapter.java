package vn.edufit.progress.infra.persistence.adapter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edufit.progress.domain.model.SessionNote;
import vn.edufit.progress.domain.repository.SessionNoteDomainRepository;
import vn.edufit.progress.infra.persistence.entity.SessionNoteJpaEntity;
import vn.edufit.progress.infra.persistence.mapper.ProgressMapper;
import vn.edufit.progress.infra.persistence.repository.SpringDataSessionNoteRepository;

@Component
@RequiredArgsConstructor
public class SessionNoteRepositoryAdapter implements SessionNoteDomainRepository {

  private final SpringDataSessionNoteRepository springDataRepository;
  private final ProgressMapper mapper;

  @Override
  public SessionNote save(SessionNote sessionNote) {
    SessionNoteJpaEntity entity = mapper.toJpaEntity(sessionNote);
    SessionNoteJpaEntity saved = springDataRepository.saveAndFlush(entity);
    return mapper.toDomain(saved);
  }

  @Override
  public Optional<SessionNote> findBySessionId(UUID sessionId) {
    return springDataRepository.findBySessionId(sessionId)
        .map(mapper::toDomain);
  }

  @Override
  public List<SessionNote> findBySessionIdIn(Collection<UUID> sessionIds) {
    if (sessionIds == null || sessionIds.isEmpty()) return List.of();
    return springDataRepository.findBySessionIdIn(sessionIds)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public boolean existsBySessionId(UUID sessionId) {
    return springDataRepository.existsBySessionId(sessionId);
  }
}

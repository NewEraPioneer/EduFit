package vn.edufit.progress.infra.persistence.adapter;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edufit.progress.domain.model.SessionMilestoneLink;
import vn.edufit.progress.domain.repository.SessionMilestoneLinkDomainRepository;
import vn.edufit.progress.infra.persistence.entity.SessionMilestoneLinkJpaEntity;
import vn.edufit.progress.infra.persistence.repository.SpringDataSessionMilestoneLinkRepository;

@Component
@RequiredArgsConstructor
public class SessionMilestoneLinkRepositoryAdapter implements SessionMilestoneLinkDomainRepository {

  private final SpringDataSessionMilestoneLinkRepository repository;

  @Override
  public List<SessionMilestoneLink> findBySessionIdIn(Collection<UUID> sessionIds) {
    if (sessionIds == null || sessionIds.isEmpty()) return List.of();
    return repository.findBySessionIdIn(sessionIds).stream()
        .map(e -> new SessionMilestoneLink(
            e.getSessionId(), e.getMilestoneId(), SessionMilestoneLink.Type.valueOf(e.getLinkType())))
        .toList();
  }

  @Override
  public void replacePlanned(UUID sessionId, Collection<UUID> milestoneIds, UUID createdBy, Instant now) {
    String planned = SessionMilestoneLink.Type.PLANNED.name();
    repository.deleteBySessionIdAndLinkType(sessionId, planned);
    repository.saveAllAndFlush(new LinkedHashSet<>(milestoneIds).stream()
        .map(milestoneId -> SessionMilestoneLinkJpaEntity.builder()
            .linkId(UUID.randomUUID())
            .sessionId(sessionId)
            .milestoneId(milestoneId)
            .linkType(planned)
            .createdBy(createdBy)
            .createdAt(now)
            .build())
        .toList());
  }
}

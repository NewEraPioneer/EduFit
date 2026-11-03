package vn.edufit.progress.infra.persistence.adapter;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edufit.progress.domain.policy.FeedbackTimingPolicy.ReminderStage;
import vn.edufit.progress.domain.repository.FeedbackReminderDomainRepository;
import vn.edufit.progress.infra.persistence.entity.FeedbackReminderJpaEntity;
import vn.edufit.progress.infra.persistence.repository.SpringDataFeedbackReminderRepository;

@Component
@RequiredArgsConstructor
public class FeedbackReminderRepositoryAdapter implements FeedbackReminderDomainRepository {

  private final SpringDataFeedbackReminderRepository repository;

  @Override
  public Map<UUID, Set<ReminderStage>> findSentStagesBySessionIdIn(Collection<UUID> sessionIds) {
    Map<UUID, Set<ReminderStage>> result = new HashMap<>();
    if (sessionIds == null || sessionIds.isEmpty()) return result;
    for (FeedbackReminderJpaEntity e : repository.findBySessionIdIn(sessionIds)) {
      result.computeIfAbsent(e.getSessionId(), k -> EnumSet.noneOf(ReminderStage.class))
          .add(ReminderStage.valueOf(e.getStage()));
    }
    return result;
  }

  @Override
  public void recordSent(UUID sessionId, ReminderStage stage, Instant sentAt) {
    repository.saveAndFlush(FeedbackReminderJpaEntity.builder()
        .reminderId(UUID.randomUUID())
        .sessionId(sessionId)
        .stage(stage.name())
        .sentAt(sentAt)
        .build());
  }
}

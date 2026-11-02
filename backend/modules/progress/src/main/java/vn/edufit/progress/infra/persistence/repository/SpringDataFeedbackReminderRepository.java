package vn.edufit.progress.infra.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edufit.progress.infra.persistence.entity.FeedbackReminderJpaEntity;

@Repository
public interface SpringDataFeedbackReminderRepository extends JpaRepository<FeedbackReminderJpaEntity, UUID> {

  List<FeedbackReminderJpaEntity> findBySessionIdIn(Collection<UUID> sessionIds);
}

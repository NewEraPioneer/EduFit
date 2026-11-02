package vn.edufit.progress.domain.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import vn.edufit.progress.domain.policy.FeedbackTimingPolicy.ReminderStage;

/** Nhật ký các lần nhắc gia sư phản hồi buổi học, dùng để không gửi trùng một mốc. */
public interface FeedbackReminderDomainRepository {

  Map<UUID, Set<ReminderStage>> findSentStagesBySessionIdIn(Collection<UUID> sessionIds);

  void recordSent(UUID sessionId, ReminderStage stage, Instant sentAt);
}

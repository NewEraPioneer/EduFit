package vn.edufit.progress.infra.persistence.adapter;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edufit.progress.domain.model.MilestoneAssessment;
import vn.edufit.progress.domain.model.SessionFeedback;
import vn.edufit.progress.domain.model.SessionMilestoneLink;
import vn.edufit.progress.domain.repository.SessionFeedbackDomainRepository;
import vn.edufit.progress.infra.persistence.entity.MilestoneAssessmentJpaEntity;
import vn.edufit.progress.infra.persistence.entity.SessionMilestoneLinkJpaEntity;
import vn.edufit.progress.infra.persistence.mapper.ProgressMapper;
import vn.edufit.progress.infra.persistence.repository.SpringDataMilestoneAssessmentRepository;
import vn.edufit.progress.infra.persistence.repository.SpringDataSessionFeedbackRepository;
import vn.edufit.progress.infra.persistence.repository.SpringDataSessionMilestoneLinkRepository;

@Component
@RequiredArgsConstructor
public class SessionFeedbackRepositoryAdapter implements SessionFeedbackDomainRepository {

  private final SpringDataSessionFeedbackRepository feedbackRepository;
  private final SpringDataMilestoneAssessmentRepository assessmentRepository;
  private final SpringDataSessionMilestoneLinkRepository linkRepository;
  private final ProgressMapper mapper;

  @Override
  public SessionFeedback save(SessionFeedback feedback) {
    feedbackRepository.saveAndFlush(mapper.toJpaEntity(feedback));
    List<MilestoneAssessmentJpaEntity> assessments = feedback.getAssessments().stream()
        .map(mapper::toJpaEntity)
        .toList();
    assessmentRepository.saveAllAndFlush(assessments);
    linkRepository.saveAllAndFlush(feedback.getAssessments().stream()
        .map(a -> SessionMilestoneLinkJpaEntity.builder()
            .linkId(UUID.randomUUID())
            .sessionId(feedback.getSessionId())
            .milestoneId(a.getMilestoneId())
            .linkType(SessionMilestoneLink.Type.ACTUAL.name())
            .createdBy(feedback.getCreatedBy())
            .createdAt(feedback.getCreatedAt())
            .build())
        .toList());
    return feedback;
  }

  @Override
  public boolean existsBySessionId(UUID sessionId) {
    return feedbackRepository.existsBySessionId(sessionId);
  }

  @Override
  public List<SessionFeedback> findBySessionIdIn(Collection<UUID> sessionIds) {
    if (sessionIds == null || sessionIds.isEmpty()) return List.of();
    Map<UUID, List<MilestoneAssessment>> assessmentsBySession = assessmentRepository.findBySessionIdIn(sessionIds)
        .stream()
        .map(mapper::toDomain)
        .collect(Collectors.groupingBy(MilestoneAssessment::getSessionId));
    return feedbackRepository.findBySessionIdIn(sessionIds).stream()
        .map(f -> mapper.toDomain(f, assessmentsBySession.getOrDefault(f.getSessionId(), List.of())))
        .toList();
  }

  @Override
  public Set<UUID> findSessionIdsWithFeedback(Collection<UUID> sessionIds) {
    if (sessionIds == null || sessionIds.isEmpty()) return Set.of();
    return new HashSet<>(feedbackRepository.findSessionIdsIn(sessionIds));
  }

  @Override
  public List<MilestoneAssessment> findAssessmentsByMilestoneIdIn(Collection<UUID> milestoneIds) {
    if (milestoneIds == null || milestoneIds.isEmpty()) return List.of();
    return assessmentRepository.findByMilestoneIdInOrderByCreatedAtAsc(milestoneIds).stream()
        .map(mapper::toDomain)
        .toList();
  }
}

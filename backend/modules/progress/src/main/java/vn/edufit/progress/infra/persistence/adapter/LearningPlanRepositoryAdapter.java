package vn.edufit.progress.infra.persistence.adapter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.progress.infra.persistence.entity.LearningPlanJpaEntity;
import vn.edufit.progress.infra.persistence.mapper.ProgressMapper;
import vn.edufit.progress.infra.persistence.repository.SpringDataLearningPlanRepository;

@Component
@RequiredArgsConstructor
public class LearningPlanRepositoryAdapter implements LearningPlanDomainRepository {

  private final SpringDataLearningPlanRepository springDataRepository;
  private final ProgressMapper mapper;

  @Override
  public Optional<LearningPlan> findByClassId(UUID classId) {
    return springDataRepository.findByClassIdWithMilestones(classId)
        .map(mapper::toDomain);
  }

  @Override
  public Optional<LearningPlan> findById(UUID planId) {
    return springDataRepository.findByIdWithMilestones(planId)
        .map(mapper::toDomain);
  }

  @Override
  public Optional<LearningPlan> findByMilestoneId(UUID milestoneId) {
    return springDataRepository.findByMilestoneIdWithMilestones(milestoneId)
        .map(mapper::toDomain);
  }

  @Override
  public boolean existsByClassId(UUID classId) {
    return springDataRepository.existsByClassId(classId);
  }

  @Override
  public LearningPlan save(LearningPlan plan) {
    LearningPlanJpaEntity entity = mapper.toJpaEntity(plan);
    LearningPlanJpaEntity saved = springDataRepository.saveAndFlush(entity);
    return mapper.toDomain(saved);
  }

  @Override
  public List<LearningPlan> findByClassIdIn(Collection<UUID> classIds) {
    if (classIds == null || classIds.isEmpty()) return List.of();
    return springDataRepository.findByClassIdInWithMilestones(classIds)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }
}

package vn.edufit.progress.infra.persistence.adapter;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edufit.progress.domain.model.PlanHistory;
import vn.edufit.progress.domain.repository.PlanHistoryDomainRepository;
import vn.edufit.progress.infra.persistence.entity.PlanHistoryJpaEntity;
import vn.edufit.progress.infra.persistence.mapper.ProgressMapper;
import vn.edufit.progress.infra.persistence.repository.SpringDataPlanHistoryRepository;

@Component
@RequiredArgsConstructor
public class PlanHistoryRepositoryAdapter implements PlanHistoryDomainRepository {

  private final SpringDataPlanHistoryRepository springDataRepository;
  private final ProgressMapper mapper;

  @Override
  public PlanHistory save(PlanHistory history) {
    PlanHistoryJpaEntity entity = mapper.toJpaEntity(history);
    PlanHistoryJpaEntity saved = springDataRepository.saveAndFlush(entity);
    return mapper.toDomain(saved);
  }

  @Override
  public List<PlanHistory> findByPlanId(UUID planId) {
    return springDataRepository.findByPlanIdOrderByCreatedAtDesc(planId)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }
}

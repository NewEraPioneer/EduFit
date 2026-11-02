package vn.edufit.progress.application.service;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.progress.api.ProgressFacade;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProgressFacadeImpl implements ProgressFacade {

  private final LearningPlanDomainRepository planRepository;

  @Override
  public Optional<LearningPlanSummaryView> findPlanSummaryByClassId(UUID classId) {
    return planRepository.findByClassId(classId).map(this::toSummaryView);
  }

  @Override
  public Integer getProgressPercentageByClassId(UUID classId) {
    return planRepository.findByClassId(classId)
        .map(LearningPlan::calculateProgressPercentage)
        .orElse(null);
  }

  private LearningPlanSummaryView toSummaryView(LearningPlan plan) {
    int activeCount = (int) plan.countActiveMilestones();
    int achievedCount = (int) plan.getMilestones().stream()
        .filter(m -> !m.isArchived() && m.getStatus() == MilestoneStatus.ACHIEVED)
        .count();

    return new LearningPlanSummaryView(
        plan.getPlanId(),
        plan.getClassId(),
        plan.getSummary(),
        plan.getVersion(),
        plan.calculateProgressPercentage(),
        activeCount,
        achievedCount,
        plan.getUpdatedAt()
    );
  }
}

package vn.edufit.progress.infra.persistence.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edufit.progress.domain.model.PlanTemplate;
import vn.edufit.progress.domain.repository.PlanTemplateDomainRepository;
import vn.edufit.progress.infra.persistence.entity.PlanTemplateJpaEntity;
import vn.edufit.progress.infra.persistence.entity.PlanTemplateMilestoneJpaEntity;
import vn.edufit.progress.infra.persistence.repository.SpringDataPlanTemplateRepository;

@Component
@RequiredArgsConstructor
public class PlanTemplateRepositoryAdapter implements PlanTemplateDomainRepository {

  private final SpringDataPlanTemplateRepository repository;

  @Override
  public PlanTemplate save(PlanTemplate template) {
    return toDomain(repository.saveAndFlush(toEntity(template)));
  }

  @Override
  public Optional<PlanTemplate> findById(UUID templateId) {
    return repository.findById(templateId).map(PlanTemplateRepositoryAdapter::toDomain);
  }

  @Override
  public List<PlanTemplate> findVisibleTo(UUID ownerTutorId) {
    return repository.findVisibleTo(ownerTutorId).stream().map(PlanTemplateRepositoryAdapter::toDomain).toList();
  }

  @Override
  public void delete(UUID templateId) {
    repository.deleteById(templateId);
    repository.flush();
  }

  private static PlanTemplateJpaEntity toEntity(PlanTemplate t) {
    PlanTemplateJpaEntity entity = PlanTemplateJpaEntity.builder()
        .templateId(t.getTemplateId())
        .ownerTutorId(t.getOwnerTutorId())
        .subjectId(t.getSubjectId())
        .educationLevelId(t.getEducationLevelId())
        .goalTagId(t.getGoalTagId())
        .title(t.getTitle())
        .description(t.getDescription())
        .createdBy(t.getCreatedBy())
        .version(t.getVersion())
        .createdAt(t.getCreatedAt())
        .updatedAt(t.getUpdatedAt())
        .build();
    List<PlanTemplate.TemplateMilestone> items = t.getMilestones();
    for (int i = 0; i < items.size(); i++) {
      PlanTemplate.TemplateMilestone m = items.get(i);
      entity.getMilestones().add(PlanTemplateMilestoneJpaEntity.builder()
          .templateMilestoneId(UUID.randomUUID())
          .template(entity)
          .title(m.title())
          .description(m.description())
          .weight((short) m.weight())
          .suggestedSessions((short) m.suggestedSessions())
          .sortOrder(i)
          .criteria(new ArrayList<>(m.criteria()))
          .build());
    }
    return entity;
  }

  private static PlanTemplate toDomain(PlanTemplateJpaEntity e) {
    return new PlanTemplate(
        e.getTemplateId(),
        e.getOwnerTutorId(),
        e.getSubjectId(),
        e.getEducationLevelId(),
        e.getGoalTagId(),
        e.getTitle(),
        e.getDescription(),
        e.getMilestones().stream()
            .map(m -> new PlanTemplate.TemplateMilestone(
                m.getTitle(), m.getDescription(), m.getWeight(), m.getSuggestedSessions(), List.copyOf(m.getCriteria())))
            .toList(),
        e.getCreatedBy(),
        e.getVersion(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }
}

package vn.edufit.progress.infra.persistence.adapter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edufit.progress.domain.model.Topic;
import vn.edufit.progress.domain.repository.TopicDomainRepository;
import vn.edufit.progress.infra.persistence.entity.TopicJpaEntity;
import vn.edufit.progress.infra.persistence.repository.SpringDataTopicRepository;

@Component
@RequiredArgsConstructor
public class TopicRepositoryAdapter implements TopicDomainRepository {

  private final SpringDataTopicRepository repository;

  @Override
  public Topic save(Topic topic) {
    return toDomain(repository.saveAndFlush(toEntity(topic)));
  }

  @Override
  public Optional<Topic> findById(UUID topicId) {
    return repository.findById(topicId).map(TopicRepositoryAdapter::toDomain);
  }

  @Override
  public List<Topic> findByIdIn(Collection<UUID> topicIds) {
    if (topicIds.isEmpty()) return List.of();
    return repository.findAllById(topicIds).stream().map(TopicRepositoryAdapter::toDomain).toList();
  }

  @Override
  public List<Topic> findAll() {
    return repository.findAllByOrderBySubjectIdAscEducationLevelIdAscSortOrderAscNameAsc().stream()
        .map(TopicRepositoryAdapter::toDomain).toList();
  }

  @Override
  public List<Topic> findActiveBySubjectAndLevel(int subjectId, int educationLevelId) {
    return repository.findBySubjectIdAndEducationLevelIdAndArchivedFalseOrderBySortOrderAscNameAsc(
        subjectId, educationLevelId).stream().map(TopicRepositoryAdapter::toDomain).toList();
  }

  private static TopicJpaEntity toEntity(Topic t) {
    return TopicJpaEntity.builder()
        .topicId(t.getTopicId())
        .subjectId(t.getSubjectId())
        .educationLevelId(t.getEducationLevelId())
        .name(t.getName())
        .sortOrder(t.getSortOrder())
        .archived(t.isArchived())
        .createdBy(t.getCreatedBy())
        .version(t.getVersion())
        .createdAt(t.getCreatedAt())
        .updatedAt(t.getUpdatedAt())
        .build();
  }

  private static Topic toDomain(TopicJpaEntity e) {
    return new Topic(e.getTopicId(), e.getSubjectId(), e.getEducationLevelId(), e.getName(), e.getSortOrder(),
        e.isArchived(), e.getCreatedBy(), e.getVersion(), e.getCreatedAt(), e.getUpdatedAt());
  }
}

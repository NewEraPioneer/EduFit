package vn.edufit.progress.infra.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edufit.progress.infra.persistence.entity.TopicJpaEntity;

@Repository
public interface SpringDataTopicRepository extends JpaRepository<TopicJpaEntity, UUID> {

  List<TopicJpaEntity> findAllByOrderBySubjectIdAscEducationLevelIdAscSortOrderAscNameAsc();

  List<TopicJpaEntity> findBySubjectIdAndEducationLevelIdAndArchivedFalseOrderBySortOrderAscNameAsc(
      Integer subjectId, Integer educationLevelId);
}

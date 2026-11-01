package vn.edufit.profile.infra.persistence.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edufit.profile.infra.persistence.entity.LearningNeed;

@Repository
public interface LearningNeedRepository extends JpaRepository<LearningNeed, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from LearningNeed n where n.needId = :needId")
  Optional<LearningNeed> findForUpdate(@Param("needId") UUID needId);

  List<LearningNeed> findByStudentIdOrderByCreatedAtAscNeedIdAsc(UUID studentId);
}

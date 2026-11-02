package vn.edufit.profile.infra.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edufit.profile.infra.persistence.entity.TutorOffering;

@Repository
public interface TutorOfferingRepository extends JpaRepository<TutorOffering, UUID> {

  List<TutorOffering> findByTutorIdOrderByCreatedAtAsc(UUID tutorId);

  List<TutorOffering> findByTutorIdIn(Collection<UUID> tutorIds);

  List<TutorOffering> findByOfferingIdInAndTutorId(Collection<UUID> offeringIds, UUID tutorId);

  Optional<TutorOffering> findByOfferingIdAndTutorId(UUID offeringId, UUID tutorId);

  boolean existsByTutorIdAndSubjectIdAndTrackId(UUID tutorId, Integer subjectId, Integer trackId);

  boolean existsByTutorIdAndSubjectIdAndTrackIdIsNull(UUID tutorId, Integer subjectId);

  boolean existsByTutorIdAndSubjectId(UUID tutorId, Integer subjectId);
}

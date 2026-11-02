package vn.edufit.profile.infra.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edufit.profile.infra.persistence.entity.SubjectTrack;

@Repository
public interface SubjectTrackRepository extends JpaRepository<SubjectTrack, Integer> {
}

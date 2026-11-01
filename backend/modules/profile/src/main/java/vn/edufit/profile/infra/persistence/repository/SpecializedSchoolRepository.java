package vn.edufit.profile.infra.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edufit.profile.infra.persistence.entity.SpecializedSchool;

@Repository
public interface SpecializedSchoolRepository extends JpaRepository<SpecializedSchool, Integer> {
}

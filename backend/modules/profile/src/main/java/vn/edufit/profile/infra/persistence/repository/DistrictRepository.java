package vn.edufit.profile.infra.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edufit.profile.infra.persistence.entity.District;

@Repository
public interface DistrictRepository extends JpaRepository<District, Integer> {
}

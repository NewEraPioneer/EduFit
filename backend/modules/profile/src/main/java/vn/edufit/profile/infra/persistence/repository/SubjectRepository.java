package vn.edufit.profile.infra.persistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edufit.profile.infra.persistence.entity.Subject;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Integer> {

  List<Subject> findByIsActiveTrueOrderByNameAsc();

  List<Subject> findAllByOrderByNameAsc();

  Optional<Subject> findByName(String name);

  boolean existsByName(String name);
}

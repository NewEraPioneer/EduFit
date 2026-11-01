package vn.edufit.connection.infra.persistence.repository;

import vn.edufit.connection.infra.persistence.entity.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TutoringClassRepository extends JpaRepository<TutoringClass, UUID> {
  boolean existsByStudentIdAndTutorIdAndStatus(UUID studentId, UUID tutorId, String status);
  boolean existsByStudentIdAndTutorIdAndNeedIdAndStatus(UUID studentId, UUID tutorId, UUID needId, String status);
  Optional<TutoringClass> findByConnectionRequestId(UUID requestId);
  Optional<TutoringClass> findByStudentIdAndTutorIdAndStatus(UUID studentId, UUID tutorId, String status);
  Optional<TutoringClass> findByStudentIdAndTutorIdAndNeedIdAndStatus(UUID studentId, UUID tutorId, UUID needId, String status);
  List<TutoringClass> findAllByStudentIdAndTutorIdAndStatus(UUID studentId, UUID tutorId, String status);
  List<TutoringClass> findByTutorIdAndStatus(UUID tutorId, String status);
  boolean existsByIdAndStatus(UUID classId, String status);
  boolean existsByIdAndTutorIdAndStudentIdAndStatus(UUID classId, UUID tutorId, UUID studentId, String status);
  Optional<TutoringClass> findByIdAndStatus(UUID classId, String status);
  List<TutoringClass> findByStudentId(UUID studentId);
  List<TutoringClass> findByTutorId(UUID tutorId);
  long countByStatus(String status);
}

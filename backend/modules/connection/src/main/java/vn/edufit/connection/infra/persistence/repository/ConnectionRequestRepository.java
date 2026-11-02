package vn.edufit.connection.infra.persistence.repository;

import vn.edufit.connection.infra.persistence.entity.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConnectionRequestRepository extends JpaRepository<ConnectionRequest, UUID>, FreshConnectionRowRepository {
  default Optional<ConnectionRequest> lockById(UUID id) {
    return lockFresh(ConnectionRequest.class, id);
  }

  long countByStudentIdAndStatus(UUID studentId, String status);
  List<ConnectionRequest> findByStudentIdAndStatus(UUID studentId, String status);
  List<ConnectionRequest> findByTutorIdAndStatus(UUID tutorId, String status);
  boolean existsByStudentIdAndTutorIdAndStatus(UUID studentId, UUID tutorId, String status);
  boolean existsByStudentIdAndTutorIdAndNeedIdAndStatus(UUID studentId, UUID tutorId, UUID needId, String status);
  List<ConnectionRequest> findByStudentIdOrderByCreatedAtDesc(UUID studentId);
  List<ConnectionRequest> findByTutorIdOrderByCreatedAtDesc(UUID tutorId);

  @Query(value = "select r from ConnectionRequest r where r.studentId = :studentId order by r.createdAt desc, r.id desc",
         countQuery = "select count(r) from ConnectionRequest r where r.studentId = :studentId")
  org.springframework.data.domain.Page<ConnectionRequest> findByStudentIdScoped(
      @org.springframework.data.repository.query.Param("studentId") UUID studentId,
      org.springframework.data.domain.Pageable pageable);

  @Query(value = "select r from ConnectionRequest r where r.tutorId = :tutorId order by r.createdAt desc, r.id desc",
         countQuery = "select count(r) from ConnectionRequest r where r.tutorId = :tutorId")
  org.springframework.data.domain.Page<ConnectionRequest> findByTutorIdScoped(
      @org.springframework.data.repository.query.Param("tutorId") UUID tutorId,
      org.springframework.data.domain.Pageable pageable);

  @Query("select r.id from ConnectionRequest r where r.status = 'PENDING' and r.expiresAt <= :now order by r.expiresAt asc, r.id asc")
  List<UUID> findCandidateExpiredIds(@org.springframework.data.repository.query.Param("now") java.time.Instant now, org.springframework.data.domain.Pageable pageable);
}

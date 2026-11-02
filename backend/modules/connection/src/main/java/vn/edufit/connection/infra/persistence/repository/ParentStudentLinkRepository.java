package vn.edufit.connection.infra.persistence.repository;

import vn.edufit.connection.infra.persistence.entity.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentStudentLinkRepository extends JpaRepository<ParentStudentLink, UUID>, FreshConnectionRowRepository {
  boolean existsByParentUserIdAndStudentIdAndStatus(UUID parentUserId, UUID studentId, String status);
  List<ParentStudentLink> findByParentUserIdAndStatus(UUID parentUserId, String status);
  List<ParentStudentLink> findByStudentIdAndStatus(UUID studentId, String status);

  @org.springframework.data.jpa.repository.Query(
      value = "select l from ParentStudentLink l where l.parentUserId = :parentUserId and l.status = 'CONFIRMED' order by l.confirmedAt desc, l.id desc",
      countQuery = "select count(l) from ParentStudentLink l where l.parentUserId = :parentUserId and l.status = 'CONFIRMED'")
  org.springframework.data.domain.Page<ParentStudentLink> findByParentUserIdAndStatusConfirmed(
      @org.springframework.data.repository.query.Param("parentUserId") UUID parentUserId,
      org.springframework.data.domain.Pageable pageable);

  @org.springframework.data.jpa.repository.Query(
      value = "select l from ParentStudentLink l where l.studentId = :studentId and l.status = 'CONFIRMED' order by l.confirmedAt desc, l.id desc",
      countQuery = "select count(l) from ParentStudentLink l where l.studentId = :studentId and l.status = 'CONFIRMED'")
  org.springframework.data.domain.Page<ParentStudentLink> findByStudentIdAndStatusConfirmed(
      @org.springframework.data.repository.query.Param("studentId") UUID studentId,
      org.springframework.data.domain.Pageable pageable);

  @org.springframework.data.jpa.repository.Query(
      value = "select l from ParentStudentLink l order by l.confirmedAt desc, l.id desc",
      countQuery = "select count(l) from ParentStudentLink l")
  org.springframework.data.domain.Page<ParentStudentLink> findAllLinks(org.springframework.data.domain.Pageable pageable);

  @org.springframework.data.jpa.repository.Query(
      value = "select l from ParentStudentLink l where l.status = :status order by l.confirmedAt desc, l.id desc",
      countQuery = "select count(l) from ParentStudentLink l where l.status = :status")
  org.springframework.data.domain.Page<ParentStudentLink> findByStatus(
      @org.springframework.data.repository.query.Param("status") String status,
      org.springframework.data.domain.Pageable pageable);

  long countByStatus(String status);

  @org.springframework.data.jpa.repository.Query("select count(distinct l.parentUserId) from ParentStudentLink l where l.status = :status")
  long countDistinctParentUserIdByStatus(@org.springframework.data.repository.query.Param("status") String status);

  @org.springframework.data.jpa.repository.Query("select count(distinct l.studentId) from ParentStudentLink l where l.status = :status")
  long countDistinctStudentIdByStatus(@org.springframework.data.repository.query.Param("status") String status);

  default Optional<ParentStudentLink> lockById(UUID id) {
    return lockFresh(ParentStudentLink.class, id);
  }
}

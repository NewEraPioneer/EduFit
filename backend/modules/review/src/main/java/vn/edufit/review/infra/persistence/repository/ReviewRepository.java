package vn.edufit.review.infra.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edufit.review.infra.persistence.entity.ReviewEntity;

@Repository
public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID> {

  boolean existsByTutorIdAndStudentId(UUID tutorId, UUID studentId);

  Optional<ReviewEntity> findByTutorIdAndStudentId(UUID tutorId, UUID studentId);

  Optional<ReviewEntity> findByTutorIdAndReviewerUserId(UUID tutorId, UUID reviewerUserId);

  Page<ReviewEntity> findByTutorId(UUID tutorId, Pageable pageable);

  List<ReviewEntity> findByTutorId(UUID tutorId);

  List<ReviewEntity> findByTutorIdIn(Collection<UUID> tutorIds);

  @Query("SELECT AVG(r.rating) FROM ReviewEntity r WHERE r.tutorId = :tutorId")
  Double findAverageRatingByTutorId(@Param("tutorId") UUID tutorId);

  @Query("SELECT COUNT(r) FROM ReviewEntity r WHERE r.tutorId = :tutorId")
  int countByTutorId(@Param("tutorId") UUID tutorId);

  @Query("SELECT r.rating, COUNT(r) FROM ReviewEntity r WHERE r.tutorId = :tutorId GROUP BY r.rating")
  List<Object[]> countRatingsByStar(@Param("tutorId") UUID tutorId);

  @Query("SELECT r.tutorId, AVG(r.rating), COUNT(r) FROM ReviewEntity r WHERE r.tutorId IN :tutorIds GROUP BY r.tutorId")
  List<Object[]> findRatingSummariesByTutorIds(@Param("tutorIds") Collection<UUID> tutorIds);
}

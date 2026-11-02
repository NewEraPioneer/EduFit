package vn.edufit.profile.infra.persistence.repository;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edufit.profile.infra.persistence.entity.TeachingMode;
import vn.edufit.profile.infra.persistence.entity.TutorProfile;
import vn.edufit.profile.infra.persistence.entity.TutorStatus;
import vn.edufit.profile.infra.persistence.entity.TutorType;

@Repository
public interface TutorProfileRepository extends JpaRepository<TutorProfile, UUID> {

  Optional<TutorProfile> findByUserId(UUID userId);

  boolean existsByUserId(UUID userId);

  List<TutorProfile> findByTutorIdInAndStatus(Collection<UUID> tutorIds, TutorStatus status);

  @Query("""
      select t from TutorProfile t
      where t.status = :status
        and exists (
          select 1 from TutorOffering o
          where o.tutorId = t.tutorId and o.subjectId = :subjectId
        )
      order by coalesce(t.ratingAvg, 0) desc, t.reviewCount desc, t.createdAt asc, t.tutorId asc
      """)
  List<TutorProfile> findVerifiedCandidatesBySubject(
      @Param("status") TutorStatus status,
      @Param("subjectId") Integer subjectId,
      Pageable pageable
  );

  /**
   * Lọc cứng theo tiêu chí người dùng tự chọn. Các điều kiện về gói dạy phải cùng thỏa trên một gói.
   * Loại gia sư chỉ lọc theo loại đã được admin xác nhận.
   */
  @Query("""
      select t
      from TutorProfile t
      where t.status = :status
        and (:tutorType is null or (t.tutorType = :tutorType and t.tutorTypeVerified = true))
        and (:mode is null or t.teachingMode = :mode or (:includeBothMode = true and t.teachingMode = :bothMode))
        and (:provinceId is null or t.provinceId = :provinceId)
        and (:districtId is null or :districtId member of t.districtIds)
        and (:minRating is null or t.ratingAvg >= :minRating)
        and (:keyword is null
          or lower(t.displayName) like lower(concat('%', cast(:keyword as String), '%'))
          or lower(t.headline) like lower(concat('%', cast(:keyword as String), '%'))
          or lower(t.bio) like lower(concat('%', cast(:keyword as String), '%'))
        )
        and (:filterOffering = false or exists (
          select 1
          from TutorOffering o
          where o.tutorId = t.tutorId
            and (:subjectId is null or o.subjectId = :subjectId)
            and (:trackId is null or o.trackId = :trackId)
            and (:levelId is null or :levelId member of o.levelIds)
            and (:textbookId is null or :textbookId member of o.textbookIds)
            and (:minPrice is null or o.pricePerSession >= :minPrice)
            and (:maxPrice is null or o.pricePerSession <= :maxPrice)
            and (:goalTagId is null or exists (
              select 1 from TutorOffering og join og.goals g
              where og.offeringId = o.offeringId and g.goalTagId = :goalTagId
            ))
        ))
        and (:dayOfWeek is null or exists (
          select 1
          from TutorAvailabilitySlot slot
          where slot.tutorId = t.tutorId
            and slot.dayOfWeek = :dayOfWeek
            and slot.startTime < :availableTo
            and slot.endTime > :availableFrom
        ))
      order by coalesce(t.ratingAvg, 0) desc, t.reviewCount desc, t.createdAt asc, t.tutorId asc
      """)
  List<TutorProfile> searchVerifiedTutors(
      @Param("status") TutorStatus status,
      @Param("tutorType") TutorType tutorType,
      @Param("mode") TeachingMode mode,
      @Param("includeBothMode") boolean includeBothMode,
      @Param("bothMode") TeachingMode bothMode,
      @Param("provinceId") Integer provinceId,
      @Param("districtId") Integer districtId,
      @Param("minRating") BigDecimal minRating,
      @Param("keyword") String keyword,
      @Param("filterOffering") boolean filterOffering,
      @Param("subjectId") Integer subjectId,
      @Param("trackId") Integer trackId,
      @Param("levelId") Integer levelId,
      @Param("textbookId") Integer textbookId,
      @Param("minPrice") Long minPrice,
      @Param("maxPrice") Long maxPrice,
      @Param("goalTagId") Integer goalTagId,
      @Param("dayOfWeek") Short dayOfWeek,
      @Param("availableFrom") LocalTime availableFrom,
      @Param("availableTo") LocalTime availableTo,
      Pageable pageable
  );
}

package vn.edufit.profile.infra.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edufit.profile.infra.persistence.entity.LearningNeedSlot;

@Repository
public interface LearningNeedSlotRepository extends JpaRepository<LearningNeedSlot, UUID> {

  List<LearningNeedSlot> findByNeedIdInOrderByDayOfWeekAscStartTimeAsc(Collection<UUID> needIds);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from LearningNeedSlot s where s.needId = :needId")
  void deleteForNeed(@Param("needId") UUID needId);
}

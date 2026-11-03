package vn.edufit.progress.infra.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edufit.progress.infra.persistence.entity.SessionMilestoneLinkJpaEntity;

@Repository
public interface SpringDataSessionMilestoneLinkRepository extends JpaRepository<SessionMilestoneLinkJpaEntity, UUID> {

  List<SessionMilestoneLinkJpaEntity> findBySessionIdIn(Collection<UUID> sessionIds);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("DELETE FROM SessionMilestoneLinkJpaEntity l WHERE l.sessionId = :sessionId AND l.linkType = :linkType")
  void deleteBySessionIdAndLinkType(@Param("sessionId") UUID sessionId, @Param("linkType") String linkType);
}

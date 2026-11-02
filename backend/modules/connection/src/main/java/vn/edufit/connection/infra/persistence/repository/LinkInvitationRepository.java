package vn.edufit.connection.infra.persistence.repository;

import vn.edufit.connection.infra.persistence.entity.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LinkInvitationRepository extends JpaRepository<LinkInvitation, UUID>, FreshConnectionRowRepository {
  default Optional<LinkInvitation> lockById(UUID id) {
    return lockFresh(LinkInvitation.class, id);
  }

  @Query("select i from LinkInvitation i where (i.inviterUserId = :a and i.invitedUserId = :b) or (i.inviterUserId = :b and i.invitedUserId = :a) order by i.createdAt desc")
  List<LinkInvitation> findPair(UUID a, UUID b);

  @Query("select i from LinkInvitation i where i.status = 'PENDING' and ((i.inviterUserId = :a and i.invitedUserId = :b) or (i.inviterUserId = :b and i.invitedUserId = :a))")
  List<LinkInvitation> findPendingPair(UUID a, UUID b);

  Optional<LinkInvitation> findFirstByInviterUserIdAndInvitedUserIdAndStatusOrderByReinviteAfterDesc(UUID inviter, UUID invited, String status);

  List<LinkInvitation> findByInviterUserIdOrInvitedUserIdOrderByCreatedAtDesc(UUID inviter, UUID invited);

  @Query(value = "select i from LinkInvitation i where i.inviterUserId = :userId or i.invitedUserId = :userId order by i.createdAt desc, i.id desc",
         countQuery = "select count(i) from LinkInvitation i where i.inviterUserId = :userId or i.invitedUserId = :userId")
  org.springframework.data.domain.Page<LinkInvitation> findScopedInvitations(
      @org.springframework.data.repository.query.Param("userId") UUID userId,
      org.springframework.data.domain.Pageable pageable);

  @Query("select i.id from LinkInvitation i where i.status = 'PENDING' and i.expiresAt <= :now order by i.expiresAt asc, i.id asc")
  List<UUID> findCandidateExpiredIds(@org.springframework.data.repository.query.Param("now") java.time.Instant now, org.springframework.data.domain.Pageable pageable);
}

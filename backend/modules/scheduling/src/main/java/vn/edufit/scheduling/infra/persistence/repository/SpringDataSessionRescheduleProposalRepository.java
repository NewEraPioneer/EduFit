package vn.edufit.scheduling.infra.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edufit.scheduling.infra.persistence.entity.SessionRescheduleProposalJpaEntity;

/**
 * Spring Data JPA Repository cho bảng {@code session_reschedule_proposal}.
 */
@Repository
public interface SpringDataSessionRescheduleProposalRepository
    extends JpaRepository<SessionRescheduleProposalJpaEntity, UUID> {

  /**
   * Tìm đề xuất đổi lịch đang PENDING của một buổi học (tối đa 1 đề xuất theo BR-44).
   */
  Optional<SessionRescheduleProposalJpaEntity> findBySessionIdAndStatus(UUID sessionId, String status);

  /**
   * Lấy toàn bộ đề xuất đổi lịch của một buổi học.
   */
  List<SessionRescheduleProposalJpaEntity> findBySessionIdOrderByCreatedAtDesc(UUID sessionId);

  /**
   * Quét các đề xuất đổi lịch PENDING đã quá hạn.
   */
  @Query("""
      SELECT p FROM SessionRescheduleProposalJpaEntity p
      WHERE p.status = 'PENDING' AND p.expiresAt < :now
  """)
  List<SessionRescheduleProposalJpaEntity> findExpiredPendingProposals(@Param("now") Instant now);
}

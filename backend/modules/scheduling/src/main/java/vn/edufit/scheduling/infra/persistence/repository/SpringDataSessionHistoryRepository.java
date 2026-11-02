package vn.edufit.scheduling.infra.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edufit.scheduling.infra.persistence.entity.SessionHistoryJpaEntity;

/**
 * Spring Data JPA Repository cho bảng {@code session_history}.
 */
@Repository
public interface SpringDataSessionHistoryRepository extends JpaRepository<SessionHistoryJpaEntity, UUID> {

  /**
   * Truy vấn toàn bộ lịch sử biến động của một buổi học xếp theo thời gian mới nhất trước.
   */
  List<SessionHistoryJpaEntity> findBySessionIdOrderByCreatedAtDesc(UUID sessionId);
}

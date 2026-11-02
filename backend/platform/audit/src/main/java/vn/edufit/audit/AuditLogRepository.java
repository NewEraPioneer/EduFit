package vn.edufit.audit;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository cho bảng nhật ký kiểm toán {@code audit_log}.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

  /**
   * Truy vấn tất cả nhật ký kiểm toán sắp xếp theo thời gian mới nhất.
   */
  Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

  /**
   * Truy vấn nhật ký kiểm toán theo hành động cụ thể (Action).
   */
  Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
}

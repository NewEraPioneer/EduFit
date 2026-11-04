package vn.edufit.notification.infra.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edufit.notification.infra.persistence.entity.Notification;

/**
 * Spring Data JPA Repository cho thực thể {@link Notification}.
 *
 * <p>Các phương thức truy vấn được tối ưu hóa để tận dụng chỉ mục:
 * {@code idx_notification_user_unread (user_id, is_read, created_at)}.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  /**
   * Truy vấn danh sách toàn bộ thông báo của người dùng theo phân trang, sắp xếp mới nhất lên đầu.
   */
  Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  /**
   * Truy vấn danh sách thông báo chưa đọc của người dùng theo phân trang, sắp xếp mới nhất lên đầu.
   */
  Page<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  /**
   * Tìm kiếm một thông báo theo ID và thuộc quyền sở hữu của người dùng chỉ định (chống IDOR).
   */
  Optional<Notification> findByNotificationIdAndUserId(UUID notificationId, UUID userId);

  /**
   * Đếm số lượng thông báo chưa đọc của người dùng (Index-Only Scan).
   */
  long countByUserIdAndIsReadFalse(UUID userId);

  /**
   * Đánh dấu toàn bộ thông báo chưa đọc của người dùng thành đã đọc bằng câu lệnh SQL bulk UPDATE.
   */
  @Modifying
  @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
  int markAllAsReadByUserId(@Param("userId") UUID userId);

  /**
   * Thống kê số lượng thông báo chưa đọc phân nhóm theo danh mục nghiệp vụ.
   */
  @Query("SELECT CASE " +
         "WHEN n.type LIKE 'SESSION%' OR n.type LIKE 'RESCHEDULE%' THEN 'SCHEDULE' " +
         "WHEN n.type LIKE 'CONNECTION%' THEN 'CONNECTION' " +
         "WHEN n.type LIKE 'LINK%' THEN 'LINK' " +
         "WHEN n.type LIKE 'PAYMENT%' THEN 'PAYMENT' " +
         "WHEN n.type LIKE 'REVIEW%' THEN 'REVIEW' " +
         "WHEN n.type LIKE 'KYC%' OR n.type LIKE 'VERIFICATION%' THEN 'VERIFICATION' " +
         "ELSE 'OTHER' END, COUNT(n) " +
         "FROM Notification n WHERE n.userId = :userId AND n.isRead = false " +
         "GROUP BY CASE " +
         "WHEN n.type LIKE 'SESSION%' OR n.type LIKE 'RESCHEDULE%' THEN 'SCHEDULE' " +
         "WHEN n.type LIKE 'CONNECTION%' THEN 'CONNECTION' " +
         "WHEN n.type LIKE 'LINK%' THEN 'LINK' " +
         "WHEN n.type LIKE 'PAYMENT%' THEN 'PAYMENT' " +
         "WHEN n.type LIKE 'REVIEW%' THEN 'REVIEW' " +
         "WHEN n.type LIKE 'KYC%' OR n.type LIKE 'VERIFICATION%' THEN 'VERIFICATION' " +
         "ELSE 'OTHER' END")
  List<Object[]> countUnreadByCategory(@Param("userId") UUID userId);
}

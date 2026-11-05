package vn.edufit.notification.application.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.notification.application.NotificationCategory;
import vn.edufit.notification.infra.persistence.entity.Notification;
import vn.edufit.notification.infra.persistence.repository.NotificationRepository;
import vn.edufit.notification.web.response.MarkAllReadResponse;
import vn.edufit.notification.web.response.MarkReadResponse;
import vn.edufit.notification.web.response.NotificationListResponse;
import vn.edufit.notification.web.response.UnreadCountResponse;
import vn.edufit.notification.web.response.UnreadSummaryResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Service điều phối nghiệp vụ và ranh giới giao dịch CSDL (@Transactional) của module Notification.
 */
@Service
public class NotificationService {

  private final NotificationRepository notificationRepository;

  public NotificationService(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  /**
   * Lấy danh sách thông báo của người dùng hiện tại có phân trang, hỗ trợ lọc thông báo chưa đọc.
   */
  @Transactional(readOnly = true)
  public NotificationListResponse getNotifications(
      CurrentUser currentUser,
      boolean unreadOnly,
      int page,
      int size
  ) {
    ensureAuthenticated(currentUser);
    UUID userId = currentUser.getUserId();
    int sanitizedPage = Math.max(page, 0);
    int sanitizedSize = Math.min(Math.max(size, 1), 100);
    PageRequest pageRequest = PageRequest.of(sanitizedPage, sanitizedSize);

    Page<Notification> notificationPage = unreadOnly
        ? notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageRequest)
        : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);

    long unreadCount = notificationRepository.countByUserIdAndIsReadFalse(userId);

    return NotificationListResponse.from(notificationPage, unreadCount);
  }

  /**
   * Đếm nhanh số lượng thông báo chưa đọc của người dùng hiện tại (cho chuông thông báo TopHeader).
   */
  @Transactional(readOnly = true)
  public UnreadCountResponse getUnreadCount(CurrentUser currentUser) {
    ensureAuthenticated(currentUser);
    long unreadCount = notificationRepository.countByUserIdAndIsReadFalse(currentUser.getUserId());
    return new UnreadCountResponse(unreadCount);
  }

  /**
   * Thống kê số lượng thông báo chưa đọc theo danh mục cho người dùng hiện tại (phục vụ sidebar dots).
   */
  @Transactional(readOnly = true)
  public UnreadSummaryResponse getUnreadSummary(CurrentUser currentUser) {
    ensureAuthenticated(currentUser);
    List<Object[]> results = notificationRepository.countUnreadByType(currentUser.getUserId());
    Map<String, Long> byCategory = new HashMap<>();
    long total = 0;
    for (Object[] row : results) {
      long count = ((Number) row[1]).longValue();
      byCategory.merge(NotificationCategory.of((String) row[0]).name(), count, Long::sum);
      total += count;
    }
    return new UnreadSummaryResponse(total, byCategory);
  }

  /**
   * Đánh dấu một thông báo cụ thể là đã đọc.
   *
   * @throws EntityNotFoundException nếu không có thông báo này của người dùng hiện tại (HTTP 404, kể cả khi thuộc tài khoản khác).
   */
  @Transactional
  public MarkReadResponse markAsRead(CurrentUser currentUser, UUID notificationId) {
    ensureAuthenticated(currentUser);
    // Truy vấn theo cả chủ sở hữu: thông báo của người khác trả 404, không lộ việc id có tồn tại.
    Notification notification = notificationRepository
        .findByNotificationIdAndUserId(notificationId, currentUser.getUserId())
        .orElseThrow(() -> EntityNotFoundException.of("Notification", notificationId));

    if (!notification.isRead()) {
      notification.markAsRead();
      notificationRepository.save(notification);
    }

    return new MarkReadResponse(notification.getNotificationId(), true);
  }

  /**
   * Đánh dấu toàn bộ thông báo chưa đọc của người dùng hiện tại là đã đọc.
   */
  @Transactional
  public MarkAllReadResponse markAllAsRead(CurrentUser currentUser) {
    ensureAuthenticated(currentUser);
    int updatedCount = notificationRepository.markAllAsReadByUserId(currentUser.getUserId());
    return new MarkAllReadResponse(updatedCount);
  }

  /**
   * Tạo bản ghi thông báo mới và lưu trữ vào cơ sở dữ liệu.
   *
   * <p>Mọi listener và {@code JpaNotificationSink} đều ghi thông báo qua hàm này (một đường ghi duy nhất).
   */
  @Transactional
  public Notification createNotification(
      UUID userId,
      UUID aboutStudentId,
      String type,
      String title,
      String shortText,
      String targetType,
      UUID targetId
  ) {
    return createNotification(userId, aboutStudentId, type, title, shortText, targetType, targetId, null);
  }

  /** Như trên nhưng giữ thời điểm xảy ra của sự kiện nguồn làm {@code createdAt} (null = bây giờ). */
  @Transactional
  public Notification createNotification(
      UUID userId,
      UUID aboutStudentId,
      String type,
      String title,
      String shortText,
      String targetType,
      UUID targetId,
      java.time.Instant occurredAt
  ) {
    Notification notification = occurredAt == null
        ? new Notification(userId, aboutStudentId, type, title, shortText, targetType, targetId)
        : new Notification(userId, aboutStudentId, type, title, shortText, targetType, targetId, occurredAt);
    return notificationRepository.save(notification);
  }

  private void ensureAuthenticated(CurrentUser currentUser) {
    if (currentUser == null || currentUser.getUserId() == null) {
      throw new InvalidOperationException(
          ErrorCode.UNAUTHORIZED,
          "Vui lòng đăng nhập để thực hiện thao tác"
      );
    }
  }
}

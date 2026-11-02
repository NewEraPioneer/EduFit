package vn.edufit.notification.application.service;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.notification.infra.persistence.entity.Notification;
import vn.edufit.notification.infra.persistence.repository.NotificationRepository;
import vn.edufit.notification.web.response.MarkAllReadResponse;
import vn.edufit.notification.web.response.MarkReadResponse;
import vn.edufit.notification.web.response.NotificationListResponse;
import vn.edufit.notification.web.response.UnreadCountResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ForbiddenOperationException;
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
   * Đánh dấu một thông báo cụ thể là đã đọc.
   *
   * @throws EntityNotFoundException nếu không tìm thấy thông báo theo ID (HTTP 404).
   * @throws ForbiddenOperationException nếu thông báo thuộc về tài khoản khác (HTTP 403).
   */
  @Transactional
  public MarkReadResponse markAsRead(CurrentUser currentUser, UUID notificationId) {
    ensureAuthenticated(currentUser);
    Notification notification = notificationRepository.findById(notificationId)
        .orElseThrow(() -> EntityNotFoundException.of("Notification", notificationId));

    if (!notification.getUserId().equals(currentUser.getUserId())) {
      throw new ForbiddenOperationException("Bạn không có quyền thao tác trên thông báo này");
    }

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
   * <p>Được gọi bởi {@code JpaNotificationSink} và {@code NotificationEventListener}.
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
    Notification notification = new Notification(
        userId,
        aboutStudentId,
        type,
        title,
        shortText,
        targetType,
        targetId
    );
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

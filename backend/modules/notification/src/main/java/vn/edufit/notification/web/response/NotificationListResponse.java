package vn.edufit.notification.web.response;

import java.util.List;
import org.springframework.data.domain.Page;
import vn.edufit.notification.infra.persistence.entity.Notification;

/**
 * DTO đại diện cho danh sách thông báo phân trang kèm số lượng chưa đọc.
 */
public record NotificationListResponse(
    List<NotificationResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    long unreadCount
) {

  public static NotificationListResponse from(Page<Notification> page, long unreadCount) {
    return new NotificationListResponse(
        page.getContent().stream().map(NotificationResponse::from).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        unreadCount
    );
  }
}

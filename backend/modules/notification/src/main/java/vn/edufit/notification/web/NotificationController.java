package vn.edufit.notification.web;

import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edufit.notification.application.service.NotificationService;
import vn.edufit.notification.web.response.MarkAllReadResponse;
import vn.edufit.notification.web.response.MarkReadResponse;
import vn.edufit.notification.web.response.NotificationListResponse;
import vn.edufit.notification.web.response.UnreadCountResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.response.ApiResponse;

/**
 * REST Controller cho phân hệ Notification, phục vụ giao diện người dùng (Next.js).
 *
 * <p>Đường dẫn cơ sở: {@code /api/v1/notifications}
 * <p>Tất cả các endpoint đều yêu cầu người dùng đã đăng nhập (Authenticated).
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final NotificationService notificationService;
  private final ObjectProvider<CurrentUser> currentUserProvider;

  public NotificationController(
      NotificationService notificationService,
      ObjectProvider<CurrentUser> currentUserProvider
  ) {
    this.notificationService = notificationService;
    this.currentUserProvider = currentUserProvider;
  }

  /**
   * Lấy danh sách thông báo của người dùng hiện tại có phân trang và tùy chọn lọc chưa đọc.
   */
  @GetMapping
  public ResponseEntity<ApiResponse<NotificationListResponse>> getNotifications(
      @RequestParam(defaultValue = "false") boolean unreadOnly,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size
  ) {
    NotificationListResponse response = notificationService.getNotifications(
        currentUser(),
        unreadOnly,
        page,
        size
    );
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy danh sách thông báo thành công"));
  }

  /**
   * Lấy số lượng thông báo chưa đọc của người dùng hiện tại (phục vụ huy hiệu chuông thông báo).
   */
  @GetMapping("/unread-count")
  public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount() {
    UnreadCountResponse response = notificationService.getUnreadCount(currentUser());
    return ResponseEntity.ok(ApiResponse.success(response, "Lấy số lượng thông báo chưa đọc thành công"));
  }

  /**
   * Đánh dấu một thông báo cụ thể là đã đọc theo ID.
   */
  @PatchMapping("/{id}/read")
  public ResponseEntity<ApiResponse<MarkReadResponse>> markAsRead(@PathVariable("id") UUID id) {
    MarkReadResponse response = notificationService.markAsRead(currentUser(), id);
    return ResponseEntity.ok(ApiResponse.success(response, "Đánh dấu thông báo đã đọc thành công"));
  }

  /**
   * Đánh dấu tất cả thông báo của người dùng hiện tại là đã đọc.
   */
  @PatchMapping("/read-all")
  public ResponseEntity<ApiResponse<MarkAllReadResponse>> markAllAsRead() {
    MarkAllReadResponse response = notificationService.markAllAsRead(currentUser());
    return ResponseEntity.ok(ApiResponse.success(response, "Đánh dấu tất cả thông báo đã đọc thành công"));
  }

  private CurrentUser currentUser() {
    return currentUserProvider.getIfAvailable();
  }
}

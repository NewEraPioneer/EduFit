package vn.edufit.notification.infra.adapter;

import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.edufit.notification.application.service.NotificationService;
import vn.edufit.shared.notification.NotificationSink;

/**
 * Hiện thực hóa interface {@link NotificationSink} từ Shared Kernel (platform/shared).
 *
 * <p>Cho phép các module nghiệp vụ khác phát thông báo mà không cần import bất kỳ class nào
 * thuộc package {@code vn.edufit.notification..}, bảo đảm tuân thủ quy tắc kiến trúc ArchUnit.
 */
@Service
public class JpaNotificationSink implements NotificationSink {

  private final NotificationService notificationService;

  public JpaNotificationSink(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @Override
  public void notify(
      UUID userId,
      UUID studentId,
      String type,
      String title,
      String text,
      String targetType,
      UUID targetId
  ) {
    notificationService.createNotification(
        userId,
        studentId,
        type,
        title,
        text,
        targetType,
        targetId
    );
  }
}

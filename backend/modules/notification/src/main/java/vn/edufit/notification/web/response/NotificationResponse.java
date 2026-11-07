package vn.edufit.notification.web.response;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.notification.application.NotificationCategory;
import vn.edufit.notification.infra.persistence.entity.Notification;

/**
 * DTO đại diện cho dữ liệu chi tiết của một thông báo gửi về Client.
 */
public record NotificationResponse(
    UUID notificationId,
    String type,
    String category,
    String title,
    String shortText,
    String targetType,
    UUID targetId,
    UUID aboutStudentId,
    boolean isRead,
    Instant createdAt
) {

  public static NotificationResponse from(Notification entity) {
    return new NotificationResponse(
        entity.getNotificationId(),
        entity.getType(),
        NotificationCategory.of(entity.getType()).name(),
        entity.getTitle(),
        entity.getShortText(),
        entity.getTargetType(),
        entity.getTargetId(),
        entity.getAboutStudentId(),
        entity.isRead(),
        entity.getCreatedAt()
    );
  }
}

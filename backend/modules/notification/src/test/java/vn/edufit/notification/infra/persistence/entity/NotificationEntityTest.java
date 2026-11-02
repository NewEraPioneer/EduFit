package vn.edufit.notification.infra.persistence.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationEntityTest {

  @Test
  @DisplayName("Khởi tạo Notification hợp lệ với các giá trị mặc định")
  void createNotification_validParameters_success() {
    UUID userId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    Notification notification = new Notification(
        userId,
        studentId,
        "SESSION_PROPOSED",
        "Đề xuất buổi học mới",
        "Gia sư đề xuất buổi học vào lúc 14:00",
        "TUTORING_SESSION",
        targetId
    );

    assertEquals(userId, notification.getUserId());
    assertEquals(studentId, notification.getAboutStudentId());
    assertEquals("SESSION_PROPOSED", notification.getType());
    assertEquals("Đề xuất buổi học mới", notification.getTitle());
    assertEquals("Gia sư đề xuất buổi học vào lúc 14:00", notification.getShortText());
    assertEquals("TUTORING_SESSION", notification.getTargetType());
    assertEquals(targetId, notification.getTargetId());
    assertFalse(notification.isRead(), "Mặc định thông báo mới tạo phải chưa đọc");
    assertNotNull(notification.getCreatedAt(), "createdAt phải được tự động sinh");
  }

  @Test
  @DisplayName("markAsRead cập nhật cờ isRead thành true")
  void markAsRead_setsIsReadToTrue() {
    Notification notification = new Notification(
        UUID.randomUUID(),
        null,
        "LINK_INVITED",
        "Lời mời liên kết",
        "Bạn có lời mời liên kết mới",
        "LINK_INVITATION",
        UUID.randomUUID()
    );

    assertFalse(notification.isRead());
    notification.markAsRead();
    assertTrue(notification.isRead());
  }

  @Test
  @DisplayName("Ném NullPointerException khi các trường bắt buộc bị null")
  void createNotification_nullRequiredFields_throwsNullPointerException() {
    UUID userId = UUID.randomUUID();

    assertThrows(NullPointerException.class, () ->
        new Notification(null, null, "TYPE", "TITLE", "TEXT", null, null));
    assertThrows(NullPointerException.class, () ->
        new Notification(userId, null, null, "TITLE", "TEXT", null, null));
    assertThrows(NullPointerException.class, () ->
        new Notification(userId, null, "TYPE", null, "TEXT", null, null));
    assertThrows(NullPointerException.class, () ->
        new Notification(userId, null, "TYPE", "TITLE", null, null, null));
  }
}

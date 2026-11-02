package vn.edufit.notification.infra.adapter;

import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.notification.application.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class JpaNotificationSinkTest {

  @Mock
  private NotificationService notificationService;

  private JpaNotificationSink sink;

  @BeforeEach
  void setUp() {
    sink = new JpaNotificationSink(notificationService);
  }

  @Test
  @DisplayName("notify: Chuyển tiếp đầy đủ tham số tới NotificationService.createNotification")
  void notify_delegatesToNotificationService() {
    UUID userId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    sink.notify(
        userId,
        studentId,
        "LINK_CONFIRMED",
        "Liên kết xác nhận",
        "Phụ huynh đã chấp nhận lời mời",
        "PARENT_STUDENT_LINK",
        targetId
    );

    verify(notificationService).createNotification(
        userId,
        studentId,
        "LINK_CONFIRMED",
        "Liên kết xác nhận",
        "Phụ huynh đã chấp nhận lời mời",
        "PARENT_STUDENT_LINK",
        targetId
    );
  }
}

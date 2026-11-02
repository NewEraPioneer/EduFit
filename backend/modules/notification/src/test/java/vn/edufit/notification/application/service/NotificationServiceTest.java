package vn.edufit.notification.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock
  private NotificationRepository notificationRepository;

  private NotificationService notificationService;

  private CurrentUser testUser;
  private UUID testUserId;

  @BeforeEach
  void setUp() {
    notificationService = new NotificationService(notificationRepository);

    testUserId = UUID.randomUUID();
    testUser = new CurrentUser() {
      @Override
      public UUID getUserId() {
        return testUserId;
      }

      @Override
      public String getEmail() {
        return "student@edufit.vn";
      }

      @Override
      public Set<String> getRoles() {
        return Set.of("STUDENT");
      }

      @Override
      public boolean hasRole(String role) {
        return "STUDENT".equalsIgnoreCase(role);
      }
    };
  }

  @Test
  @DisplayName("getNotifications: Lấy danh sách toàn bộ thông báo phân trang thành công")
  void getNotifications_unreadOnlyFalse_returnsPagedNotifications() {
    Notification notification = new Notification(
        testUserId,
        null,
        "LINK_INVITED",
        "Lời mời",
        "Nội dung lời mời",
        "LINK_INVITATION",
        UUID.randomUUID()
    );

    Page<Notification> page = new PageImpl<>(List.of(notification), PageRequest.of(0, 20), 1);
    when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(testUserId), any(PageRequest.class)))
        .thenReturn(page);
    when(notificationRepository.countByUserIdAndIsReadFalse(testUserId)).thenReturn(1L);

    NotificationListResponse response = notificationService.getNotifications(testUser, false, 0, 20);

    assertNotNull(response);
    assertEquals(1, response.items().size());
    assertEquals(1, response.totalElements());
    assertEquals(1L, response.unreadCount());
    assertEquals("LINK_INVITED", response.items().getFirst().type());
  }

  @Test
  @DisplayName("getNotifications: Lọc chỉ thông báo chưa đọc thành công")
  void getNotifications_unreadOnlyTrue_queriesUnreadOnly() {
    Notification notification = new Notification(
        testUserId,
        null,
        "SESSION_PROPOSED",
        "Đề xuất",
        "Nội dung đề xuất",
        "TUTORING_SESSION",
        UUID.randomUUID()
    );

    Page<Notification> page = new PageImpl<>(List.of(notification), PageRequest.of(0, 10), 1);
    when(notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(eq(testUserId), any(PageRequest.class)))
        .thenReturn(page);
    when(notificationRepository.countByUserIdAndIsReadFalse(testUserId)).thenReturn(1L);

    NotificationListResponse response = notificationService.getNotifications(testUser, true, 0, 10);

    assertNotNull(response);
    assertEquals(1, response.items().size());
    verify(notificationRepository).findByUserIdAndIsReadFalseOrderByCreatedAtDesc(eq(testUserId), any(PageRequest.class));
    verify(notificationRepository, never()).findByUserIdOrderByCreatedAtDesc(any(), any());
  }

  @Test
  @DisplayName("getNotifications: Chưa đăng nhập ném ngoại lệ UNAUTHORIZED")
  void getNotifications_unauthenticated_throwsUnauthorized() {
    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> notificationService.getNotifications(null, false, 0, 20)
    );
    assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
  }

  @Test
  @DisplayName("getUnreadCount: Trả về số lượng chưa đọc chính xác")
  void getUnreadCount_authenticated_returnsCount() {
    when(notificationRepository.countByUserIdAndIsReadFalse(testUserId)).thenReturn(5L);

    UnreadCountResponse response = notificationService.getUnreadCount(testUser);

    assertNotNull(response);
    assertEquals(5L, response.unreadCount());
  }

  @Test
  @DisplayName("markAsRead: Đánh dấu đã đọc thành công khi đúng chủ sở hữu")
  void markAsRead_validOwner_marksAsRead() {
    Notification notification = new Notification(
        testUserId,
        null,
        "LINK_INVITED",
        "Lời mời",
        "Nội dung",
        "LINK_INVITATION",
        UUID.randomUUID()
    );
    UUID notificationId = UUID.randomUUID();

    when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
    when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

    MarkReadResponse response = notificationService.markAsRead(testUser, notificationId);

    assertNotNull(response);
    assertTrue(response.isRead());
    assertTrue(notification.isRead());
    verify(notificationRepository).save(notification);
  }

  @Test
  @DisplayName("markAsRead: Ném EntityNotFoundException khi không tìm thấy thông báo")
  void markAsRead_notFound_throwsEntityNotFound() {
    UUID notFoundId = UUID.randomUUID();
    when(notificationRepository.findById(notFoundId)).thenReturn(Optional.empty());

    assertThrows(
        EntityNotFoundException.class,
        () -> notificationService.markAsRead(testUser, notFoundId)
    );
  }

  @Test
  @DisplayName("markAsRead: Ném ForbiddenOperationException khi cố đọc thông báo người khác")
  void markAsRead_differentUser_throwsForbidden() {
    UUID otherUserId = UUID.randomUUID();
    Notification notification = new Notification(
        otherUserId,
        null,
        "LINK_INVITED",
        "Lời mời",
        "Nội dung",
        "LINK_INVITATION",
        UUID.randomUUID()
    );
    UUID notificationId = UUID.randomUUID();

    when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

    assertThrows(
        ForbiddenOperationException.class,
        () -> notificationService.markAsRead(testUser, notificationId)
    );
    verify(notificationRepository, never()).save(any());
  }

  @Test
  @DisplayName("markAllAsRead: Gọi hàm bulk update của repository")
  void markAllAsRead_callsBulkUpdate() {
    when(notificationRepository.markAllAsReadByUserId(testUserId)).thenReturn(3);

    MarkAllReadResponse response = notificationService.markAllAsRead(testUser);

    assertNotNull(response);
    assertEquals(3, response.count());
    verify(notificationRepository).markAllAsReadByUserId(testUserId);
  }

  @Test
  @DisplayName("createNotification: Lưu thực thể mới vào cơ sở dữ liệu")
  void createNotification_validParameters_savesEntity() {
    UUID studentId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Notification result = notificationService.createNotification(
        testUserId,
        studentId,
        "CLASS_STARTED",
        "Lớp học đã bắt đầu",
        "Buổi học đầu tiên đã được lên lịch",
        "CLASS",
        targetId
    );

    assertNotNull(result);
    assertEquals(testUserId, result.getUserId());
    assertEquals(studentId, result.getAboutStudentId());
    assertEquals("CLASS_STARTED", result.getType());
    assertFalse(result.isRead());
    verify(notificationRepository).save(any(Notification.class));
  }
}

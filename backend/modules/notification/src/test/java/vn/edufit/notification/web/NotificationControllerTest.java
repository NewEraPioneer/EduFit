package vn.edufit.notification.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.edufit.notification.application.service.NotificationService;
import vn.edufit.notification.web.response.MarkAllReadResponse;
import vn.edufit.notification.web.response.MarkReadResponse;
import vn.edufit.notification.web.response.NotificationListResponse;
import vn.edufit.notification.web.response.UnreadCountResponse;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.response.ApiResponse;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

  @Mock
  private NotificationService notificationService;

  @Mock
  private ObjectProvider<CurrentUser> currentUserProvider;

  private NotificationController controller;

  private CurrentUser testUser;
  private UUID testUserId;

  @BeforeEach
  void setUp() {
    controller = new NotificationController(notificationService, currentUserProvider);

    testUserId = UUID.randomUUID();
    testUser = new CurrentUser() {
      @Override
      public UUID getUserId() {
        return testUserId;
      }

      @Override
      public String getEmail() {
        return "tutor@edufit.vn";
      }

      @Override
      public Set<String> getRoles() {
        return Set.of("TUTOR");
      }

      @Override
      public boolean hasRole(String role) {
        return "TUTOR".equalsIgnoreCase(role);
      }
    };
  }

  @Test
  @DisplayName("getNotifications: Trả về HTTP 200 kèm danh sách phân trang")
  void getNotifications_success_returns200() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);
    NotificationListResponse listResponse = new NotificationListResponse(List.of(), 0, 20, 0, 0, 0);
    when(notificationService.getNotifications(testUser, false, 0, 20)).thenReturn(listResponse);

    ResponseEntity<ApiResponse<NotificationListResponse>> response = controller.getNotifications(false, 0, 20);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().success());
    assertEquals(listResponse, response.getBody().data());
  }

  @Test
  @DisplayName("getUnreadCount: Trả về HTTP 200 kèm số lượng chưa đọc")
  void getUnreadCount_success_returns200() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);
    UnreadCountResponse unreadCountResponse = new UnreadCountResponse(4L);
    when(notificationService.getUnreadCount(testUser)).thenReturn(unreadCountResponse);

    ResponseEntity<ApiResponse<UnreadCountResponse>> response = controller.getUnreadCount();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().success());
    assertEquals(4L, response.getBody().data().unreadCount());
  }

  @Test
  @DisplayName("markAsRead: Trả về HTTP 200 kèm MarkReadResponse")
  void markAsRead_success_returns200() {
    UUID notificationId = UUID.randomUUID();
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);
    MarkReadResponse markReadResponse = new MarkReadResponse(notificationId, true);
    when(notificationService.markAsRead(testUser, notificationId)).thenReturn(markReadResponse);

    ResponseEntity<ApiResponse<MarkReadResponse>> response = controller.markAsRead(notificationId);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().success());
    assertTrue(response.getBody().data().isRead());
  }

  @Test
  @DisplayName("markAllAsRead: Trả về HTTP 200 kèm số lượng đã cập nhật")
  void markAllAsRead_success_returns200() {
    when(currentUserProvider.getIfAvailable()).thenReturn(testUser);
    MarkAllReadResponse markAllResponse = new MarkAllReadResponse(7);
    when(notificationService.markAllAsRead(testUser)).thenReturn(markAllResponse);

    ResponseEntity<ApiResponse<MarkAllReadResponse>> response = controller.markAllAsRead();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().success());
    assertEquals(7, response.getBody().data().count());
  }
}

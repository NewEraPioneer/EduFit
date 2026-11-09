package vn.edufit.connection;

import static org.mockito.Mockito.mock;

import java.util.Map;
import vn.edufit.connection.api.ClassScheduleStatusProvider;
import vn.edufit.connection.api.TutorScheduleConflictChecker;
import vn.edufit.connection.application.service.ConnectionExpiryService;

/**
 * Collaborator mặc định cho unit test của module Connection.
 *
 * <p>Service production yêu cầu đủ mọi collaborator nghiệp vụ; test chọn tường minh hành vi trung
 * tính thay vì truyền {@code null} để code production phải rẽ nhánh bỏ qua quy tắc.
 */
final class ConnectionTestDefaults {

  private ConnectionTestDefaults() {}

  /** Lịch của gia sư không trùng buổi học đã lên lịch nào. */
  static TutorScheduleConflictChecker noScheduleConflicts() {
    return mock(TutorScheduleConflictChecker.class);
  }

  /** Không lớp nào còn buổi học đã lên lịch. */
  static ClassScheduleStatusProvider noScheduledSessions() {
    return classIds -> Map.of();
  }

  /** Expiry service giả cho test không kiểm tra luồng hết hạn; test cần kiểm tra thì verify trên mock này. */
  static ConnectionExpiryService expiryMock() {
    return mock(ConnectionExpiryService.class);
  }
}

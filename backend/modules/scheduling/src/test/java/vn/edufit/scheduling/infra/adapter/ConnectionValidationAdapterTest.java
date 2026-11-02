package vn.edufit.scheduling.infra.adapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.connection.api.ConnectionFacade;

/**
 * Unit Test cho {@link ConnectionValidationAdapter}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Adapter: Xác thực trạng thái kết nối lớp học (ConnectionValidationAdapter)")
class ConnectionValidationAdapterTest {

  @Mock
  private ConnectionFacade connectionFacade;

  private ConnectionValidationAdapter adapter;

  private final UUID classId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    adapter = new ConnectionValidationAdapter(connectionFacade);
  }

  @Test
  @DisplayName("Trả về true khi ConnectionFacade xác nhận lớp học ACTIVE")
  void shouldReturnTrueWhenClassActive() {
    when(connectionFacade.isClassActive(classId, tutorId, studentId)).thenReturn(true);

    boolean result = adapter.isConnectionActive(classId, tutorId, studentId);

    assertTrue(result);
    verify(connectionFacade).isClassActive(classId, tutorId, studentId);
  }

  @Test
  @DisplayName("Trả về false khi ConnectionFacade báo lớp học không ACTIVE hoặc không khớp thành viên")
  void shouldReturnFalseWhenClassNotActive() {
    when(connectionFacade.isClassActive(classId, tutorId, studentId)).thenReturn(false);

    boolean result = adapter.isConnectionActive(classId, tutorId, studentId);

    assertFalse(result);
    verify(connectionFacade).isClassActive(classId, tutorId, studentId);
  }
}

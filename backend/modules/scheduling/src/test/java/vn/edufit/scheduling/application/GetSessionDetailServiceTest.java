package vn.edufit.scheduling.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.scheduling.application.service.GetSessionDetailService;
import vn.edufit.scheduling.application.service.SchedulingSecurityResolver;
import vn.edufit.scheduling.application.service.SimpleCurrentUser;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ForbiddenOperationException;

/**
 * Unit Test cho {@link GetSessionDetailService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Service: Lấy chi tiết buổi học có phân quyền (GetSessionDetailService)")
class GetSessionDetailServiceTest {

  @Mock
  private TutoringSessionDomainRepository sessionRepository;

  @Mock
  private SchedulingSecurityResolver schedulingSecurityResolver;

  private GetSessionDetailService service;

  private final UUID sessionId = UUID.randomUUID();
  private final UUID classId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-10-25T10:00:00Z");

  private CurrentUser actor;
  private TutoringSession session;

  @BeforeEach
  void setUp() {
    service = new GetSessionDetailService(sessionRepository, schedulingSecurityResolver);
    actor = SimpleCurrentUser.of(userId, "STUDENT");

    session = new TutoringSession(
        sessionId, classId, tutorId, studentId,
        now.plusSeconds(3600), now.plusSeconds(7200), SessionMode.ONLINE,
        "https://meet.google.com/abc", null, null, SessionStatus.SCHEDULED,
        studentId, tutorId, now, null, now.plusSeconds(1800),
        null, null, null, null, false, null, null, 1, now
    );
  }

  @Test
  @DisplayName("Lấy chi tiết buổi học thành công khi người dùng có quyền")
  void shouldReturnSessionWhenAuthorized() {
    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

    TutoringSession result = service.getSessionDetail(sessionId, actor);

    assertNotNull(result);
    assertEquals(sessionId, result.getSessionId());
    verify(schedulingSecurityResolver).requireSessionAccess(actor, session);
  }

  @Test
  @DisplayName("Ném EntityNotFoundException khi không tìm thấy buổi học theo ID")
  void shouldThrowEntityNotFoundWhenMissing() {
    when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

    assertThrows(EntityNotFoundException.class, () -> service.getSessionDetail(sessionId, actor));
  }

  @Test
  @DisplayName("Ném ForbiddenOperationException khi người dùng không có quyền xem buổi học")
  void shouldThrowForbiddenWhenUnauthorized() {
    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    doThrow(new ForbiddenOperationException("Bạn không có quyền truy cập thông tin buổi học này"))
        .when(schedulingSecurityResolver).requireSessionAccess(actor, session);

    assertThrows(ForbiddenOperationException.class, () -> service.getSessionDetail(sessionId, actor));
  }
}

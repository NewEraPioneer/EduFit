package vn.edufit.scheduling.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.scheduling.application.service.SchedulingSecurityResolver;
import vn.edufit.scheduling.application.service.SimpleCurrentUser;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Unit Test toàn diện cho {@link SchedulingSecurityResolver}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test Service: Giải quyết định danh và bảo mật (SchedulingSecurityResolver)")
class SchedulingSecurityResolverTest {

  @Mock
  private ProfileFacade profileFacade;

  @Mock
  private ConnectionFacade connectionFacade;

  private SchedulingSecurityResolver resolver;

  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID studentUserId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID parentUserId = UUID.randomUUID();
  private final UUID strangerUserId = UUID.randomUUID();
  private final UUID adminUserId = UUID.randomUUID();
  private final UUID classId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-10-25T10:00:00Z");

  private CurrentUser tutorActor;
  private CurrentUser studentActor;
  private CurrentUser parentActor;
  private CurrentUser strangerActor;
  private CurrentUser adminActor;

  @BeforeEach
  void setUp() {
    resolver = new SchedulingSecurityResolver(profileFacade, connectionFacade);

    tutorActor = SimpleCurrentUser.of(tutorUserId, "TUTOR");
    studentActor = SimpleCurrentUser.of(studentUserId, "STUDENT");
    parentActor = SimpleCurrentUser.of(parentUserId, "PARENT");
    strangerActor = SimpleCurrentUser.of(strangerUserId, "STUDENT");
    adminActor = SimpleCurrentUser.of(adminUserId, "ADMIN");
  }

  @Test
  void unrelatedStudentCannotReadClassSchedule() {
    when(connectionFacade.findClassById(classId)).thenReturn(Optional.of(new ConnectionFacade.ClassDetailView(
        classId, studentId, tutorId, UUID.randomUUID(), "ACTIVE", now, null, 250000)));
    when(profileFacade.findStudentByUserId(strangerUserId)).thenReturn(Optional.empty());
    assertThrows(ForbiddenOperationException.class, () -> resolver.requireClassAccess(strangerActor, classId));
  }

  private TutoringSession createSession(UUID proposedBy) {
    return new TutoringSession(
        sessionId, classId, tutorId, studentId,
        now.plusSeconds(3600), now.plusSeconds(7200), SessionMode.ONLINE,
        null, null, null, SessionStatus.PROPOSED,
        proposedBy, null, null, null, now.plusSeconds(1800),
        null, null, null, null, false, null, null, 0, now
    );
  }

  @Test
  @DisplayName("resolveTutorId -> Thành công khi có hồ sơ gia sư")
  void shouldResolveTutorIdSuccessfully() {
    TutorSummaryDto tutorDto = new TutorSummaryDto(tutorId, tutorUserId, "Thầy Nam", null, null, null, null, false, null, null, null, null, "VERIFIED", now, null, 0);
    when(profileFacade.findTutorByUserId(tutorUserId)).thenReturn(Optional.of(tutorDto));

    UUID resolved = resolver.resolveTutorId(tutorActor);
    assertEquals(tutorId, resolved);
  }

  @Test
  @DisplayName("resolveTutorId -> Ném Forbidden khi người dùng không có hồ sơ gia sư")
  void shouldThrowForbiddenWhenNoTutorProfile() {
    when(profileFacade.findTutorByUserId(strangerUserId)).thenReturn(Optional.empty());

    assertThrows(ForbiddenOperationException.class, () -> resolver.resolveTutorId(strangerActor));
  }

  @Test
  @DisplayName("resolveStudentId -> Thành công khi có hồ sơ học sinh")
  void shouldResolveStudentIdSuccessfully() {
    StudentSummaryDto studentDto = new StudentSummaryDto(studentId, studentUserId, 1, null, null, true);
    when(profileFacade.findStudentByUserId(studentUserId)).thenReturn(Optional.of(studentDto));

    UUID resolved = resolver.resolveStudentId(studentActor);
    assertEquals(studentId, resolved);
  }

  @Test
  @DisplayName("resolveStudentId -> Ném Forbidden khi người dùng không có hồ sơ học sinh")
  void shouldThrowForbiddenWhenNoStudentProfile() {
    when(profileFacade.findStudentByUserId(strangerUserId)).thenReturn(Optional.empty());

    assertThrows(ForbiddenOperationException.class, () -> resolver.resolveStudentId(strangerActor));
  }

  @Test
  @DisplayName("requireStudentOrParentAccess -> Cho phép chính học sinh truy cập")
  void shouldAllowStudentAccessOwnStudentId() {
    StudentSummaryDto studentDto = new StudentSummaryDto(studentId, studentUserId, 1, null, null, true);
    when(profileFacade.findStudentByUserId(studentUserId)).thenReturn(Optional.of(studentDto));

    assertDoesNotThrow(() -> resolver.requireStudentOrParentAccess(studentActor, studentId));
  }

  @Test
  @DisplayName("requireStudentOrParentAccess -> Cho phép Phụ huynh có liên kết xác nhận truy cập")
  void shouldAllowParentWithConfirmedLink() {
    when(connectionFacade.hasConfirmedParentLink(parentUserId, studentId)).thenReturn(true);

    assertDoesNotThrow(() -> resolver.requireStudentOrParentAccess(parentActor, studentId));
  }

  @Test
  @DisplayName("requireStudentOrParentAccess -> Ném Forbidden khi Phụ huynh chưa có liên kết xác nhận")
  void shouldThrowForbiddenWhenParentNotLinked() {
    when(connectionFacade.hasConfirmedParentLink(parentUserId, studentId)).thenReturn(false);

    assertThrows(ForbiddenOperationException.class, () ->
        resolver.requireStudentOrParentAccess(parentActor, studentId));
  }

  @Test
  @DisplayName("requireStudentOrParentAccess -> Cho phép Admin truy cập")
  void shouldAllowAdminAccessStudent() {
    assertDoesNotThrow(() -> resolver.requireStudentOrParentAccess(adminActor, studentId));
  }

  @Test
  @DisplayName("requireSessionAccess -> Cho phép Gia sư của buổi học xem chi tiết")
  void shouldAllowTutorToAccessSession() {
    TutoringSession session = createSession(studentUserId);
    TutorSummaryDto tutorDto = new TutorSummaryDto(tutorId, tutorUserId, "Thầy Nam", null, null, null, null, false, null, null, null, null, "VERIFIED", now, null, 0);
    when(profileFacade.findTutorByUserId(tutorUserId)).thenReturn(Optional.of(tutorDto));

    assertDoesNotThrow(() -> resolver.requireSessionAccess(tutorActor, session));
  }

  @Test
  @DisplayName("requireSessionAccess -> Cho phép Phụ huynh có liên kết xem chi tiết buổi học")
  void shouldAllowParentWithLinkToAccessSession() {
    TutoringSession session = createSession(tutorUserId);
    when(connectionFacade.hasConfirmedParentLink(parentUserId, studentId)).thenReturn(true);

    assertDoesNotThrow(() -> resolver.requireSessionAccess(parentActor, session));
  }

  @Test
  @DisplayName("requireSessionAccess -> Ném Forbidden khi người lạ cố xem chi tiết")
  void shouldThrowForbiddenWhenStrangerAccessesSession() {
    TutoringSession session = createSession(tutorUserId);

    assertThrows(ForbiddenOperationException.class, () -> resolver.requireSessionAccess(strangerActor, session));
  }

  @Test
  @DisplayName("requireCounterpartyResponseAccess -> Ném Forbidden nếu tự phản hồi đề xuất của chính mình")
  void shouldThrowForbiddenWhenSelfResponding() {
    TutoringSession session = createSession(tutorUserId);

    assertThrows(ForbiddenOperationException.class, () ->
        resolver.requireCounterpartyResponseAccess(tutorActor, session));
  }

  @Test
  @DisplayName("requireCounterpartyResponseAccess -> Gia sư đề xuất -> Học sinh phản hồi thành công")
  void shouldAllowStudentToRespondToTutorProposal() {
    TutoringSession session = createSession(tutorUserId);
    StudentSummaryDto studentDto = new StudentSummaryDto(studentId, studentUserId, 1, null, null, true);
    when(profileFacade.findTutorByUserId(studentUserId)).thenReturn(Optional.empty());
    when(profileFacade.findStudentByUserId(studentUserId)).thenReturn(Optional.of(studentDto));

    // Proposer is tutor
    TutorSummaryDto tutorDto = new TutorSummaryDto(tutorId, tutorUserId, "Thầy Nam", null, null, null, null, false, null, null, null, null, "VERIFIED", now, null, 0);
    when(profileFacade.findTutorByUserId(tutorUserId)).thenReturn(Optional.of(tutorDto));

    assertDoesNotThrow(() -> resolver.requireCounterpartyResponseAccess(studentActor, session));
  }

  @Test
  @DisplayName("requireCounterpartyResponseAccess -> Gia sư đề xuất -> Phụ huynh có link phản hồi thành công")
  void shouldAllowParentToRespondToTutorProposal() {
    TutoringSession session = createSession(tutorUserId);
    when(profileFacade.findTutorByUserId(parentUserId)).thenReturn(Optional.empty());
    when(profileFacade.findStudentByUserId(parentUserId)).thenReturn(Optional.empty());
    when(connectionFacade.hasConfirmedParentLink(parentUserId, studentId)).thenReturn(true);

    TutorSummaryDto tutorDto = new TutorSummaryDto(tutorId, tutorUserId, "Thầy Nam", null, null, null, null, false, null, null, null, null, "VERIFIED", now, null, 0);
    when(profileFacade.findTutorByUserId(tutorUserId)).thenReturn(Optional.of(tutorDto));

    assertDoesNotThrow(() -> resolver.requireCounterpartyResponseAccess(parentActor, session));
  }

  @Test
  @DisplayName("requireCounterpartyResponseAccess -> Học sinh đề xuất -> Gia sư phản hồi thành công")
  void shouldAllowTutorToRespondToStudentProposal() {
    TutoringSession session = createSession(studentUserId);
    TutorSummaryDto tutorDto = new TutorSummaryDto(tutorId, tutorUserId, "Thầy Nam", null, null, null, null, false, null, null, null, null, "VERIFIED", now, null, 0);
    when(profileFacade.findTutorByUserId(tutorUserId)).thenReturn(Optional.of(tutorDto));
    // studentUserId is not a tutor
    when(profileFacade.findTutorByUserId(studentUserId)).thenReturn(Optional.empty());

    assertDoesNotThrow(() -> resolver.requireCounterpartyResponseAccess(tutorActor, session));
  }

  @Test
  @DisplayName("requireCounterpartyResponseAccess -> Ném Forbidden khi người thứ 3 cố phản hồi")
  void shouldThrowForbiddenWhenStrangerResponds() {
    TutoringSession session = createSession(tutorUserId);

    assertThrows(ForbiddenOperationException.class, () ->
        resolver.requireCounterpartyResponseAccess(strangerActor, session));
  }

  @Test
  @DisplayName("resolveCalendarProfileId -> Phụ huynh cung cấp studentId được xác thực hợp lệ")
  void shouldResolveCalendarProfileIdForParentWithStudentId() {
    when(connectionFacade.hasConfirmedParentLink(parentUserId, studentId)).thenReturn(true);

    UUID resolved = resolver.resolveCalendarProfileId(parentActor, studentId);
    assertEquals(studentId, resolved);
  }

  @Test
  @DisplayName("resolveCalendarProfileId -> Phụ huynh không cung cấp studentId ném InvalidOperationException")
  void shouldThrowInvalidOperationWhenParentOmitsStudentId() {
    assertThrows(InvalidOperationException.class, () ->
        resolver.resolveCalendarProfileId(parentActor, null));
  }

  @Test
  @DisplayName("requireTutorAccess -> Chỉ Gia sư của buổi học mới được phép ghi nhận kết quả")
  void shouldEnforceTutorAccessForOutcome() {
    TutorSummaryDto tutorDto = new TutorSummaryDto(tutorId, tutorUserId, "Thầy Nam", null, null, null, null, false, null, null, null, null, "VERIFIED", now, null, 0);
    when(profileFacade.findTutorByUserId(tutorUserId)).thenReturn(Optional.of(tutorDto));

    assertDoesNotThrow(() -> resolver.requireTutorAccess(tutorActor, tutorId));
    assertThrows(ForbiddenOperationException.class, () -> resolver.requireTutorAccess(studentActor, tutorId));
  }
}

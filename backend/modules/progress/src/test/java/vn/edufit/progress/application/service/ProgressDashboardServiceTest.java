package vn.edufit.progress.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.progress.application.dto.ProgressViews.ParentDashboardView;
import vn.edufit.progress.application.dto.ProgressViews.StudentDashboardView;
import vn.edufit.progress.application.dto.ProgressViews.TeachingHistoryItemView;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.model.SessionNote;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.progress.domain.repository.SessionNoteDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.ClassSessionStatsView;
import vn.edufit.scheduling.api.dto.SessionTimelineView;
import vn.edufit.shared.auth.CurrentUser;

class ProgressDashboardServiceTest {

  private LearningPlanDomainRepository planRepository;
  private SessionNoteDomainRepository noteRepository;
  private ConnectionFacade connectionFacade;
  private ProfileFacade profileFacade;
  private IamFacade iamFacade;
  private SessionFacade sessionFacade;
  private Clock fixedClock;
  private ProgressDashboardService dashboardService;

  private final Instant fixedInstant = Instant.parse("2026-10-20T10:00:00Z");

  @BeforeEach
  void setUp() {
    planRepository = mock(LearningPlanDomainRepository.class);
    noteRepository = mock(SessionNoteDomainRepository.class);
    connectionFacade = mock(ConnectionFacade.class);
    profileFacade = mock(ProfileFacade.class);
    iamFacade = mock(IamFacade.class);
    sessionFacade = mock(SessionFacade.class);
    fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

    dashboardService = new ProgressDashboardService(
        planRepository, noteRepository, connectionFacade, profileFacade, iamFacade, sessionFacade, fixedClock
    );
  }

  @Test
  @DisplayName("getStudentDashboard: Tra ve thong tin tien do, bieu do series va ghi chu")
  void getStudentDashboard_Success() {
    UUID studentId = UUID.randomUUID();
    UUID studentUserId = UUID.randomUUID();
    UUID classId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID tutorUserId = UUID.randomUUID();
    UUID needId = UUID.randomUUID();

    CurrentUser actor = mock(CurrentUser.class);
    when(actor.hasRole("STUDENT")).thenReturn(true);
    when(actor.getUserId()).thenReturn(studentUserId);
    when(profileFacade.getStudentIdByUserId(studentUserId)).thenReturn(studentId);

    StudentSummaryDto studentDto = mock(StudentSummaryDto.class);
    when(studentDto.userId()).thenReturn(studentUserId);
    when(profileFacade.findStudentById(studentId)).thenReturn(Optional.of(studentDto));

    UserSummaryView studentUserSummary = mock(UserSummaryView.class);
    when(studentUserSummary.fullName()).thenReturn("Nguyen Van B");
    when(iamFacade.findUserSummaryById(studentUserId)).thenReturn(Optional.of(studentUserSummary));

    ConnectionFacade.ClassDetailView cls = new ConnectionFacade.ClassDetailView(classId, studentId, tutorId, needId, "ACTIVE", fixedInstant, null, 200000L);
    when(connectionFacade.findClassesByStudent(studentId)).thenReturn(List.of(cls));

    Milestone m1 = new Milestone(
        UUID.randomUUID(), UUID.randomUUID(), "M1", "Desc", LocalDate.parse("2026-10-15"),
        2, MilestoneStatus.ACHIEVED, LocalDate.parse("2026-10-15"), false, 0, fixedInstant
    );
    Milestone m2 = new Milestone(
        UUID.randomUUID(), UUID.randomUUID(), "M2", "Desc", LocalDate.parse("2026-10-25"),
        2, MilestoneStatus.IN_PROGRESS, null, false, 1, fixedInstant
    );
    LearningPlan plan = new LearningPlan(
        UUID.randomUUID(), classId, "Plan Summary", 0, fixedInstant, fixedInstant, List.of(m1, m2)
    );
    when(planRepository.findByClassIdIn(eq(List.of(classId)))).thenReturn(List.of(plan));
    when(planRepository.findByClassId(classId)).thenReturn(Optional.of(plan));

    LearningNeedView needView = mock(LearningNeedView.class);
    when(needView.goalTagName()).thenReturn("Chứng chỉ IELTS 5.0–6.5");
    when(needView.subjectName()).thenReturn("Tiếng Anh");
    when(needView.educationLevelName()).thenReturn("Lớp 9");
    when(profileFacade.findLearningNeeds(List.of(needId))).thenReturn(Map.of(needId, needView));

    TutorSummaryDto tutorDto = mock(TutorSummaryDto.class);
    when(tutorDto.userId()).thenReturn(tutorUserId);
    when(profileFacade.findTutorById(tutorId)).thenReturn(Optional.of(tutorDto));

    UserSummaryView tutorUserSummary = mock(UserSummaryView.class);
    when(tutorUserSummary.fullName()).thenReturn("Tran Thi C");
    when(iamFacade.findUserSummaryById(tutorUserId)).thenReturn(Optional.of(tutorUserSummary));

    ClassSessionStatsView stats = new ClassSessionStatsView(classId, 5, 0, 0, 2, fixedInstant.plusSeconds(86400), fixedInstant.minusSeconds(86400));
    when(sessionFacade.getSessionStatsByClassIds(eq(List.of(classId)), any(), any())).thenReturn(Map.of(classId, stats));

    UUID sessionId = UUID.randomUUID();
    SessionTimelineView sessView = new SessionTimelineView(sessionId, classId, fixedInstant.minusSeconds(7200), fixedInstant.minusSeconds(3600), "COMPLETED");
    when(sessionFacade.findSessionsByClassId(classId, null, null)).thenReturn(List.of(sessView));

    SessionNote note = new SessionNote(UUID.randomUUID(), sessionId, tutorId, "Ghi chu hoc tap", fixedInstant.minusSeconds(3500));
    when(noteRepository.findBySessionIdIn(List.of(sessionId))).thenReturn(List.of(note));

    StudentDashboardView response = dashboardService.getStudentDashboard(actor, studentId, classId, "30D");

    assertThat(response).isNotNull();
    assertThat(response.studentId()).isEqualTo(studentId);
    assertThat(response.studentName()).isEqualTo("Nguyen Van B");
    assertThat(response.tutorName()).isEqualTo("Tran Thi C");
    assertThat(response.progressPercentage()).isEqualTo(50); // 2/4 = 50%
    assertThat(response.recentNotes()).hasSize(1);
    assertThat(response.recentNotes().getFirst().noteText()).isEqualTo("Ghi chu hoc tap");
    assertThat(response.progressSeries()).isNotEmpty();
  }

  @Test
  @DisplayName("getTeachingHistory: Phan trang danh sach lop truoc khi hydrate metadata")
  void getTeachingHistory_PaginationBeforeHydration() {
    UUID tutorId = UUID.randomUUID();
    UUID tutorUserId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID studentUserId = UUID.randomUUID();

    CurrentUser actor = mock(CurrentUser.class);
    when(actor.hasRole("TUTOR")).thenReturn(true);
    when(actor.getUserId()).thenReturn(tutorUserId);
    when(profileFacade.getTutorIdByUserId(tutorUserId)).thenReturn(tutorId);

    UUID class1 = UUID.randomUUID();
    UUID class2 = UUID.randomUUID();
    ConnectionFacade.ClassDetailView c1 = new ConnectionFacade.ClassDetailView(class1, studentId, tutorId, UUID.randomUUID(), "ACTIVE", fixedInstant, null, 200000L);
    ConnectionFacade.ClassDetailView c2 = new ConnectionFacade.ClassDetailView(class2, studentId, tutorId, UUID.randomUUID(), "COMPLETED", fixedInstant, null, 200000L);

    when(connectionFacade.findClassesByTutor(tutorId)).thenReturn(List.of(c1, c2));

    StudentSummaryDto studentDto = mock(StudentSummaryDto.class);
    when(studentDto.userId()).thenReturn(studentUserId);
    when(profileFacade.findStudentById(studentId)).thenReturn(Optional.of(studentDto));

    UserSummaryView studentUserSummary = mock(UserSummaryView.class);
    when(studentUserSummary.fullName()).thenReturn("Hoc sinh D");
    when(iamFacade.findUserSummaryById(studentUserId)).thenReturn(Optional.of(studentUserSummary));

    when(sessionFacade.getSessionStatsByClassIds(any(), any(), any())).thenReturn(Map.of());
    when(planRepository.findByClassIdIn(any())).thenReturn(List.of());
    when(profileFacade.findLearningNeeds(any())).thenReturn(Map.of());

    // Yeu cau trang 0 voi size 1
    Page<TeachingHistoryItemView> page = dashboardService.getTeachingHistory(actor, 0, 1, null, null, null, null);

    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().getFirst().classId()).isEqualTo(class1);
    assertThat(page.getContent().getFirst().studentName()).isEqualTo("Hoc sinh D");
  }

  @Test
  @DisplayName("getParentDashboard: Tra ve thong tin con cai cho tai khoan PHU HUYNH")
  void getParentDashboard_Success() {
    UUID parentUserId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();

    CurrentUser actor = mock(CurrentUser.class);
    when(actor.hasRole("PARENT")).thenReturn(true);
    when(actor.getUserId()).thenReturn(parentUserId);

    when(connectionFacade.findConfirmedStudentIds(parentUserId)).thenReturn(List.of(studentId));
    when(connectionFacade.hasConfirmedParentLink(parentUserId, studentId)).thenReturn(true);
    when(connectionFacade.findClassesByStudent(studentId)).thenReturn(List.of());

    ParentDashboardView response = dashboardService.getParentDashboard(actor);

    assertThat(response).isNotNull();
    assertThat(response.students()).hasSize(1);
    assertThat(response.students().getFirst().studentId()).isEqualTo(studentId);
  }
}

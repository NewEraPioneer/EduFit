package vn.edufit.progress.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.progress.application.dto.TutorTodoView;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.progress.domain.repository.SessionFeedbackDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionSummaryView;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;

class TutorTodoServiceTest {

  private final Instant now = Instant.parse("2026-11-02T10:00:00Z");
  private final LocalDate today = LocalDate.parse("2026-11-02");
  private final UUID tutorId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID planned = UUID.randomUUID();
  private final UUID unplanned = UUID.randomUUID();

  private SessionFacade sessionFacade;
  private SessionFeedbackDomainRepository feedbackRepository;
  private TutorTodoService service;
  private CurrentUser tutor;

  @BeforeEach
  void setUp() {
    ConnectionFacade connectionFacade = mock(ConnectionFacade.class);
    ProfileFacade profileFacade = mock(ProfileFacade.class);
    sessionFacade = mock(SessionFacade.class);
    LearningPlanDomainRepository planRepository = mock(LearningPlanDomainRepository.class);
    feedbackRepository = mock(SessionFeedbackDomainRepository.class);
    service = new TutorTodoService(connectionFacade, profileFacade, sessionFacade, planRepository, feedbackRepository,
        new ProgressDisplayNames(profileFacade, mock(IamFacade.class)), Clock.fixed(now, ZoneId.of("UTC")));

    tutor = mock(CurrentUser.class);
    when(tutor.hasRole("TUTOR")).thenReturn(true);
    when(tutor.getUserId()).thenReturn(tutorUserId);
    when(profileFacade.getTutorIdByUserId(tutorUserId)).thenReturn(tutorId);
    when(profileFacade.findLearningNeeds(any())).thenReturn(Map.of());
    when(connectionFacade.findClassesByTutor(tutorId)).thenReturn(List.of(
        cls(planned, "ACTIVE"), cls(unplanned, "ACTIVE"), cls(UUID.randomUUID(), "ENDED")));

    Milestone overdue = milestone("Hệ phương trình", today.minusDays(5), MilestoneStatus.IN_PROGRESS, 0);
    Milestone doneLate = milestone("Phương trình bậc nhất", today.minusDays(20), MilestoneStatus.ACHIEVED, 1);
    Milestone finalWithoutDate = milestone("Ôn tổng hợp", null, MilestoneStatus.NOT_STARTED, 2);
    when(planRepository.findByClassIdIn(any())).thenReturn(List.of(
        new LearningPlan(UUID.randomUUID(), planned, null, 0, now, now, List.of(overdue, doneLate, finalWithoutDate))));
  }

  private ConnectionFacade.ClassDetailView cls(UUID classId, String status) {
    return new ConnectionFacade.ClassDetailView(classId, UUID.randomUUID(), tutorId, null, status, now, null, 200000L);
  }

  private Milestone milestone(String title, LocalDate target, MilestoneStatus status, int order) {
    return new Milestone(UUID.randomUUID(), null, title, null, target, 1, status,
        status == MilestoneStatus.ACHIEVED ? target : null, false, order, now, List.of());
  }

  private SessionSummaryView completed(Instant endAt) {
    return new SessionSummaryView(UUID.randomUUID(), planned, tutorId, UUID.randomUUID(), endAt.minusSeconds(5400), endAt,
        "ONLINE", "COMPLETED", false, endAt.minusSeconds(86400));
  }

  @Test
  @DisplayName("Gom đủ 4 nhóm việc: buổi chưa phản hồi, lớp chưa có lộ trình, cột mốc quá hạn, thiếu ngày cuối")
  void getTodos_collectsAllGroups() {
    SessionSummaryView lateOne = completed(now.minusSeconds(3 * 86400));
    SessionSummaryView fresh = completed(now.minusSeconds(3600));
    SessionSummaryView answered = completed(now.minusSeconds(7200));
    when(sessionFacade.findSessionSummariesByClassIds(eq(List.of(planned)), any(), eq(now)))
        .thenReturn(List.of(fresh, answered, lateOne));
    when(feedbackRepository.findSessionIdsWithFeedback(anyCollection())).thenReturn(Set.of(answered.sessionId()));

    TutorTodoView todos = service.getTodos(tutor);

    assertThat(todos.sessionsMissingFeedback()).extracting(TutorTodoView.MissingFeedbackItem::sessionId)
        .containsExactly(lateOne.sessionId(), fresh.sessionId());
    assertThat(todos.sessionsMissingFeedback()).extracting(TutorTodoView.MissingFeedbackItem::late)
        .containsExactly(true, false);
    assertThat(todos.classesWithoutPlan()).extracting(TutorTodoView.ClassItem::classId).containsExactly(unplanned);
    assertThat(todos.overdueMilestones()).singleElement().satisfies(m -> {
      assertThat(m.milestoneTitle()).isEqualTo("Hệ phương trình");
      assertThat(m.daysLate()).isEqualTo(5);
    });
    assertThat(todos.plansMissingFinalDate()).extracting(TutorTodoView.ClassItem::classId).containsExactly(planned);
    assertThat(todos.totalCount()).isEqualTo(5);
  }

  @Test
  @DisplayName("Chỉ tài khoản gia sư được xem ngăn Việc cần làm")
  void getTodos_requiresTutor() {
    CurrentUser parent = mock(CurrentUser.class);
    when(parent.hasRole("PARENT")).thenReturn(true);
    assertThatThrownBy(() -> service.getTodos(parent)).isInstanceOf(ForbiddenOperationException.class);
  }

  @Test
  @DisplayName("Gia sư chưa có lớp đang hoạt động: ngăn trống")
  void getTodos_emptyWithoutActiveClasses() {
    when(tutor.getUserId()).thenReturn(UUID.randomUUID());
    assertThat(service.getTodos(tutor).totalCount()).isZero();
  }
}

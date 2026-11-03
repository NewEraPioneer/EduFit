package vn.edufit.progress.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.progress.application.dto.SessionFeedbackView;
import vn.edufit.progress.application.dto.SubmitSessionFeedbackCommand;
import vn.edufit.progress.application.dto.SubmitSessionFeedbackCommand.TaughtMilestone;
import vn.edufit.progress.domain.model.AttitudeChip;
import vn.edufit.progress.domain.model.HomeworkStatus;
import vn.edufit.progress.domain.model.IndependenceLevel;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneAssessment;
import vn.edufit.progress.domain.model.MilestoneCriterion;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.model.SessionFeedback;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.progress.domain.repository.PlanHistoryDomainRepository;
import vn.edufit.progress.domain.repository.SessionFeedbackDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionSummaryView;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;
import vn.edufit.shared.notification.NotificationSink;

class SessionFeedbackServiceTest {

  private final Instant now = Instant.parse("2026-11-02T10:00:00Z");
  private final UUID classId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();

  private SessionFeedbackDomainRepository feedbackRepository;
  private LearningPlanDomainRepository planRepository;
  private SessionFacade sessionFacade;
  private ProfileFacade profileFacade;
  private NotificationSink notifications;
  private SessionFeedbackService service;
  private CurrentUser tutor;
  private Milestone milestone;

  @BeforeEach
  void setUp() {
    feedbackRepository = mock(SessionFeedbackDomainRepository.class);
    planRepository = mock(LearningPlanDomainRepository.class);
    sessionFacade = mock(SessionFacade.class);
    ConnectionFacade connectionFacade = mock(ConnectionFacade.class);
    profileFacade = mock(ProfileFacade.class);
    notifications = mock(NotificationSink.class);
    service = new SessionFeedbackService(
        feedbackRepository,
        planRepository,
        mock(PlanHistoryDomainRepository.class),
        sessionFacade,
        new ProgressAccessGuard(connectionFacade, profileFacade),
        new MilestoneAchievementNotifier(connectionFacade, profileFacade, notifications),
        Clock.fixed(now, ZoneId.of("UTC"))
    );

    tutor = mock(CurrentUser.class);
    when(tutor.hasRole("TUTOR")).thenReturn(true);
    when(tutor.getUserId()).thenReturn(tutorUserId);
    when(profileFacade.getTutorIdByUserId(tutorUserId)).thenReturn(tutorId);
    when(connectionFacade.findClassById(classId)).thenReturn(Optional.of(new ConnectionFacade.ClassDetailView(
        classId, studentId, tutorId, UUID.randomUUID(), "ACTIVE", now, null, 200000L)));
    givenSessionStatus("COMPLETED");

    milestone = new Milestone(UUID.randomUUID(), null, "Hệ phương trình", null, LocalDate.parse("2026-11-20"), 2,
        MilestoneStatus.NOT_STARTED, null, false, 0, now,
        List.of(new MilestoneCriterion(UUID.randomUUID(), "Giải bằng phương pháp thế", 0, false, null, now),
            new MilestoneCriterion(UUID.randomUUID(), "Giải bài toán lời văn", 1, false, null, now)));
    LearningPlan plan = new LearningPlan(UUID.randomUUID(), classId, null, 0, now, now, List.of(milestone));
    when(planRepository.findByClassId(classId)).thenReturn(Optional.of(plan));
    when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(feedbackRepository.findAssessmentsByMilestoneIdIn(any())).thenReturn(List.of());
  }

  private void givenPreviousLevel(int level) {
    when(feedbackRepository.findAssessmentsByMilestoneIdIn(any())).thenReturn(List.of(
        new MilestoneAssessment(UUID.randomUUID(), milestone.getMilestoneId(), UUID.randomUUID(),
            IndependenceLevel.fromValue(1), null, null, null, tutorUserId, now.minusSeconds(864000)),
        new MilestoneAssessment(UUID.randomUUID(), milestone.getMilestoneId(), UUID.randomUUID(),
            IndependenceLevel.fromValue(level), null, null, null, tutorUserId, now.minusSeconds(86400))));
  }

  private void givenSessionStatus(String status) {
    when(sessionFacade.findSessionById(sessionId)).thenReturn(Optional.of(new SessionSummaryView(
        sessionId, classId, tutorId, studentId, now.minusSeconds(7200), now.minusSeconds(3600),
        "ONLINE", status, false, now.minusSeconds(86400))));
  }

  private SubmitSessionFeedbackCommand command(TaughtMilestone... items) {
    return new SubmitSessionFeedbackCommand(HomeworkStatus.ASSIGNED, List.of(AttitudeChip.FOCUSED), null, List.of(items));
  }

  private UUID criterion(int i) {
    return milestone.getCriteria().get(i).getCriterionId();
  }

  @Test
  @DisplayName("Ghi phản hồi: lưu điểm chấm, tick tiêu chí và tự chuyển cột mốc sang IN_PROGRESS")
  void submit_recordsAssessmentAndStartsMilestone() {
    SessionFeedbackView view = service.submit(tutor, sessionId,
        command(new TaughtMilestone(milestone.getMilestoneId(), 2, 7, 10, null, List.of(criterion(0)), false)));

    ArgumentCaptor<SessionFeedback> captor = ArgumentCaptor.forClass(SessionFeedback.class);
    verify(feedbackRepository).save(captor.capture());
    SessionFeedback saved = captor.getValue();
    assertThat(saved.getHomeworkStatus()).isEqualTo(HomeworkStatus.ASSIGNED);
    assertThat(saved.getAssessments()).singleElement()
        .satisfies(a -> {
          assertThat(a.getLevel().value()).isEqualTo(2);
          assertThat(a.getCorrectCount()).isEqualTo(7);
          assertThat(a.getAssessedBy()).isEqualTo(tutorUserId);
        });
    assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.IN_PROGRESS);
    assertThat(milestone.completionShare()).isEqualTo(new Milestone.CompletionShare(1, 2));
    assertThat(view.assessments().getFirst().milestoneTitle()).isEqualTo("Hệ phương trình");
    assertThat(view.attitudeChips()).containsExactly("FOCUSED");
  }

  @Test
  @DisplayName("Chốt đạt trong phản hồi chỉ khi tick đủ tiêu chí, kèm thông báo")
  void submit_markAchievedRequiresAllCriteria() {
    assertThatThrownBy(() -> service.submit(tutor, sessionId,
        command(new TaughtMilestone(milestone.getMilestoneId(), 3, null, null, null, List.of(criterion(0)), true))))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("tick đủ tiêu chí");
    verify(feedbackRepository, never()).save(any());
  }

  @Test
  @DisplayName("Chốt đạt khi tick đủ tiêu chí")
  void submit_markAchieved() {
    service.submit(tutor, sessionId, command(new TaughtMilestone(
        milestone.getMilestoneId(), 4, null, null, null, List.of(criterion(0), criterion(1)), true)));

    assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.ACHIEVED);
    assertThat(milestone.getAchievedDate()).isEqualTo(LocalDate.parse("2026-11-02"));
  }

  @Test
  @DisplayName("Từ chối: buổi chưa hoàn thành, đã có phản hồi, thiếu cột mốc, cột mốc lạ, không phải gia sư lớp")
  void submit_rejectsInvalidRequests() {
    TaughtMilestone ok = new TaughtMilestone(milestone.getMilestoneId(), 2, null, null, null, null, false);

    when(feedbackRepository.existsBySessionId(sessionId)).thenReturn(true);
    assertThatThrownBy(() -> service.submit(tutor, sessionId, command(ok))).isInstanceOf(ResourceConflictException.class);
    when(feedbackRepository.existsBySessionId(sessionId)).thenReturn(false);

    assertThatThrownBy(() -> service.submit(tutor, sessionId, command()))
        .isInstanceOf(InvalidOperationException.class).hasMessageContaining("ít nhất một cột mốc");

    assertThatThrownBy(() -> service.submit(tutor, sessionId,
        command(new TaughtMilestone(UUID.randomUUID(), 2, null, null, null, null, false))))
        .isInstanceOf(InvalidOperationException.class).hasMessageContaining("không thuộc lộ trình");

    assertThatThrownBy(() -> service.submit(tutor, sessionId,
        command(new TaughtMilestone(milestone.getMilestoneId(), 2, 11, 10, null, null, false))))
        .isInstanceOf(InvalidOperationException.class);

    givenSessionStatus("SCHEDULED");
    assertThatThrownBy(() -> service.submit(tutor, sessionId, command(ok)))
        .isInstanceOf(InvalidOperationException.class).hasMessageContaining("hoàn thành");

    givenSessionStatus("COMPLETED");
    when(profileFacade.getTutorIdByUserId(tutorUserId)).thenReturn(UUID.randomUUID());
    assertThatThrownBy(() -> service.submit(tutor, sessionId, command(ok))).isInstanceOf(ForbiddenOperationException.class);
  }

  @Test
  @DisplayName("Chống chấm nhảy mức: tăng từ 2 mức trở lên cần đúng x/y hoặc câu bằng chứng; tăng 1 mức thì không")
  void submit_jumpOfTwoLevelsRequiresEvidence() {
    givenPreviousLevel(1);
    UUID id = milestone.getMilestoneId();

    assertThatThrownBy(() -> service.submit(tutor, sessionId, command(new TaughtMilestone(id, 3, null, null, null, null, false))))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("từ mức 1 lên mức 3");
    assertThatThrownBy(() -> service.submit(tutor, sessionId, command(new TaughtMilestone(id, 3, null, null, "   ", null, false))))
        .isInstanceOf(InvalidOperationException.class);
    verify(feedbackRepository, never()).save(any());

    SessionFeedbackView view = service.submit(tutor, sessionId,
        command(new TaughtMilestone(id, 3, null, null, "Tự giải đúng 3 bài hệ đối xứng", null, false)));
    assertThat(view.assessments().getFirst().evidenceNote()).isEqualTo("Tự giải đúng 3 bài hệ đối xứng");
  }

  @Test
  @DisplayName("Chống chấm nhảy mức: đúng x/y bài thay cho câu bằng chứng; điểm xuất phát không bị kiểm tra")
  void submit_scoreCountsAsEvidenceAndBaselineIsFree() {
    UUID id = milestone.getMilestoneId();
    service.submit(tutor, sessionId, command(new TaughtMilestone(id, 3, null, null, null, null, false)));

    givenPreviousLevel(1);
    when(feedbackRepository.existsBySessionId(sessionId)).thenReturn(false);
    SessionFeedbackView view = service.submit(tutor, sessionId,
        command(new TaughtMilestone(id, 3, 8, 10, null, null, false)));
    assertThat(view.assessments().getFirst().correctCount()).isEqualTo(8);
  }

  @Test
  @DisplayName("Không được chấm mức 4 khi chưa tick đủ tiêu chí (tính cả tick trong chính buổi này)")
  void submit_levelFourRequiresAllCriteriaChecked() {
    UUID id = milestone.getMilestoneId();
    assertThatThrownBy(() -> service.submit(tutor, sessionId,
        command(new TaughtMilestone(id, 4, null, null, null, List.of(criterion(0)), false))))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("chưa thể chấm mức 4");
    verify(feedbackRepository, never()).save(any());
  }

  @Test
  @DisplayName("Lời nhắn riêng cho phụ huynh được lưu; ghi sau 48 giờ kể từ khi buổi kết thúc thì bổ sung muộn")
  void submit_storesParentMessageAndLateFlag() {
    TaughtMilestone ok = new TaughtMilestone(milestone.getMilestoneId(), 2, null, null, null, null, false);
    SessionFeedbackView onTime = service.submit(tutor, sessionId, new SubmitSessionFeedbackCommand(
        HomeworkStatus.NONE, List.of(), "  Con cần ngủ sớm hơn trước buổi học  ", List.of(ok)));
    assertThat(onTime.parentMessage()).isEqualTo("Con cần ngủ sớm hơn trước buổi học");
    assertThat(onTime.late()).isFalse();

    when(sessionFacade.findSessionById(sessionId)).thenReturn(Optional.of(new SessionSummaryView(
        sessionId, classId, tutorId, studentId, now.minusSeconds(49 * 3600 + 5400), now.minusSeconds(49 * 3600),
        "ONLINE", "COMPLETED", false, now.minusSeconds(86400 * 3))));
    SessionFeedbackView late = service.submit(tutor, sessionId, command(ok));
    assertThat(late.late()).isTrue();
    assertThat(late.parentMessage()).isNull();
  }
}

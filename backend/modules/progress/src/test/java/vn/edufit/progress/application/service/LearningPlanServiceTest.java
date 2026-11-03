package vn.edufit.progress.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.progress.application.dto.CreatePlanCommand;
import vn.edufit.progress.application.dto.UpdateMilestoneStatusCommand;
import vn.edufit.progress.application.dto.UpdatePlanCommand;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneCriterion;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.model.PlanHistory;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.progress.domain.repository.PlanHistoryDomainRepository;
import vn.edufit.progress.domain.repository.TopicDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;
import vn.edufit.shared.notification.NotificationSink;

class LearningPlanServiceTest {

  private static final List<String> CRITERIA = List.of("Giải đúng dạng cơ bản", "Tự trình bày lời giải");

  private LearningPlanDomainRepository planRepository;
  private PlanHistoryDomainRepository historyRepository;
  private ConnectionFacade connectionFacade;
  private ProfileFacade profileFacade;
  private NotificationSink notifications;
  private TopicDomainRepository topicRepository;
  private LearningPlanService planService;

  private final Instant fixedInstant = Instant.parse("2026-10-20T10:00:00Z");
  private final LocalDate today = LocalDate.parse("2026-10-20");

  private final UUID classId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private CurrentUser tutor;

  @BeforeEach
  void setUp() {
    planRepository = mock(LearningPlanDomainRepository.class);
    historyRepository = mock(PlanHistoryDomainRepository.class);
    connectionFacade = mock(ConnectionFacade.class);
    profileFacade = mock(ProfileFacade.class);
    notifications = mock(NotificationSink.class);
    topicRepository = mock(TopicDomainRepository.class);
    Clock fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

    planService = new LearningPlanService(
        planRepository,
        historyRepository,
        new ProgressAccessGuard(connectionFacade, profileFacade),
        new MilestoneAchievementNotifier(connectionFacade, profileFacade, notifications),
        new TopicCatalogService(topicRepository, new ProgressAccessGuard(connectionFacade, profileFacade),
            new ClassNeedResolver(profileFacade), fixedClock),
        fixedClock
    );

    tutor = mock(CurrentUser.class);
    when(tutor.hasRole("TUTOR")).thenReturn(true);
    when(tutor.getUserId()).thenReturn(tutorUserId);
    when(profileFacade.getTutorIdByUserId(tutorUserId)).thenReturn(tutorId);
    givenClass("ACTIVE");
    when(planRepository.save(any(LearningPlan.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private void givenClass(String status) {
    when(connectionFacade.findClassById(classId)).thenReturn(Optional.of(new ConnectionFacade.ClassDetailView(
        classId, studentId, tutorId, UUID.randomUUID(), status, fixedInstant, null, 200000L)));
  }

  private Milestone milestone(UUID id, MilestoneStatus status, LocalDate achievedDate, boolean... checks) {
    List<MilestoneCriterion> criteria = new java.util.ArrayList<>();
    for (int i = 0; i < checks.length; i++) {
      criteria.add(new MilestoneCriterion(UUID.randomUUID(), "Tiêu chí " + i, i, checks[i],
          checks[i] ? fixedInstant : null, fixedInstant));
    }
    return new Milestone(id, null, "Cot moc 1", "Desc", LocalDate.parse("2026-10-25"),
        3, status, achievedDate, false, 0, fixedInstant, criteria);
  }

  private LearningPlan planWith(Milestone m) {
    UUID planId = UUID.randomUUID();
    LearningPlan plan = new LearningPlan(planId, classId, "Summary", 0, fixedInstant, fixedInstant, List.of(m));
    when(planRepository.findByMilestoneId(m.getMilestoneId())).thenReturn(Optional.of(plan));
    return plan;
  }

  @Test
  @DisplayName("createPlan: Tạo kế hoạch kèm tiêu chí đạt và lưu audit trail")
  void createPlan_Success() {
    when(planRepository.existsByClassId(classId)).thenReturn(false);
    CreatePlanCommand cmd = new CreatePlanCommand(classId, "Lo trinh on thi THPT", List.of(
        new CreatePlanCommand.CreateMilestoneItem("On tap Ham so", "Chuong 1", LocalDate.parse("2026-11-01"), 3, CRITERIA, null, null),
        new CreatePlanCommand.CreateMilestoneItem("On tap Hinh hoc", "Chuong 2", null, 2, CRITERIA, null, null),
        new CreatePlanCommand.CreateMilestoneItem("De thi thu", "Chuong 3", LocalDate.parse("2026-11-29"), 1, CRITERIA, null, null)
    ));

    LearningPlan result = planService.createPlan(tutor, cmd);

    assertThat(result.getClassId()).isEqualTo(classId);
    assertThat(result.getMilestones()).hasSize(3);
    assertThat(result.getMilestones().getFirst().getCriteria())
        .extracting(MilestoneCriterion::getContent).containsExactlyElementsOf(CRITERIA);
    assertThat(result.calculateProgressPercentage()).isZero();

    ArgumentCaptor<PlanHistory> historyCaptor = ArgumentCaptor.forClass(PlanHistory.class);
    verify(historyRepository).save(historyCaptor.capture());
    assertThat(historyCaptor.getValue().getAction()).isEqualTo("PLAN_CREATED");
  }

  @Test
  @DisplayName("createPlan: lộ trình phải có 3–8 cột mốc và cột mốc cuối bắt buộc có hạn")
  void createPlan_RequiresRoadmapShape() {
    when(planRepository.existsByClassId(classId)).thenReturn(false);
    CreatePlanCommand tooShort = new CreatePlanCommand(classId, null, List.of(
        new CreatePlanCommand.CreateMilestoneItem("M1", null, null, 1, CRITERIA, null, null),
        new CreatePlanCommand.CreateMilestoneItem("M2", null, LocalDate.parse("2026-11-29"), 1, CRITERIA, null, null)));
    assertThatThrownBy(() -> planService.createPlan(tutor, tooShort))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("từ 3 đến 8 cột mốc");

    CreatePlanCommand noFinalDate = new CreatePlanCommand(classId, null, List.of(
        new CreatePlanCommand.CreateMilestoneItem("M1", null, LocalDate.parse("2026-11-10"), 1, CRITERIA, null, null),
        new CreatePlanCommand.CreateMilestoneItem("M2", null, null, 1, CRITERIA, null, null),
        new CreatePlanCommand.CreateMilestoneItem("M3", null, null, 1, CRITERIA, null, null)));
    assertThatThrownBy(() -> planService.createPlan(tutor, noFinalDate))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("Cột mốc cuối");
  }

  @Test
  @DisplayName("createPlan: Cột mốc mới thiếu tiêu chí đạt bị từ chối")
  void createPlan_RejectsMilestoneWithoutCriteria() {
    when(planRepository.existsByClassId(classId)).thenReturn(false);
    CreatePlanCommand cmd = new CreatePlanCommand(classId, null, List.of(
        new CreatePlanCommand.CreateMilestoneItem("M1", null, null, 1, List.of(), null, null)));

    assertThatThrownBy(() -> planService.createPlan(tutor, cmd))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("tiêu chí đạt");
  }

  @Test
  @DisplayName("createPlan: Từ chối nếu người gọi không phải gia sư của lớp")
  void createPlan_ForbiddenWhenNotClassTutor() {
    when(profileFacade.getTutorIdByUserId(tutorUserId)).thenReturn(UUID.randomUUID());
    CreatePlanCommand cmd = new CreatePlanCommand(classId, "Summary", List.of(
        new CreatePlanCommand.CreateMilestoneItem("M1", null, null, 1, CRITERIA, null, null)));

    assertThatThrownBy(() -> planService.createPlan(tutor, cmd)).isInstanceOf(ForbiddenOperationException.class);
  }

  @Test
  @DisplayName("createPlan: Từ chối nếu lớp không ở trạng thái ACTIVE")
  void createPlan_InvalidWhenNotActive() {
    givenClass("COMPLETED");
    CreatePlanCommand cmd = new CreatePlanCommand(classId, "Summary", List.of(
        new CreatePlanCommand.CreateMilestoneItem("M1", null, null, 1, CRITERIA, null, null)));

    assertThatThrownBy(() -> planService.createPlan(tutor, cmd))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("đang hoạt động");
  }

  @Test
  @DisplayName("updatePlan: Xung đột phiên bản (Optimistic Locking) ném PLAN_VERSION_CONFLICT")
  void updatePlan_VersionConflict() {
    UUID planId = UUID.randomUUID();
    LearningPlan plan = new LearningPlan(planId, classId, "Old Summary", 2, fixedInstant, fixedInstant, List.of());
    when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

    UpdatePlanCommand cmd = new UpdatePlanCommand(1, "New Summary", List.of(
        new UpdatePlanCommand.UpdateMilestoneItem(null, "New M", null, null, 1, List.of(), null, null)));

    assertThatThrownBy(() -> planService.updatePlan(tutor, planId, cmd))
        .isInstanceOf(ResourceConflictException.class)
        .satisfies(ex -> assertThat(((ResourceConflictException) ex).getErrorCode()).isEqualTo(ErrorCode.PLAN_VERSION_CONFLICT));
  }

  @Test
  @DisplayName("updatePlan: Cột mốc cũ chưa có tiêu chí được giữ nguyên; khi sửa thì bắt buộc có tiêu chí")
  void updatePlan_LegacyMilestoneNeedsCriteriaOnlyWhenEdited() {
    UUID planId = UUID.randomUUID();
    Milestone legacy = new Milestone(UUID.randomUUID(), planId, "Legacy", null, null, 2,
        MilestoneStatus.NOT_STARTED, null, false, 0, fixedInstant, List.of());
    LearningPlan plan = new LearningPlan(planId, classId, null, 0, fixedInstant, fixedInstant, List.of(legacy));
    when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
    List<UpdatePlanCommand.CriterionItem> criteria = List.of(new UpdatePlanCommand.CriterionItem(null, "Tieu chi"));
    UpdatePlanCommand.UpdateMilestoneItem added = new UpdatePlanCommand.UpdateMilestoneItem(
        null, "Moi", null, null, 1, criteria, null, null);
    UpdatePlanCommand.UpdateMilestoneItem finalOne = new UpdatePlanCommand.UpdateMilestoneItem(
        null, "Cuoi", null, LocalDate.parse("2026-12-20"), 1, criteria, null, null);

    UpdatePlanCommand unchanged = new UpdatePlanCommand(0, null, List.of(
        new UpdatePlanCommand.UpdateMilestoneItem(legacy.getMilestoneId(), "Legacy", null, null, 2, List.of(), null, null),
        added, finalOne));
    assertThat(planService.updatePlan(tutor, planId, unchanged).getMilestones()).hasSize(3);

    UpdatePlanCommand renamed = new UpdatePlanCommand(0, null, List.of(
        new UpdatePlanCommand.UpdateMilestoneItem(legacy.getMilestoneId(), "Legacy v2", null, null, 2, List.of(), null, null),
        added, finalOne));
    assertThatThrownBy(() -> planService.updatePlan(tutor, planId, renamed))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("tiêu chí đạt");
  }

  @Test
  @DisplayName("updateMilestoneStatus: Chốt ACHIEVED khi đã tick đủ tiêu chí và gửi thông báo")
  void updateMilestoneStatus_AchievedNotification() {
    UUID milestoneId = UUID.randomUUID();
    UUID studentUserId = UUID.randomUUID();
    UUID parentUserId = UUID.randomUUID();
    planWith(milestone(milestoneId, MilestoneStatus.IN_PROGRESS, null, true, true));

    StudentSummaryDto studentDto = mock(StudentSummaryDto.class);
    when(studentDto.userId()).thenReturn(studentUserId);
    when(profileFacade.findStudentById(studentId)).thenReturn(Optional.of(studentDto));
    when(connectionFacade.findConfirmedParentUserIds(studentId)).thenReturn(List.of(parentUserId));

    LearningPlan updated = planService.updateMilestoneStatus(tutor, milestoneId,
        new UpdateMilestoneStatusCommand(0, MilestoneStatus.ACHIEVED, today, null));

    Milestone m = updated.getMilestones().getFirst();
    assertThat(m.getStatus()).isEqualTo(MilestoneStatus.ACHIEVED);
    assertThat(m.getAchievedDate()).isEqualTo(today);
    verify(notifications).notify(
        eq(studentUserId), eq(studentId), eq("MILESTONE_ACHIEVED"), any(), any(), eq("MILESTONE"), eq(milestoneId));
    verify(notifications).notify(
        eq(parentUserId), eq(studentId), eq("MILESTONE_ACHIEVED"), any(), any(), eq("MILESTONE"), eq(milestoneId));
  }

  @Test
  @DisplayName("updateMilestoneStatus: Không chốt ACHIEVED khi chưa tick đủ tiêu chí")
  void updateMilestoneStatus_RejectsAchievedWithUncheckedCriteria() {
    UUID milestoneId = UUID.randomUUID();
    planWith(milestone(milestoneId, MilestoneStatus.IN_PROGRESS, null, true, false));

    assertThatThrownBy(() -> planService.updateMilestoneStatus(tutor, milestoneId,
        new UpdateMilestoneStatusCommand(0, MilestoneStatus.ACHIEVED, today, null)))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("tick đủ tiêu chí");
    verify(planRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateMilestoneStatus: Mở lại ACHIEVED → IN_PROGRESS bắt buộc lý do, lưu ngày đạt cũ vào lịch sử")
  void updateMilestoneStatus_ReopenRequiresReasonAndKeepsHistory() {
    UUID milestoneId = UUID.randomUUID();
    LocalDate achievedOn = LocalDate.parse("2026-10-10");
    planWith(milestone(milestoneId, MilestoneStatus.ACHIEVED, achievedOn, true));

    assertThatThrownBy(() -> planService.updateMilestoneStatus(tutor, milestoneId,
        new UpdateMilestoneStatusCommand(0, MilestoneStatus.IN_PROGRESS, null, "  ")))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("lý do");

    LearningPlan updated = planService.updateMilestoneStatus(tutor, milestoneId,
        new UpdateMilestoneStatusCommand(0, MilestoneStatus.IN_PROGRESS, null, "Con chưa vững dạng khó"));

    Milestone m = updated.getMilestones().getFirst();
    assertThat(m.getStatus()).isEqualTo(MilestoneStatus.IN_PROGRESS);
    assertThat(m.getAchievedDate()).isNull();
    ArgumentCaptor<PlanHistory> captor = ArgumentCaptor.forClass(PlanHistory.class);
    verify(historyRepository).save(captor.capture());
    assertThat(captor.getValue().getAction()).isEqualTo(LearningPlanService.ACTION_REOPENED);
    assertThat(captor.getValue().getOldValue()).contains(achievedOn.toString());
    assertThat(captor.getValue().getNewValue()).contains("Con chưa vững dạng khó");
  }

  @Test
  @DisplayName("updateMilestoneStatus: Không cho đặt IN_PROGRESS thủ công (chỉ tự động khi gắn buổi học)")
  void updateMilestoneStatus_RejectsManualInProgress() {
    UUID milestoneId = UUID.randomUUID();
    planWith(milestone(milestoneId, MilestoneStatus.NOT_STARTED, null, false));

    assertThatThrownBy(() -> planService.updateMilestoneStatus(tutor, milestoneId,
        new UpdateMilestoneStatusCommand(0, MilestoneStatus.IN_PROGRESS, null, null)))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("tự động");
  }
}

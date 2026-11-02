package vn.edufit.progress.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.model.PlanHistory;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.progress.domain.repository.PlanHistoryDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;
import vn.edufit.shared.notification.NotificationSink;

class LearningPlanServiceTest {

  private LearningPlanDomainRepository planRepository;
  private PlanHistoryDomainRepository historyRepository;
  private ConnectionFacade connectionFacade;
  private ProfileFacade profileFacade;
  private NotificationSink notifications;
  private Clock fixedClock;
  private LearningPlanService planService;

  private final Instant fixedInstant = Instant.parse("2026-10-20T10:00:00Z");

  @BeforeEach
  void setUp() {
    planRepository = mock(LearningPlanDomainRepository.class);
    historyRepository = mock(PlanHistoryDomainRepository.class);
    connectionFacade = mock(ConnectionFacade.class);
    profileFacade = mock(ProfileFacade.class);
    notifications = mock(NotificationSink.class);
    fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

    planService = new LearningPlanService(
        planRepository, historyRepository, connectionFacade, profileFacade, notifications, fixedClock
    );
  }

  @Test
  @DisplayName("createPlan: Tao ke hoach hoc tap thanh cong va luu audit trail")
  void createPlan_Success() {
    UUID classId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID tutorUserId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();

    CurrentUser actor = mock(CurrentUser.class);
    when(actor.hasRole("TUTOR")).thenReturn(true);
    when(actor.getUserId()).thenReturn(tutorUserId);

    ConnectionFacade.ClassDetailView cls = new ConnectionFacade.ClassDetailView(classId, studentId, tutorId, UUID.randomUUID(), "ACTIVE", fixedInstant, null, 200000L);
    when(connectionFacade.findClassById(classId)).thenReturn(Optional.of(cls));
    when(profileFacade.getTutorIdByUserId(tutorUserId)).thenReturn(tutorId);
    when(planRepository.existsByClassId(classId)).thenReturn(false);
    when(planRepository.save(any(LearningPlan.class))).thenAnswer(inv -> inv.getArgument(0));

    CreatePlanCommand cmd = new CreatePlanCommand(
        classId,
        "Lo trinh on thi THPT",
        List.of(
            new CreatePlanCommand.CreateMilestoneItem("On tap Ham so", "Chuong 1", LocalDate.parse("2026-11-01"), 3),
            new CreatePlanCommand.CreateMilestoneItem("On tap Hinh hoc", "Chuong 2", LocalDate.parse("2026-11-15"), 2)
        )
    );

    LearningPlan result = planService.createPlan(actor, cmd);

    assertThat(result).isNotNull();
    assertThat(result.getClassId()).isEqualTo(classId);
    assertThat(result.getSummary()).isEqualTo("Lo trinh on thi THPT");
    assertThat(result.getMilestones()).hasSize(2);

    ArgumentCaptor<PlanHistory> historyCaptor = ArgumentCaptor.forClass(PlanHistory.class);
    verify(historyRepository).save(historyCaptor.capture());
    assertThat(historyCaptor.getValue().getAction()).isEqualTo("PLAN_CREATED");
    assertThat(historyCaptor.getValue().getActorUserId()).isEqualTo(tutorUserId);
  }

  @Test
  @DisplayName("createPlan: Tu choi neu nguoi goi khong phai gia su cua lop")
  void createPlan_ForbiddenWhenNotClassTutor() {
    UUID classId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID anotherTutorId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    CurrentUser actor = mock(CurrentUser.class);
    when(actor.hasRole("TUTOR")).thenReturn(true);
    when(actor.getUserId()).thenReturn(userId);

    ConnectionFacade.ClassDetailView cls = new ConnectionFacade.ClassDetailView(classId, UUID.randomUUID(), tutorId, UUID.randomUUID(), "ACTIVE", fixedInstant, null, 200000L);
    when(connectionFacade.findClassById(classId)).thenReturn(Optional.of(cls));
    when(profileFacade.getTutorIdByUserId(userId)).thenReturn(anotherTutorId);

    CreatePlanCommand cmd = new CreatePlanCommand(classId, "Summary", List.of(
        new CreatePlanCommand.CreateMilestoneItem("M1", null, null, 1)
    ));

    assertThatThrownBy(() -> planService.createPlan(actor, cmd))
        .isInstanceOf(ForbiddenOperationException.class);
  }

  @Test
  @DisplayName("createPlan: Tu choi neu lop khong o trang thai ACTIVE")
  void createPlan_InvalidWhenNotActive() {
    UUID classId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    CurrentUser actor = mock(CurrentUser.class);
    when(actor.hasRole("TUTOR")).thenReturn(true);
    when(actor.getUserId()).thenReturn(userId);

    ConnectionFacade.ClassDetailView cls = new ConnectionFacade.ClassDetailView(classId, UUID.randomUUID(), tutorId, UUID.randomUUID(), "COMPLETED", fixedInstant, null, 200000L);
    when(connectionFacade.findClassById(classId)).thenReturn(Optional.of(cls));
    when(profileFacade.getTutorIdByUserId(userId)).thenReturn(tutorId);

    CreatePlanCommand cmd = new CreatePlanCommand(classId, "Summary", List.of(
        new CreatePlanCommand.CreateMilestoneItem("M1", null, null, 1)
    ));

    assertThatThrownBy(() -> planService.createPlan(actor, cmd))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("đang hoạt động");
  }

  @Test
  @DisplayName("updatePlan: Xung dot phien ban (Optimistic Locking) nem PLAN_VERSION_CONFLICT")
  void updatePlan_VersionConflict() {
    UUID planId = UUID.randomUUID();
    UUID classId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    CurrentUser actor = mock(CurrentUser.class);
    when(actor.hasRole("TUTOR")).thenReturn(true);
    when(actor.getUserId()).thenReturn(userId);

    LearningPlan plan = new LearningPlan(
        planId, classId, "Old Summary", 2, fixedInstant, fixedInstant, List.of()
    );
    when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

    ConnectionFacade.ClassDetailView cls = new ConnectionFacade.ClassDetailView(classId, UUID.randomUUID(), tutorId, UUID.randomUUID(), "ACTIVE", fixedInstant, null, 200000L);
    when(connectionFacade.findClassById(classId)).thenReturn(Optional.of(cls));
    when(profileFacade.getTutorIdByUserId(userId)).thenReturn(tutorId);

    UpdatePlanCommand cmd = new UpdatePlanCommand(
        1, // Client gửi version 1 trong khi máy chủ đã là version 2
        "New Summary",
        List.of(new UpdatePlanCommand.UpdateMilestoneItem(null, "New M", null, null, 1))
    );

    assertThatThrownBy(() -> planService.updatePlan(actor, planId, cmd))
        .isInstanceOf(ResourceConflictException.class)
        .satisfies(ex -> {
          ResourceConflictException rce = (ResourceConflictException) ex;
          assertThat(rce.getErrorCode()).isEqualTo(ErrorCode.PLAN_VERSION_CONFLICT);
        });
  }

  @Test
  @DisplayName("updateMilestoneStatus: Chuyen sang ACHIEVED thanh cong va gui thong bao")
  void updateMilestoneStatus_AchievedNotification() {
    UUID planId = UUID.randomUUID();
    UUID milestoneId = UUID.randomUUID();
    UUID classId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID studentUserId = UUID.randomUUID();
    UUID parentUserId = UUID.randomUUID();
    UUID tutorUserId = UUID.randomUUID();

    CurrentUser actor = mock(CurrentUser.class);
    when(actor.hasRole("TUTOR")).thenReturn(true);
    when(actor.getUserId()).thenReturn(tutorUserId);

    Milestone milestone = new Milestone(
        milestoneId, planId, "Cot moc 1", "Desc", LocalDate.parse("2026-10-25"),
        3, MilestoneStatus.IN_PROGRESS, null, false, 0, fixedInstant
    );

    LearningPlan plan = new LearningPlan(
        planId, classId, "Summary", 0, fixedInstant, fixedInstant, List.of(milestone)
    );
    when(planRepository.findByMilestoneId(milestoneId)).thenReturn(Optional.of(plan));

    ConnectionFacade.ClassDetailView cls = new ConnectionFacade.ClassDetailView(classId, studentId, tutorId, UUID.randomUUID(), "ACTIVE", fixedInstant, null, 200000L);
    when(connectionFacade.findClassById(classId)).thenReturn(Optional.of(cls));
    when(profileFacade.getTutorIdByUserId(tutorUserId)).thenReturn(tutorId);
    when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    StudentSummaryDto studentDto = mock(StudentSummaryDto.class);
    when(studentDto.userId()).thenReturn(studentUserId);
    when(profileFacade.findStudentById(studentId)).thenReturn(Optional.of(studentDto));
    when(connectionFacade.findConfirmedParentUserIds(studentId)).thenReturn(List.of(parentUserId));

    UpdateMilestoneStatusCommand cmd = new UpdateMilestoneStatusCommand(
        0, MilestoneStatus.ACHIEVED, LocalDate.parse("2026-10-20")
    );

    LearningPlan updated = planService.updateMilestoneStatus(actor, milestoneId, cmd);

    assertThat(updated).isNotNull();
    Milestone m = updated.getMilestones().getFirst();
    assertThat(m.getStatus()).isEqualTo(MilestoneStatus.ACHIEVED);
    assertThat(m.getAchievedDate()).isEqualTo(LocalDate.parse("2026-10-20"));

    // Xác nhận gửi notification cho cả Học sinh và Phụ huynh
    verify(notifications).notify(
        eq(studentUserId), eq(studentId), eq("MILESTONE_ACHIEVED"), any(), any(), eq("MILESTONE"), eq(milestoneId)
    );
    verify(notifications).notify(
        eq(parentUserId), eq(studentId), eq("MILESTONE_ACHIEVED"), any(), any(), eq("MILESTONE"), eq(milestoneId)
    );
  }
}

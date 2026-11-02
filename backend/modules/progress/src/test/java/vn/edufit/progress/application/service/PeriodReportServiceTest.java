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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.progress.application.dto.PeriodReportView;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneCriterion;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.model.PeriodReport;
import vn.edufit.progress.domain.policy.PeriodReportPolicy;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.progress.domain.repository.PeriodReportDomainRepository;
import vn.edufit.progress.domain.repository.PlanHistoryDomainRepository;
import vn.edufit.progress.domain.repository.SessionFeedbackDomainRepository;
import vn.edufit.progress.domain.repository.SessionMilestoneLinkDomainRepository;
import vn.edufit.progress.domain.repository.TopicDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ResourceConflictException;
import vn.edufit.shared.notification.NotificationSink;

class PeriodReportServiceTest {

  /** Lớp bắt đầu 01/09 (giờ Việt Nam); hôm nay 02/10 → kỳ 1 là 01/09–30/09 vừa kết thúc. */
  private static final Instant CLASS_START = Instant.parse("2026-08-31T17:00:00Z");
  private static final Instant NOW = Instant.parse("2026-10-02T03:00:00Z");
  private static final LocalDate PERIOD_START = LocalDate.parse("2026-09-01");
  private static final LocalDate PERIOD_END = LocalDate.parse("2026-09-30");

  private final UUID classId = UUID.randomUUID();
  private final UUID endedClassId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID studentUserId = UUID.randomUUID();
  private final UUID parentUserId = UUID.randomUUID();
  private final UUID tutorId = UUID.randomUUID();
  private final UUID tutorUserId = UUID.randomUUID();

  private PeriodReportDomainRepository reportRepository;
  private LearningPlanDomainRepository planRepository;
  private ConnectionFacade connectionFacade;
  private ProfileFacade profileFacade;
  private NotificationSink notifications;
  private PeriodReportService service;
  private ConnectionFacade.ClassDetailView cls;

  @BeforeEach
  void setUp() {
    reportRepository = mock(PeriodReportDomainRepository.class);
    planRepository = mock(LearningPlanDomainRepository.class);
    connectionFacade = mock(ConnectionFacade.class);
    profileFacade = mock(ProfileFacade.class);
    notifications = mock(NotificationSink.class);
    Clock clock = Clock.fixed(NOW, LearningPlan.BUSINESS_ZONE);
    ProgressAccessGuard guard = new ProgressAccessGuard(connectionFacade, profileFacade);
    ProgressOverviewService overview = new ProgressOverviewService(
        mock(SessionFeedbackDomainRepository.class), mock(SessionMilestoneLinkDomainRepository.class),
        mock(PlanHistoryDomainRepository.class), mock(TopicDomainRepository.class), mock(SessionFacade.class),
        profileFacade, connectionFacade, clock);
    service = new PeriodReportService(reportRepository, planRepository, connectionFacade, profileFacade, guard,
        overview, notifications, clock);

    cls = new ConnectionFacade.ClassDetailView(classId, studentId, tutorId, null, "ACTIVE", CLASS_START, null, 200000L);
    when(connectionFacade.findClassById(classId)).thenReturn(Optional.of(cls));
    when(profileFacade.findLearningNeeds(any())).thenReturn(Map.of());
    when(profileFacade.findStudentById(studentId))
        .thenReturn(Optional.of(new StudentSummaryDto(studentId, studentUserId, null, null, null, true)));
    when(connectionFacade.findConfirmedParentUserIds(studentId)).thenReturn(List.of(parentUserId));
    when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private static Milestone milestone(String title, MilestoneStatus status, LocalDate achieved, LocalDate target, int order) {
    MilestoneCriterion criterion = new MilestoneCriterion(UUID.randomUUID(), "Tiêu chí", 0,
        status == MilestoneStatus.ACHIEVED, status == MilestoneStatus.ACHIEVED ? NOW : null, CLASS_START);
    return new Milestone(UUID.randomUUID(), null, title, null, target, 1, status, achieved, false, order,
        CLASS_START, List.of(criterion));
  }

  private LearningPlan plan() {
    return new LearningPlan(UUID.randomUUID(), classId, null, 0, CLASS_START, CLASS_START, List.of(
        milestone("Phương trình bậc nhất", MilestoneStatus.ACHIEVED, PERIOD_START.plusDays(19), null, 0),
        milestone("Hệ phương trình", MilestoneStatus.ACHIEVED, PERIOD_END.plusDays(1), null, 1),
        milestone("Hình học không gian", MilestoneStatus.NOT_STARTED, null, LocalDate.parse("2026-12-20"), 2)));
  }

  private void givenPlannedClasses() {
    when(planRepository.findAllClassIds()).thenReturn(List.of(classId, endedClassId));
    when(connectionFacade.findClassesByIds(List.of(classId, endedClassId))).thenReturn(List.of(cls,
        new ConnectionFacade.ClassDetailView(endedClassId, studentId, tutorId, null, "ENDED", CLASS_START, NOW, 1L)));
    when(planRepository.findByClassIdIn(List.of(classId))).thenReturn(List.of(plan()));
  }

  private PeriodReport draft(int version) {
    PeriodReport.Snapshot snapshot = new PeriodReport.Snapshot("ON_TRACK", "Đúng tiến độ", 40, 20, 7, 8,
        null, "Hình học không gian", List.of("Phương trình bậc nhất"));
    return new PeriodReport(UUID.randomUUID(), classId, 1, PERIOD_START, PERIOD_END, snapshot, null,
        PeriodReport.Status.DRAFT, NOW.plus(PeriodReportPolicy.AUTO_SEND_AFTER), null, version, NOW);
  }

  private CurrentUser user(String role, UUID userId) {
    CurrentUser actor = mock(CurrentUser.class);
    when(actor.hasRole(role)).thenReturn(true);
    when(actor.getUserId()).thenReturn(userId);
    return actor;
  }

  @Test
  @DisplayName("Lập bản nháp cho kỳ vừa kết thúc: ảnh chụp chỉ gồm cột mốc đạt trong kỳ, trọng tâm kỳ tới, tự gửi sau 48h")
  void generateDraftForCompletedPeriod() {
    givenPlannedClasses();

    assertThat(service.generateDueDrafts()).isEqualTo(1);

    ArgumentCaptor<PeriodReport> saved = ArgumentCaptor.forClass(PeriodReport.class);
    verify(reportRepository).save(saved.capture());
    PeriodReport report = saved.getValue();
    assertThat(report.getClassId()).isEqualTo(classId);
    assertThat(report.getPeriodIndex()).isEqualTo(1);
    assertThat(report.getPeriodStart()).isEqualTo(PERIOD_START);
    assertThat(report.getPeriodEnd()).isEqualTo(PERIOD_END);
    assertThat(report.getStatus()).isEqualTo(PeriodReport.Status.DRAFT);
    assertThat(report.getAutoSendAt()).isEqualTo(NOW.plus(PeriodReportPolicy.AUTO_SEND_AFTER));
    assertThat(report.getSnapshot().achievedMilestones()).containsExactly("Phương trình bậc nhất");
    assertThat(report.getSnapshot().nextFocusTitle()).isEqualTo("Hình học không gian");
    assertThat(report.getSnapshot().onTrackStatus()).isNotBlank();
    verify(notifications, never()).notify(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Mỗi kỳ chỉ một báo cáo: kỳ đã có báo cáo thì bỏ qua")
  void generateIsIdempotent() {
    givenPlannedClasses();
    when(reportRepository.existsByClassIdAndPeriodIndex(classId, 1)).thenReturn(true);

    assertThat(service.generateDueDrafts()).isZero();
    verify(reportRepository, never()).save(any());
  }

  @Test
  @DisplayName("Bản nháp quá 48h được tự gửi và thông báo cho học sinh lẫn phụ huynh")
  void overdueDraftsAreSentAndNotified() {
    PeriodReport due = draft(0);
    when(reportRepository.findDraftsDueBy(NOW)).thenReturn(List.of(due));

    assertThat(service.sendOverdueDrafts()).isEqualTo(1);

    assertThat(due.getStatus()).isEqualTo(PeriodReport.Status.SENT);
    assertThat(due.getSentAt()).isEqualTo(NOW);
    verify(notifications).notify(eq(studentUserId), eq(studentId), eq(PeriodReportService.NOTIFICATION_TYPE),
        any(), any(), eq("PERIOD_REPORT"), eq(due.getReportId()));
    verify(notifications).notify(eq(parentUserId), eq(studentId), eq(PeriodReportService.NOTIFICATION_TYPE),
        any(), any(), eq("PERIOD_REPORT"), eq(due.getReportId()));
  }

  @Test
  @DisplayName("Học sinh chỉ thấy báo cáo đã gửi; gia sư thấy cả bản nháp")
  void draftsAreVisibleToTutorOnly() {
    PeriodReport sent = draft(0);
    sent.send(NOW);
    when(reportRepository.findByClassId(classId)).thenReturn(List.of(draft(0), sent));
    CurrentUser student = user("STUDENT", studentUserId);
    when(profileFacade.getStudentIdByUserId(studentUserId)).thenReturn(studentId);
    CurrentUser tutor = user("TUTOR", tutorUserId);
    when(profileFacade.getTutorIdByUserId(tutorUserId)).thenReturn(tutorId);

    assertThat(service.listForClass(student, classId)).extracting(PeriodReportView::status).containsExactly("SENT");
    assertThat(service.listForClass(tutor, classId)).extracting(PeriodReportView::status).containsExactly("DRAFT", "SENT");
  }

  @Test
  @DisplayName("Gia sư gửi ngay kèm câu tổng kết; sai phiên bản (vd. vừa được tự gửi) thì báo xung đột")
  void tutorSendsNowWithSummary() {
    PeriodReport report = draft(3);
    when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
    CurrentUser tutor = user("TUTOR", tutorUserId);
    when(profileFacade.getTutorIdByUserId(tutorUserId)).thenReturn(tutorId);

    assertThatThrownBy(() -> service.sendNow(tutor, report.getReportId(), "Con tiến bộ rõ", 2))
        .isInstanceOf(ResourceConflictException.class);

    PeriodReportView view = service.sendNow(tutor, report.getReportId(), "  Con tiến bộ rõ  ", 3);
    assertThat(view.status()).isEqualTo("SENT");
    assertThat(view.tutorSummary()).isEqualTo("Con tiến bộ rõ");
  }
}

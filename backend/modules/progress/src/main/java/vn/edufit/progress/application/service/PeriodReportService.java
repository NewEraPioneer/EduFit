package vn.edufit.progress.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.progress.application.dto.PeriodReportView;
import vn.edufit.progress.application.dto.ProgressViews.ProgressOverviewView;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.model.PeriodReport;
import vn.edufit.progress.domain.policy.PeriodReportPolicy;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.progress.domain.repository.PeriodReportDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ResourceConflictException;
import vn.edufit.shared.notification.NotificationSink;

/**
 * Báo cáo kỳ 30 ngày (mục 3.13): job lập bản nháp cho lớp đang hoạt động có lộ trình khi một kỳ kết thúc, gia sư
 * viết câu tổng kết tùy chọn và gửi; quá 48h thì tự gửi. Khi gửi, học sinh và phụ huynh nhận thông báo.
 */
@Slf4j
@Service
public class PeriodReportService {

  static final String NOTIFICATION_TYPE = "PERIOD_REPORT_SENT";
  private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

  private final PeriodReportDomainRepository reportRepository;
  private final LearningPlanDomainRepository planRepository;
  private final ConnectionFacade connectionFacade;
  private final ProfileFacade profileFacade;
  private final ProgressAccessGuard accessGuard;
  private final ProgressOverviewService overviewService;
  private final NotificationSink notifications;
  private final Clock clock;

  public PeriodReportService(
      PeriodReportDomainRepository reportRepository,
      LearningPlanDomainRepository planRepository,
      ConnectionFacade connectionFacade,
      ProfileFacade profileFacade,
      ProgressAccessGuard accessGuard,
      ProgressOverviewService overviewService,
      NotificationSink notifications,
      Clock clock
  ) {
    this.reportRepository = reportRepository;
    this.planRepository = planRepository;
    this.connectionFacade = connectionFacade;
    this.profileFacade = profileFacade;
    this.accessGuard = accessGuard;
    this.overviewService = overviewService;
    this.notifications = notifications;
    this.clock = Objects.requireNonNull(clock);
  }

  /** Lập bản nháp cho kỳ vừa kết thúc của từng lớp; mỗi kỳ chỉ một báo cáo. @return số bản nháp mới. */
  @Transactional
  public int generateDueDrafts() {
    Instant now = clock.instant();
    LocalDate today = LocalDate.now(clock);
    List<ConnectionFacade.ClassDetailView> classes = connectionFacade
        .findClassesByIds(planRepository.findAllClassIds()).stream()
        .filter(c -> "ACTIVE".equals(c.status()) && c.startedAt() != null)
        .toList();
    if (classes.isEmpty()) return 0;

    Map<UUID, LearningPlan> plans = planRepository
        .findByClassIdIn(classes.stream().map(ConnectionFacade.ClassDetailView::classId).toList()).stream()
        .collect(Collectors.toMap(LearningPlan::getClassId, p -> p));
    Map<UUID, LearningNeedView> needs = profileFacade.findLearningNeeds(
        classes.stream().map(ConnectionFacade.ClassDetailView::needId).filter(Objects::nonNull).toList());

    int created = 0;
    for (ConnectionFacade.ClassDetailView cls : classes) {
      LearningPlan plan = plans.get(cls.classId());
      Optional<PeriodReportPolicy.Period> period = PeriodReportPolicy.latestCompleted(
          cls.startedAt().atZone(LearningPlan.BUSINESS_ZONE).toLocalDate(), today);
      if (plan == null || period.isEmpty()
          || reportRepository.existsByClassIdAndPeriodIndex(cls.classId(), period.get().index())) {
        continue;
      }
      String goalTitle = ProgressDisplayNames.needTitle(cls.needId() != null ? needs.get(cls.needId()) : null, "Lớp học");
      ProgressOverviewView overview = overviewService.build(cls, plan, goalTitle, List.of());
      reportRepository.save(PeriodReport.draft(
          cls.classId(), period.get().index(), period.get().start(), period.get().end(),
          snapshot(overview, plan, period.get()), now.plus(PeriodReportPolicy.AUTO_SEND_AFTER), now));
      created++;
    }
    if (created > 0) log.info("Đã lập {} bản nháp báo cáo kỳ", created);
    return created;
  }

  /** Gửi các bản nháp đã quá 48h. @return số báo cáo đã gửi. */
  @Transactional
  public int sendOverdueDrafts() {
    Instant now = clock.instant();
    List<PeriodReport> due = reportRepository.findDraftsDueBy(now);
    for (PeriodReport report : due) {
      ConnectionFacade.ClassDetailView cls = accessGuard.requireClass(report.getClassId());
      deliver(report, cls, now);
    }
    if (!due.isEmpty()) log.info("Đã tự gửi {} báo cáo kỳ quá hạn 48 giờ", due.size());
    return due.size();
  }

  /** Gia sư và admin thấy mọi báo cáo; học sinh và phụ huynh chỉ thấy báo cáo đã gửi. Kỳ mới nhất trước. */
  @Transactional(readOnly = true)
  public List<PeriodReportView> listForClass(CurrentUser actor, UUID classId) {
    ConnectionFacade.ClassDetailView cls = accessGuard.requireClass(classId);
    ViewerAudience audience = accessGuard.requireMemberOrParentAccess(
        actor, cls, "Bạn không có quyền xem báo cáo kỳ của lớp này");
    boolean seesDrafts = audience == ViewerAudience.TUTOR || audience == ViewerAudience.ADMIN;
    return reportRepository.findByClassId(classId).stream()
        .filter(r -> seesDrafts || r.isSent())
        .map(PeriodReportView::from)
        .toList();
  }

  @Transactional
  public PeriodReportView editSummary(CurrentUser actor, UUID reportId, String summary, Integer version) {
    PeriodReport report = requireTutorDraft(actor, reportId, version).report();
    report.editSummary(summary);
    return PeriodReportView.from(reportRepository.save(report));
  }

  /** Gia sư gửi ngay, không chờ hết 48h; kèm câu tổng kết cuối cùng nếu có. */
  @Transactional
  public PeriodReportView sendNow(CurrentUser actor, UUID reportId, String summary, Integer version) {
    TutorDraft draft = requireTutorDraft(actor, reportId, version);
    if (summary != null) draft.report().editSummary(summary); // null = giữ câu tổng kết đã lưu
    return PeriodReportView.from(deliver(draft.report(), draft.cls(), clock.instant()));
  }

  private record TutorDraft(PeriodReport report, ConnectionFacade.ClassDetailView cls) {}

  private TutorDraft requireTutorDraft(CurrentUser actor, UUID reportId, Integer version) {
    PeriodReport report = reportRepository.findById(reportId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy báo cáo kỳ với ID: " + reportId));
    ConnectionFacade.ClassDetailView cls = accessGuard.requireClass(report.getClassId());
    accessGuard.requireTutorOfClass(actor, cls);
    if (version == null || version != report.getVersion()) {
      throw new ResourceConflictException(
          ErrorCode.CONFLICT_DETECTED, "Báo cáo kỳ đã thay đổi (có thể vừa được tự gửi). Hãy tải lại.");
    }
    return new TutorDraft(report, cls);
  }

  private PeriodReport deliver(PeriodReport report, ConnectionFacade.ClassDetailView cls, Instant now) {
    report.send(now);
    PeriodReport saved = reportRepository.save(report);
    String title = "Báo cáo tiến độ kỳ " + saved.getPeriodIndex();
    String message = "Báo cáo 30 ngày (" + DAY_MONTH.format(saved.getPeriodStart()) + "–"
        + DAY_MONTH.format(saved.getPeriodEnd()) + ") đã sẵn sàng để xem.";
    profileFacade.findStudentById(cls.studentId()).ifPresent(student ->
        notifications.notify(student.userId(), cls.studentId(), NOTIFICATION_TYPE, title, message,
            "PERIOD_REPORT", saved.getReportId()));
    for (UUID parentUserId : connectionFacade.findConfirmedParentUserIds(cls.studentId())) {
      notifications.notify(parentUserId, cls.studentId(), NOTIFICATION_TYPE, title, message,
          "PERIOD_REPORT", saved.getReportId());
    }
    return saved;
  }

  private static PeriodReport.Snapshot snapshot(
      ProgressOverviewView overview, LearningPlan plan, PeriodReportPolicy.Period period) {
    List<String> achieved = plan.orderedActiveMilestones().stream()
        .filter(m -> m.getStatus() == MilestoneStatus.ACHIEVED && period.contains(m.getAchievedDate()))
        .map(Milestone::getTitle)
        .toList();
    return new PeriodReport.Snapshot(
        overview.onTrack().status(),
        overview.onTrack().explanation(),
        overview.goalProgress().progressPercentage(),
        overview.goalProgress().progressDelta(),
        overview.attendance().attendedCount(),
        overview.attendance().countedCount(),
        overview.goalProgress().highlight(),
        plan.findFocusMilestone().map(Milestone::getTitle).orElse(null),
        achieved);
  }
}

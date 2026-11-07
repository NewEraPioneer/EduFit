package vn.edufit.progress.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.progress.application.dto.CreatePlanCommand;
import vn.edufit.progress.application.dto.UpdateMilestoneStatusCommand;
import vn.edufit.progress.application.dto.UpdatePlanCommand;
import org.springframework.context.ApplicationEventPublisher;
import vn.edufit.progress.api.event.MilestoneAchievedEvent;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.model.PlanHistory;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.progress.domain.repository.PlanHistoryDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

@Slf4j
@Service
public class LearningPlanService {

  static final String ACTION_STATUS_CHANGED = "MILESTONE_STATUS_CHANGED";
  static final String ACTION_REOPENED = "MILESTONE_REOPENED";
  static final String ACTION_UPDATED = "MILESTONE_UPDATED";

  private final LearningPlanDomainRepository planRepository;
  private final PlanHistoryDomainRepository historyRepository;
  private final ProgressAccessGuard accessGuard;
  private final ApplicationEventPublisher events;
  private final TopicCatalogService topicCatalog;
  private final Clock clock;

  public LearningPlanService(
      LearningPlanDomainRepository planRepository,
      PlanHistoryDomainRepository historyRepository,
      ProgressAccessGuard accessGuard,
      ApplicationEventPublisher events,
      TopicCatalogService topicCatalog,
      Clock clock
  ) {
    this.planRepository = planRepository;
    this.historyRepository = historyRepository;
    this.accessGuard = accessGuard;
    this.events = events;
    this.topicCatalog = topicCatalog;
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Tạo Kế hoạch học tập mới cho lớp học (UC5.1).
   * Chỉ Gia sư của lớp học ACTIVE mới có quyền tạo. Mỗi cột mốc bắt buộc có 1–4 tiêu chí đạt.
   */
  @Transactional
  public LearningPlan createPlan(CurrentUser actor, CreatePlanCommand req) {
    ConnectionFacade.ClassDetailView cls = accessGuard.requireClass(req.classId());
    accessGuard.requireTutorOfClass(actor, cls);
    accessGuard.requireActiveClass(cls, "Chỉ có thể tạo kế hoạch học tập cho lớp học đang hoạt động");

    if (planRepository.existsByClassId(req.classId())) {
      throw new ResourceConflictException("Lớp học này đã có kế hoạch học tập");
    }
    topicCatalog.requireAssignable(
        cls, req.milestones().stream().map(CreatePlanCommand.CreateMilestoneItem::topicId).toList(), Set.of());

    LocalDate today = LocalDate.now(clock);
    Instant now = Instant.now(clock);
    List<Milestone> initialMilestones = new ArrayList<>();
    int order = 0;
    for (CreatePlanCommand.CreateMilestoneItem item : req.milestones()) {
      initialMilestones.add(Milestone.createNew(
          null, // planId will be set by LearningPlan.createNew
          item.title(),
          item.description(),
          item.targetDate(),
          item.weight() != null ? item.weight() : 1,
          order++,
          item.criteria(),
          Milestone.Planning.of(item.suggestedSessions(), item.topicId()),
          today,
          now
      ));
    }

    LearningPlan plan = LearningPlan.createNew(req.classId(), req.summary(), initialMilestones, now);
    LearningPlan saved = planRepository.save(plan);

    historyRepository.save(PlanHistory.of(
        saved.getPlanId(), null, "PLAN_CREATED", actor.getUserId(), null, saved.getSummary(), now
    ));

    log.info("Tạo kế hoạch học tập thành công: planId={}, classId={}, tutorUserId={}",
        saved.getPlanId(), saved.getClassId(), actor.getUserId());

    return saved;
  }

  /**
   * Xem Kế hoạch học tập theo mã lớp học (UC5.2).
   * Quyền xem: Gia sư của lớp, Học sinh của lớp, hoặc Phụ huynh có liên kết CONFIRMED.
   */
  @Transactional(readOnly = true)
  public LearningPlan getPlanByClassId(CurrentUser actor, UUID classId) {
    ConnectionFacade.ClassDetailView cls = accessGuard.requireClass(classId);
    accessGuard.requireMemberOrParentAccess(actor, cls, "Bạn không có quyền truy cập kế hoạch học tập của lớp này");

    return planRepository.findByClassId(classId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy kế hoạch học tập cho lớp học ID: " + classId));
  }

  /**
   * Cập nhật Kế hoạch học tập (UC5.3).
   * Kiểm tra Optimistic Locking qua trường version.
   */
  @Transactional
  public LearningPlan updatePlan(CurrentUser actor, UUID planId, UpdatePlanCommand req) {
    LearningPlan plan = planRepository.findById(planId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy kế hoạch học tập với ID: " + planId));

    ConnectionFacade.ClassDetailView cls = accessGuard.requireClass(plan.getClassId());
    accessGuard.requireTutorOfClass(actor, cls);
    accessGuard.requireActiveClass(cls, "Chỉ có thể cập nhật kế hoạch học tập cho lớp học đang hoạt động");
    requireVersion(plan, req.version());
    topicCatalog.requireAssignable(
        cls,
        req.milestones().stream().map(UpdatePlanCommand.UpdateMilestoneItem::topicId).toList(),
        plan.getMilestones().stream().map(Milestone::getTopicId).filter(Objects::nonNull).collect(Collectors.toSet()));

    LocalDate today = LocalDate.now(clock);
    Instant now = Instant.now(clock);

    String oldSummary = plan.getSummary();
    if (!Objects.equals(oldSummary, req.summary())) {
      plan.updateSummary(req.summary(), now);
      historyRepository.save(PlanHistory.of(
          planId, null, "PLAN_SUMMARY_UPDATED", actor.getUserId(), oldSummary, req.summary(), now
      ));
    }

    Map<UUID, UpdatePlanCommand.UpdateMilestoneItem> incomingMap = req.milestones().stream()
        .filter(m -> m.milestoneId() != null)
        .collect(Collectors.toMap(UpdatePlanCommand.UpdateMilestoneItem::milestoneId, m -> m));

    List<Milestone> currentList = new ArrayList<>(plan.getMilestones());
    List<Milestone> nextList = new ArrayList<>();

    // 1. Milestone hiện có: cập nhật, archive (nếu đã tiến triển) hoặc xóa thật
    for (Milestone existing : currentList) {
      UpdatePlanCommand.UpdateMilestoneItem incoming = incomingMap.get(existing.getMilestoneId());
      if (incoming != null) {
        boolean changed = existing.updateDetails(
            incoming.title(),
            incoming.description(),
            incoming.targetDate(),
            incoming.weight() != null ? incoming.weight() : existing.getWeight(),
            req.milestones().indexOf(incoming),
            toDrafts(incoming.criteria()),
            Milestone.Planning.of(incoming.suggestedSessions(), incoming.topicId()),
            today,
            now
        );
        if (changed) {
          historyRepository.save(PlanHistory.of(
              planId, existing.getMilestoneId(), ACTION_UPDATED, actor.getUserId(), null, existing.getTitle(), now
          ));
        }
        nextList.add(existing);
      } else if (existing.hasProgressed()) {
        existing.archive();
        nextList.add(existing);
        historyRepository.save(PlanHistory.of(
            planId, existing.getMilestoneId(), "MILESTONE_ARCHIVED", actor.getUserId(), existing.getTitle(), null, now
        ));
      } else {
        historyRepository.save(PlanHistory.of(
            planId, existing.getMilestoneId(), "MILESTONE_DELETED", actor.getUserId(), existing.getTitle(), null, now
        ));
      }
    }

    // 2. Milestone mới (chưa có milestoneId hoặc milestoneId lạ)
    for (int i = 0; i < req.milestones().size(); i++) {
      UpdatePlanCommand.UpdateMilestoneItem item = req.milestones().get(i);
      if (item.milestoneId() == null || currentList.stream().noneMatch(m -> m.getMilestoneId().equals(item.milestoneId()))) {
        List<String> contents = item.criteria() != null
            ? item.criteria().stream().map(UpdatePlanCommand.CriterionItem::content).toList()
            : List.of();
        Milestone newMilestone = Milestone.createNew(
            planId, item.title(), item.description(), item.targetDate(),
            item.weight() != null ? item.weight() : 1, i, contents,
            Milestone.Planning.of(item.suggestedSessions(), item.topicId()), today, now);
        nextList.add(newMilestone);
        historyRepository.save(PlanHistory.of(
            planId, newMilestone.getMilestoneId(), "MILESTONE_CREATED", actor.getUserId(), null, item.title(), now
        ));
      }
    }

    LearningPlan updatedPlan = new LearningPlan(
        plan.getPlanId(),
        plan.getClassId(),
        plan.getSummary(),
        plan.getVersion(),
        plan.getCreatedAt(),
        now,
        nextList
    );
    updatedPlan.validateShape();

    LearningPlan saved = planRepository.save(updatedPlan);
    log.info("Cập nhật kế hoạch học tập thành công: planId={}, newVersion={}", saved.getPlanId(), saved.getVersion());
    return saved;
  }

  /**
   * Cập nhật trạng thái một Cột mốc (UC5.4).
   *
   * <ul>
   *   <li>{@code ACHIEVED}: gia sư chốt, yêu cầu tick đủ tiêu chí; phát thông báo tới học sinh và phụ huynh.</li>
   *   <li>{@code ACHIEVED → IN_PROGRESS}: mở lại, bắt buộc có lý do; ngày đạt cũ được lưu trong plan_history.</li>
   *   <li>{@code IN_PROGRESS} tự động khi buổi học được gắn vào cột mốc, không đặt thủ công.</li>
   * </ul>
   */
  @Transactional
  public LearningPlan updateMilestoneStatus(CurrentUser actor, UUID milestoneId, UpdateMilestoneStatusCommand req) {
    LearningPlan plan = planRepository.findByMilestoneId(milestoneId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy cột mốc với ID: " + milestoneId));

    ConnectionFacade.ClassDetailView cls = accessGuard.requireClass(plan.getClassId());
    accessGuard.requireTutorOfClass(actor, cls);
    accessGuard.requireActiveClass(cls, "Chỉ có thể cập nhật cột mốc cho lớp học đang hoạt động");
    requireVersion(plan, req.version());

    Milestone milestone = plan.findActiveMilestone(milestoneId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy cột mốc trong kế hoạch"));
    if (req.status() == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Trạng thái cột mốc không được để trống");
    }

    LocalDate today = LocalDate.now(clock);
    Instant now = Instant.now(clock);
    MilestoneStatus oldStatus = milestone.getStatus();

    if (req.status() == MilestoneStatus.ACHIEVED) {
      milestone.achieve(req.achievedDate(), today);
      historyRepository.save(PlanHistory.of(
          plan.getPlanId(), milestoneId, ACTION_STATUS_CHANGED, actor.getUserId(),
          oldStatus.name(), MilestoneStatus.ACHIEVED.name(), now
      ));
    } else if (req.status() == MilestoneStatus.IN_PROGRESS && oldStatus == MilestoneStatus.ACHIEVED) {
      LocalDate previousAchievedDate = milestone.reopen(req.reason());
      historyRepository.save(PlanHistory.of(
          plan.getPlanId(), milestoneId, ACTION_REOPENED, actor.getUserId(),
          MilestoneStatus.ACHIEVED.name() + "@" + previousAchievedDate,
          MilestoneStatus.IN_PROGRESS.name() + ": " + req.reason().trim(),
          now
      ));
    } else {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Chỉ có thể chốt đạt hoặc mở lại cột mốc đã đạt. Trạng thái \"Đang học\" được cập nhật tự động khi buổi học được gắn vào cột mốc."
      );
    }

    LearningPlan saved = planRepository.save(plan);

    if (req.status() == MilestoneStatus.ACHIEVED) {
      events.publishEvent(MilestoneAchievedEvent.of(
          cls.classId(), cls.studentId(), milestone.getMilestoneId(), milestone.getTitle()));
    }

    log.info("Cập nhật trạng thái milestone thành công: milestoneId={}, oldStatus={}, newStatus={}",
        milestoneId, oldStatus, req.status());
    return saved;
  }

  private static void requireVersion(LearningPlan plan, Integer requestedVersion) {
    if (requestedVersion == null || plan.getVersion() != requestedVersion) {
      throw new ResourceConflictException(
          ErrorCode.PLAN_VERSION_CONFLICT,
          "Kế hoạch đã bị chỉnh sửa bởi phiên làm việc khác (phiên bản máy chủ: "
              + plan.getVersion() + ", phiên bản gửi lên: " + requestedVersion + ")"
      );
    }
  }

  private static List<Milestone.CriterionDraft> toDrafts(List<UpdatePlanCommand.CriterionItem> items) {
    if (items == null) return List.of();
    return items.stream().map(c -> new Milestone.CriterionDraft(c.criterionId(), c.content())).toList();
  }
}

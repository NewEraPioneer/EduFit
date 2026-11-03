package vn.edufit.progress.application.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.progress.application.dto.SessionMilestonesView;
import vn.edufit.progress.application.dto.SessionMilestonesView.MilestoneRef;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneStatus;
import vn.edufit.progress.domain.policy.SessionMilestoneResolver;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.progress.domain.repository.SessionMilestoneLinkDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionSummaryView;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Liên kết "dự kiến" buổi học ↔ cột mốc (mục 3.5): gia sư gắn tùy chọn cho buổi chưa diễn ra;
 * lịch hiển thị cột mốc hiệu lực của từng buổi (thực tế thắng dự kiến). Module Scheduling không biết cột mốc.
 */
@Service
public class SessionMilestonePlanningService {

  /** Trạng thái buổi học chưa diễn ra, còn được gắn cột mốc dự kiến. */
  static final Set<String> PLANNABLE_STATUSES = Set.of("PROPOSED", "SCHEDULED");
  public static final int MAX_PLANNED_MILESTONES = 3;
  public static final int MAX_LOOKUP_SESSIONS = 200;

  private final SessionMilestoneLinkDomainRepository linkRepository;
  private final LearningPlanDomainRepository planRepository;
  private final SessionFacade sessionFacade;
  private final ProgressAccessGuard accessGuard;
  private final Clock clock;

  public SessionMilestonePlanningService(
      SessionMilestoneLinkDomainRepository linkRepository,
      LearningPlanDomainRepository planRepository,
      SessionFacade sessionFacade,
      ProgressAccessGuard accessGuard,
      Clock clock
  ) {
    this.linkRepository = linkRepository;
    this.planRepository = planRepository;
    this.sessionFacade = sessionFacade;
    this.accessGuard = accessGuard;
    this.clock = Objects.requireNonNull(clock);
  }

  /** Thay danh sách cột mốc dự kiến của một buổi học chưa diễn ra (rỗng = bỏ dự kiến). */
  @Transactional
  public SessionMilestonesView planMilestones(CurrentUser actor, UUID sessionId, List<UUID> milestoneIds) {
    SessionSummaryView session = sessionFacade.findSessionById(sessionId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy buổi học với ID: " + sessionId));
    ConnectionFacade.ClassDetailView cls = accessGuard.requireClass(session.classId());
    accessGuard.requireTutorOfClass(actor, cls);
    accessGuard.requireActiveClass(cls, "Chỉ gắn cột mốc dự kiến cho buổi học của lớp đang hoạt động");
    if (!PLANNABLE_STATUSES.contains(session.status())) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION, "Chỉ gắn cột mốc dự kiến cho buổi học chưa diễn ra");
    }

    Set<UUID> requested = new LinkedHashSet<>(milestoneIds != null ? milestoneIds : List.of());
    if (requested.size() > MAX_PLANNED_MILESTONES) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED, "Một buổi học gắn tối đa " + MAX_PLANNED_MILESTONES + " cột mốc dự kiến");
    }
    LearningPlan plan = planRepository.findByClassId(cls.classId())
        .orElseThrow(() -> new InvalidOperationException(
            ErrorCode.INVALID_OPERATION, "Lớp học chưa có lộ trình để gắn cột mốc dự kiến"));
    List<MilestoneRef> refs = new ArrayList<>();
    for (UUID id : requested) {
      Milestone m = plan.findActiveMilestone(id)
          .orElseThrow(() -> new InvalidOperationException(
              ErrorCode.VALIDATION_FAILED, "Cột mốc " + id + " không thuộc lộ trình của lớp"));
      if (m.getStatus() == MilestoneStatus.ACHIEVED) {
        throw new InvalidOperationException(
            ErrorCode.VALIDATION_FAILED, "Cột mốc \"" + m.getTitle() + "\" đã đạt, không cần dự kiến dạy thêm");
      }
      refs.add(new MilestoneRef(m.getMilestoneId(), m.getTitle()));
    }

    linkRepository.replacePlanned(sessionId, requested, actor.getUserId(), clock.instant());
    return new SessionMilestonesView(sessionId, "PLANNED", refs);
  }

  /**
   * Cột mốc hiệu lực của các buổi học (dùng cho lịch). Buổi thuộc lớp người xem không có quyền, hoặc chưa có
   * liên kết nào, bị bỏ qua.
   */
  @Transactional(readOnly = true)
  public List<SessionMilestonesView> findSessionMilestones(CurrentUser actor, Collection<UUID> sessionIds) {
    if (sessionIds == null || sessionIds.isEmpty()) return List.of();
    if (sessionIds.size() > MAX_LOOKUP_SESSIONS) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED, "Tra cứu tối đa " + MAX_LOOKUP_SESSIONS + " buổi học mỗi lần");
    }
    Map<UUID, List<SessionSummaryView>> byClass = sessionFacade.findSessionsByIds(sessionIds).stream()
        .collect(Collectors.groupingBy(SessionSummaryView::classId));
    List<UUID> visibleClassIds = byClass.keySet().stream()
        .filter(classId -> {
          ConnectionFacade.ClassDetailView cls = accessGuard.requireClass(classId);
          return accessGuard.resolveAudience(actor, cls.tutorId(), cls.studentId()).isPresent();
        })
        .toList();
    if (visibleClassIds.isEmpty()) return List.of();

    Map<UUID, String> titles = new HashMap<>();
    planRepository.findByClassIdIn(visibleClassIds)
        .forEach(p -> p.getMilestones().forEach(m -> titles.put(m.getMilestoneId(), m.getTitle())));
    List<UUID> visibleSessionIds = visibleClassIds.stream()
        .flatMap(c -> byClass.get(c).stream())
        .map(SessionSummaryView::sessionId)
        .toList();

    return SessionMilestoneResolver.resolve(linkRepository.findBySessionIdIn(visibleSessionIds)).entrySet().stream()
        .map(e -> new SessionMilestonesView(
            e.getKey(),
            e.getValue().type().name(),
            e.getValue().milestoneIds().stream()
                .filter(titles::containsKey)
                .map(id -> new MilestoneRef(id, titles.get(id)))
                .toList()))
        .filter(v -> !v.milestones().isEmpty())
        .toList();
  }
}

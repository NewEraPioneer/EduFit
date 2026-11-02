package vn.edufit.progress.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.progress.application.dto.ProgressViews.ClassSelectorItemView;
import vn.edufit.progress.application.dto.ProgressViews.ParentDashboardView;
import vn.edufit.progress.application.dto.ProgressViews.ParentStudentSummaryView;
import vn.edufit.progress.application.dto.ProgressViews.PlanDetailView;
import vn.edufit.progress.application.dto.ProgressViews.ProgressOverviewView;
import vn.edufit.progress.application.dto.ProgressViews.StudentDashboardView;
import vn.edufit.progress.application.dto.ProgressViews.TeachingHistoryItemView;
import vn.edufit.progress.application.dto.SessionNoteTimelineView;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.ClassSessionStatsView;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;

@Slf4j
@Service
public class ProgressDashboardService {

  private static final int RECENT_NOTES_LIMIT = 10;

  private final LearningPlanDomainRepository planRepository;
  private final ConnectionFacade connectionFacade;
  private final ProfileFacade profileFacade;
  private final ProgressDisplayNames names;
  private final SessionFacade sessionFacade;
  private final SessionNoteTimelineAssembler timelineAssembler;
  private final ProgressOverviewService overviewService;
  private final ProgressAccessGuard accessGuard;

  public ProgressDashboardService(
      LearningPlanDomainRepository planRepository,
      ConnectionFacade connectionFacade,
      ProfileFacade profileFacade,
      ProgressDisplayNames names,
      SessionFacade sessionFacade,
      SessionNoteTimelineAssembler timelineAssembler,
      ProgressOverviewService overviewService,
      ProgressAccessGuard accessGuard
  ) {
    this.planRepository = planRepository;
    this.connectionFacade = connectionFacade;
    this.profileFacade = profileFacade;
    this.names = names;
    this.sessionFacade = sessionFacade;
    this.timelineAssembler = timelineAssembler;
    this.overviewService = overviewService;
    this.accessGuard = accessGuard;
  }

  /**
   * Lấy dữ liệu Dashboard Tiến độ học tập cho Học sinh (hoặc Phụ huynh xem con).
   */
  @Transactional(readOnly = true)
  public StudentDashboardView getStudentDashboard(
      CurrentUser actor,
      UUID studentId,
      UUID specifiedClassId
  ) {
    if (actor.hasRole("STUDENT")) {
      UUID actorStudentId = profileFacade.getStudentIdByUserId(actor.getUserId());
      if (actor.getUserId().equals(studentId) && actorStudentId != null) {
        studentId = actorStudentId;
      }
    }
    requireStudentAccess(actor, studentId);

    StudentSummaryDto student = profileFacade.findStudentById(studentId).orElse(null);
    String studentName = names.studentName(student);

    List<ConnectionFacade.ClassDetailView> classes = connectionFacade.findClassesByStudent(studentId);
    if (classes.isEmpty()) {
      return new StudentDashboardView(
          studentId, studentName, List.of(), null, null, null, null, null,
          null, null, null, List.of(), false
      );
    }

    // Batch query plans và goals để tránh N+1
    List<UUID> classIds = classes.stream().map(ConnectionFacade.ClassDetailView::classId).toList();
    Map<UUID, LearningPlan> planMap = planRepository.findByClassIdIn(classIds).stream()
        .collect(Collectors.toMap(LearningPlan::getClassId, p -> p));

    List<UUID> needIds = classes.stream().map(ConnectionFacade.ClassDetailView::needId).filter(java.util.Objects::nonNull).toList();
    Map<UUID, LearningNeedView> goalMap = profileFacade.findLearningNeeds(needIds);

    List<ClassSelectorItemView> selectorItems = new ArrayList<>();
    for (ConnectionFacade.ClassDetailView c : classes) {
      LearningPlan p = planMap.get(c.classId());
      Integer prog = p != null ? p.calculateProgressPercentage() : null;
      String title = ProgressDisplayNames.needTitle(goalMap.get(c.needId()), "Lớp học");
      selectorItems.add(new ClassSelectorItemView(c.classId(), title, c.status(), prog));
    }

    // Chọn lớp hiển thị: nếu có specifiedClassId thì dùng, nếu không chọn lớp ACTIVE đầu tiên
    ConnectionFacade.ClassDetailView selectedClass = null;
    if (specifiedClassId != null) {
      selectedClass = classes.stream()
          .filter(c -> c.classId().equals(specifiedClassId))
          .findFirst()
          .orElse(null);
    }
    if (selectedClass == null) {
      selectedClass = classes.stream()
          .filter(c -> "ACTIVE".equals(c.status()))
          .findFirst()
          .orElse(classes.getFirst());
    }

    UUID classId = selectedClass.classId();
    LearningPlan plan = planMap.get(classId);
    if (plan == null) {
      plan = planRepository.findByClassId(classId).orElse(null);
    }

    // Thông tin Tutor
    String tutorName = names.tutorName(selectedClass.tutorId());

    // Tiêu đề mục tiêu
    String goalTitle = ProgressDisplayNames.needTitle(goalMap.get(selectedClass.needId()), "Mục tiêu học tập");

    // Người xem quyết định phần riêng tư: gia sư dạy học sinh ở lớp khác chỉ có quyền như gia sư.
    ViewerAudience audience = accessGuard
        .resolveAudience(actor, selectedClass.tutorId(), selectedClass.studentId())
        .orElse(ViewerAudience.TUTOR);

    // Sổ liên lạc: chỉ các buổi đã có nhận xét/ghi nhận, mới nhất trước
    List<SessionNoteTimelineView> timeline = timelineAssembler.assemble(
        sessionFacade.findSessionsByClassId(classId, null, null), plan, audience
    ).stream()
        .filter(n -> n.recordedAt() != null)
        .sorted(Comparator.comparing(SessionNoteTimelineView::startAt).reversed())
        .toList();
    List<SessionNoteTimelineView> noteViews = timeline.stream().limit(RECENT_NOTES_LIMIT).toList();
    boolean hasMoreNotes = timeline.size() > RECENT_NOTES_LIMIT;

    ProgressOverviewView overview = overviewService.build(selectedClass, plan, goalTitle, timeline);

    return new StudentDashboardView(
        studentId,
        studentName,
        selectorItems,
        classId,
        selectedClass.tutorId(),
        tutorName,
        selectedClass.needId(),
        goalTitle,
        plan != null ? plan.calculateProgressPercentage() : null,
        plan != null ? PlanDetailView.from(plan) : null,
        overview,
        noteViews,
        hasMoreNotes
    );
  }

  /**
   * Lấy dữ liệu Dashboard cho Phụ huynh nhóm theo từng học sinh.
   */
  @Transactional(readOnly = true)
  public ParentDashboardView getParentDashboard(CurrentUser actor) {
    if (!actor.hasRole("PARENT")) {
      throw new ForbiddenOperationException("Chỉ tài khoản Phụ huynh mới có quyền truy cập");
    }

    List<UUID> studentIds = connectionFacade.findConfirmedStudentIds(actor.getUserId());
    List<ParentStudentSummaryView> summaries = new ArrayList<>();

    for (UUID sId : studentIds) {
      StudentDashboardView studentDash = getStudentDashboard(actor, sId, null);
      summaries.add(new ParentStudentSummaryView(
          sId,
          studentDash.studentName(),
          studentDash.classes(),
          studentDash
      ));
    }

    return new ParentDashboardView(summaries);
  }

  /**
   * Lấy Lịch sử giảng dạy (Teaching History) của Gia sư có phân trang.
   * Tối ưu hiệu năng: Phân trang danh sách lớp TRƯỚC KHI hydrate metadata nặng.
   */
  @Transactional(readOnly = true)
  public Page<TeachingHistoryItemView> getTeachingHistory(
      CurrentUser actor,
      int page,
      int size,
      String studentNameFilter,
      String statusFilter,
      Instant from,
      Instant to
  ) {
    if (!actor.hasRole("TUTOR")) {
      throw new ForbiddenOperationException("Chỉ tài khoản Gia sư mới có quyền truy cập Lịch sử giảng dạy");
    }

    UUID tutorId = profileFacade.getTutorIdByUserId(actor.getUserId());
    if (tutorId == null) {
      return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
    }

    List<ConnectionFacade.ClassDetailView> classes = connectionFacade.findClassesByTutor(tutorId);
    if (classes.isEmpty()) {
      return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
    }

    // Filter theo status nếu có
    if (statusFilter != null && !statusFilter.isBlank()) {
      classes = classes.stream().filter(c -> statusFilter.equalsIgnoreCase(c.status())).toList();
    }

    // Filter theo studentName nếu có
    if (studentNameFilter != null && !studentNameFilter.isBlank()) {
      String filterLower = studentNameFilter.toLowerCase();
      classes = classes.stream().filter(c -> {
        StudentSummaryDto s = profileFacade.findStudentById(c.studentId()).orElse(null);
        String name = names.studentName(s);
        return name.toLowerCase().contains(filterLower);
      }).toList();
    }

    int totalElements = classes.size();
    int start = Math.min(page * size, totalElements);
    int end = Math.min(start + size, totalElements);
    List<ConnectionFacade.ClassDetailView> pagedClasses = classes.subList(start, end);

    if (pagedClasses.isEmpty()) {
      return new PageImpl<>(List.of(), PageRequest.of(page, size), totalElements);
    }

    // Batch query session stats & plans cho đúng các lớp trong trang hiện tại (chống N+1)
    List<UUID> pagedClassIds = pagedClasses.stream().map(ConnectionFacade.ClassDetailView::classId).toList();
    Map<UUID, ClassSessionStatsView> statsMap = sessionFacade.getSessionStatsByClassIds(pagedClassIds, from, to);
    Map<UUID, LearningPlan> planMap = planRepository.findByClassIdIn(pagedClassIds).stream()
        .collect(Collectors.toMap(LearningPlan::getClassId, p -> p));

    List<UUID> pagedNeedIds = pagedClasses.stream().map(ConnectionFacade.ClassDetailView::needId).filter(java.util.Objects::nonNull).toList();
    Map<UUID, LearningNeedView> goalMap = profileFacade.findLearningNeeds(pagedNeedIds);

    List<TeachingHistoryItemView> items = new ArrayList<>();
    for (ConnectionFacade.ClassDetailView c : pagedClasses) {
      StudentSummaryDto student = profileFacade.findStudentById(c.studentId()).orElse(null);
      String studentName = names.studentName(student);

      String goalTitle = ProgressDisplayNames.needTitle(goalMap.get(c.needId()), "Mục tiêu");

      LearningPlan p = planMap.get(c.classId());
      Integer progress = p != null ? p.calculateProgressPercentage() : null;

      ClassSessionStatsView stats = statsMap.get(c.classId());

      items.add(new TeachingHistoryItemView(
          c.classId(),
          c.studentId(),
          studentName,
          c.needId(),
          goalTitle,
          c.status(),
          progress,
          c.startedAt(),
          c.endedAt(),
          stats
      ));
    }

    return new PageImpl<>(items, PageRequest.of(page, size), totalElements);
  }

  private void requireStudentAccess(CurrentUser actor, UUID studentId) {
    if (actor.hasRole("ADMIN")) return;

    if (actor.hasRole("STUDENT")) {
      if (actor.getUserId().equals(studentId)) return;
      UUID actorStudentId = profileFacade.getStudentIdByUserId(actor.getUserId());
      if (actorStudentId != null && (actorStudentId.equals(studentId) || actor.getUserId().equals(studentId))) return;
    }

    if (actor.hasRole("PARENT")) {
      if (connectionFacade.hasConfirmedParentLink(actor.getUserId(), studentId)) return;
    }

    if (actor.hasRole("TUTOR")) {
      UUID tutorId = profileFacade.getTutorIdByUserId(actor.getUserId());
      if (tutorId != null) {
        boolean teaches = connectionFacade.findClassesByTutor(tutorId).stream()
            .anyMatch(c -> c.studentId().equals(studentId));
        if (teaches) return;
      }
    }

    throw new ForbiddenOperationException("Không có quyền truy cập tiến độ của học sinh này");
  }
}

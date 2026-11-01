package vn.edufit.progress.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
import vn.edufit.iam.api.IamFacade;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.ConnectionGoalDto;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.progress.application.dto.ProgressViews.ClassSelectorItemView;
import vn.edufit.progress.application.dto.ProgressViews.ParentDashboardView;
import vn.edufit.progress.application.dto.ProgressViews.ParentStudentSummaryView;
import vn.edufit.progress.application.dto.ProgressViews.PlanDetailView;
import vn.edufit.progress.application.dto.ProgressViews.ProgressSeriesPointView;
import vn.edufit.progress.application.dto.ProgressViews.StudentDashboardView;
import vn.edufit.progress.application.dto.ProgressViews.TeachingHistoryItemView;
import vn.edufit.progress.application.dto.SessionNoteTimelineView;
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
import vn.edufit.shared.exception.ForbiddenOperationException;

@Slf4j
@Service
public class ProgressDashboardService {

  private final LearningPlanDomainRepository planRepository;
  private final SessionNoteDomainRepository noteRepository;
  private final ConnectionFacade connectionFacade;
  private final ProfileFacade profileFacade;
  private final IamFacade iamFacade;
  private final SessionFacade sessionFacade;
  private final Clock clock;

  public ProgressDashboardService(
      LearningPlanDomainRepository planRepository,
      SessionNoteDomainRepository noteRepository,
      ConnectionFacade connectionFacade,
      ProfileFacade profileFacade,
      IamFacade iamFacade,
      SessionFacade sessionFacade,
      Clock clock
  ) {
    this.planRepository = planRepository;
    this.noteRepository = noteRepository;
    this.connectionFacade = connectionFacade;
    this.profileFacade = profileFacade;
    this.iamFacade = iamFacade;
    this.sessionFacade = sessionFacade;
    this.clock = java.util.Objects.requireNonNull(clock);
  }

  /**
   * Lấy dữ liệu Dashboard Tiến độ học tập cho Học sinh (hoặc Phụ huynh xem con).
   */
  @Transactional(readOnly = true)
  public StudentDashboardView getStudentDashboard(
      CurrentUser actor,
      UUID studentId,
      UUID specifiedClassId,
      String range
  ) {
    if (actor.hasRole("STUDENT")) {
      UUID actorStudentId = profileFacade.getStudentIdByUserId(actor.getUserId());
      if (actor.getUserId().equals(studentId) && actorStudentId != null) {
        studentId = actorStudentId;
      }
    }
    requireStudentAccess(actor, studentId);

    StudentSummaryDto student = profileFacade.findStudentById(studentId).orElse(null);
    String studentName = resolveStudentName(student);

    List<ConnectionFacade.ClassDetailView> classes = connectionFacade.findClassesByStudent(studentId);
    if (classes.isEmpty()) {
      return new StudentDashboardView(
          studentId, studentName, List.of(), null, null, null, null, null,
          null, null, null, List.of(), List.of(), false
      );
    }

    // Batch query plans và goals để tránh N+1
    List<UUID> classIds = classes.stream().map(ConnectionFacade.ClassDetailView::classId).toList();
    Map<UUID, LearningPlan> planMap = planRepository.findByClassIdIn(classIds).stream()
        .collect(Collectors.toMap(LearningPlan::getClassId, p -> p));

    List<UUID> goalIds = classes.stream().map(ConnectionFacade.ClassDetailView::goalId).filter(java.util.Objects::nonNull).toList();
    Map<UUID, ConnectionGoalDto> goalMap = profileFacade.findGoalsForConnection(goalIds);

    List<ClassSelectorItemView> selectorItems = new ArrayList<>();
    for (ConnectionFacade.ClassDetailView c : classes) {
      LearningPlan p = planMap.get(c.classId());
      Integer prog = p != null ? p.calculateProgressPercentage() : null;
      ConnectionGoalDto g = goalMap.get(c.goalId());
      String title = (g != null && g.goalType() != null)
          ? "Mục tiêu " + g.goalType()
          : "Lớp học";
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
    String tutorName = "Gia sư";
    if (selectedClass.tutorId() != null) {
      TutorSummaryDto tutor = profileFacade.findTutorById(selectedClass.tutorId()).orElse(null);
      if (tutor != null && tutor.userId() != null) {
        tutorName = iamFacade.findUserSummaryById(tutor.userId())
            .map(UserSummaryView::fullName)
            .orElse("Gia sư");
      }
    }

    // Tiêu đề mục tiêu
    ConnectionGoalDto goal = goalMap.get(selectedClass.goalId());
    String goalTitle = (goal != null && goal.goalType() != null)
        ? "Mục tiêu " + goal.goalType()
        : "Mục tiêu học tập";

    // Session stats từ Scheduling
    Map<UUID, ClassSessionStatsView> statsMap = sessionFacade.getSessionStatsByClassIds(List.of(classId), null, null);
    ClassSessionStatsView stats = statsMap.get(classId);

    // Biểu đồ chuỗi thời gian tiến độ
    List<ProgressSeriesPointView> series = buildProgressSeries(plan, range);

    // 10 ghi chú gần nhất
    List<SessionTimelineView> recentSessions = sessionFacade.findSessionsByClassId(classId, null, null);
    List<UUID> sessionIds = recentSessions.stream().map(SessionTimelineView::sessionId).toList();
    Map<UUID, SessionNote> notesMap = noteRepository.findBySessionIdIn(sessionIds).stream()
        .collect(Collectors.toMap(SessionNote::getSessionId, n -> n));

    List<SessionNoteTimelineView> noteViews = recentSessions.stream()
        .filter(s -> notesMap.containsKey(s.sessionId()))
        .limit(10)
        .map(s -> {
          SessionNote note = notesMap.get(s.sessionId());
          return new SessionNoteTimelineView(
              s.sessionId(), s.classId(), s.startAt(), s.endAt(), s.status(),
              note.getNoteText(), note.getCreatedAt()
          );
        })
        .toList();

    boolean hasMoreNotes = notesMap.size() > 10;

    return new StudentDashboardView(
        studentId,
        studentName,
        selectorItems,
        classId,
        selectedClass.tutorId(),
        tutorName,
        selectedClass.goalId(),
        goalTitle,
        plan != null ? plan.calculateProgressPercentage() : null,
        plan != null ? PlanDetailView.from(plan) : null,
        stats,
        series,
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
      StudentDashboardView studentDash = getStudentDashboard(actor, sId, null, "30D");
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
        String name = resolveStudentName(s);
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

    List<UUID> pagedGoalIds = pagedClasses.stream().map(ConnectionFacade.ClassDetailView::goalId).filter(java.util.Objects::nonNull).toList();
    Map<UUID, ConnectionGoalDto> goalMap = profileFacade.findGoalsForConnection(pagedGoalIds);

    List<TeachingHistoryItemView> items = new ArrayList<>();
    for (ConnectionFacade.ClassDetailView c : pagedClasses) {
      StudentSummaryDto student = profileFacade.findStudentById(c.studentId()).orElse(null);
      String studentName = resolveStudentName(student);

      ConnectionGoalDto goal = goalMap.get(c.goalId());
      String goalTitle = (goal != null && goal.goalType() != null)
          ? "Mục tiêu " + goal.goalType()
          : "Mục tiêu";

      LearningPlan p = planMap.get(c.classId());
      Integer progress = p != null ? p.calculateProgressPercentage() : null;

      ClassSessionStatsView stats = statsMap.get(c.classId());

      items.add(new TeachingHistoryItemView(
          c.classId(),
          c.studentId(),
          studentName,
          c.goalId(),
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

  // ── Helper xây dựng biểu đồ tích lũy tiến độ theo thời gian ───────────────

  private List<ProgressSeriesPointView> buildProgressSeries(LearningPlan plan, String range) {
    if (plan == null) return List.of();

    List<Milestone> active = plan.getActiveMilestones();
    int totalWeight = active.stream().mapToInt(Milestone::getWeight).sum();
    if (totalWeight == 0) return List.of();

    LocalDate today = LocalDate.now(clock);
    LocalDate rangeStart = switch (range != null ? range.toUpperCase() : "30D") {
      case "90D" -> today.minusDays(90);
      case "ALL" -> LocalDate.MIN;
      default -> today.minusDays(30);
    };

    // Lọc các milestone ACHIEVED có achievedDate
    List<Milestone> achieved = active.stream()
        .filter(m -> m.getStatus() == MilestoneStatus.ACHIEVED && m.getAchievedDate() != null)
        .sorted(Comparator.comparing(Milestone::getAchievedDate))
        .toList();

    if (achieved.isEmpty()) return List.of();

    // Điểm mở đầu: nếu có milestone achieved trước rangeStart
    int priorWeight = achieved.stream()
        .filter(m -> m.getAchievedDate().isBefore(rangeStart))
        .mapToInt(Milestone::getWeight)
        .sum();

    List<ProgressSeriesPointView> points = new ArrayList<>();
    if (rangeStart != LocalDate.MIN && priorWeight > 0) {
      int initialPercent = BigDecimal.valueOf(priorWeight)
          .multiply(BigDecimal.valueOf(100))
          .divide(BigDecimal.valueOf(totalWeight), 0, RoundingMode.HALF_UP)
          .intValue();
      points.add(new ProgressSeriesPointView(rangeStart, initialPercent));
    }

    int runningWeight = priorWeight;
    for (Milestone m : achieved) {
      if (m.getAchievedDate().isBefore(rangeStart)) continue;
      runningWeight += m.getWeight();
      int percent = BigDecimal.valueOf(runningWeight)
          .multiply(BigDecimal.valueOf(100))
          .divide(BigDecimal.valueOf(totalWeight), 0, RoundingMode.HALF_UP)
          .intValue();
      points.add(new ProgressSeriesPointView(m.getAchievedDate(), percent));
    }

    return points;
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

  private String resolveStudentName(StudentSummaryDto student) {
    if (student == null || student.userId() == null) return "Học sinh";
    return iamFacade.findUserSummaryById(student.userId())
        .map(UserSummaryView::fullName)
        .orElse("Học sinh");
  }
}

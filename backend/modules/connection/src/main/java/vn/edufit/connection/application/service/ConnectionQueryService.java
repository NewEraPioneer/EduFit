package vn.edufit.connection.application.service;

import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ClassScheduleStatusProvider;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.connection.api.TutorScheduleConflictChecker;
import vn.edufit.connection.application.dto.AdminParentStudentLinkResponse;
import vn.edufit.connection.application.dto.AdminParentStudentLinkStatsResponse;
import vn.edufit.connection.application.dto.InvitationResult;
import vn.edufit.connection.application.dto.ParentStudentLinkResult;
import vn.edufit.connection.application.dto.ConnectionRequestResult;
import vn.edufit.connection.application.dto.TutorInboxResult;
import vn.edufit.connection.application.dto.PageResponse;
import vn.edufit.connection.application.policy.SlotConflictDetector;
import vn.edufit.connection.infra.persistence.entity.LinkInvitation;
import vn.edufit.connection.infra.persistence.entity.ConnectionRequest;
import vn.edufit.connection.infra.persistence.entity.ParentStudentLink;
import vn.edufit.connection.infra.persistence.entity.TutoringClass;
import vn.edufit.connection.infra.persistence.repository.LinkInvitationRepository;
import vn.edufit.connection.infra.persistence.repository.ParentStudentLinkRepository;
import vn.edufit.connection.infra.persistence.repository.ConnectionRequestRepository;
import vn.edufit.connection.infra.persistence.repository.TutoringClassRepository;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.ConnectionGoalDto;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ResourceConflictException;
import vn.edufit.shared.exception.ErrorCode;

@Service
public class ConnectionQueryService {
  private final Clock clock;
  private final LinkInvitationRepository invitations;
  private final ParentStudentLinkRepository links;
  private final ConnectionRequestRepository requests;
  private final ProfileFacade profile;
  private final IamFacade iam;
  private final ConnectionFacade facade;
  private final ConnectionExpiryService expiryService;
  private final TutoringClassRepository classes;
  private final SlotConflictDetector slotConflictDetector;
  private final TutorScheduleConflictChecker scheduleConflictChecker;
  private final ClassScheduleStatusProvider scheduleStatusProvider;

  @org.springframework.beans.factory.annotation.Autowired
  public ConnectionQueryService(LinkInvitationRepository invitations, ParentStudentLinkRepository links,
      ConnectionRequestRepository requests, TutoringClassRepository classes, ProfileFacade profile,
      IamFacade iam, ConnectionFacade facade, ConnectionExpiryService expiryService,
      SlotConflictDetector slotConflictDetector, TutorScheduleConflictChecker scheduleConflictChecker,
      ClassScheduleStatusProvider scheduleStatusProvider,
      Clock clock) {
    this.clock = Objects.requireNonNull(clock);
    this.invitations = Objects.requireNonNull(invitations);
    this.links = Objects.requireNonNull(links);
    this.requests = Objects.requireNonNull(requests);
    this.classes = Objects.requireNonNull(classes);
    this.profile = Objects.requireNonNull(profile);
    this.iam = Objects.requireNonNull(iam);
    this.facade = Objects.requireNonNull(facade);
    this.expiryService = Objects.requireNonNull(expiryService);
    this.slotConflictDetector = Objects.requireNonNull(slotConflictDetector);
    this.scheduleConflictChecker = Objects.requireNonNull(scheduleConflictChecker);
    this.scheduleStatusProvider = Objects.requireNonNull(scheduleStatusProvider);
  }


  private static void requireRole(CurrentUser actor, String... roles) {
    for (String role : roles) if (actor.hasRole(role)) return;
    throw new ForbiddenOperationException("Không có quyền thực hiện thao tác này");
  }

  private void requireStudentAccess(CurrentUser actor, UUID studentId) {
    if (actor.hasRole("STUDENT") && profile.findStudentById(studentId)
        .map(s -> s.userId().equals(actor.getUserId())).orElse(false)) return;
    if (actor.hasRole("PARENT") && facade.hasConfirmedParentLink(actor.getUserId(), studentId)) return;
    throw new ForbiddenOperationException("Không có quyền với học sinh này");
  }

  @Transactional
  public List<InvitationResult> invitations(CurrentUser actor) {
    requireRole(actor, "PARENT", "STUDENT");
    List<LinkInvitation> result = invitations.findByInviterUserIdOrInvitedUserIdOrderByCreatedAtDesc(
        actor.getUserId(), actor.getUserId());
    Instant now = clock.instant();
    for (LinkInvitation i : result) {
      if ("PENDING".equals(i.getStatus()) && vn.edufit.connection.application.policy.ConnectionTransitions.isDue(i.getExpiresAt(), now)) {
        expiryService.expireInvitation(i.getId(), now);
        i.expire(now);
      }
    }
    if (result.isEmpty()) {
      return List.of();
    }
    List<UUID> inviterUserIds = result.stream().map(LinkInvitation::getInviterUserId).distinct().toList();
    Map<UUID, UserSummaryView> inviterSummaries = iam.findUserSummariesByIds(inviterUserIds);
    return result.stream().map(i -> {
      UserSummaryView inviter = inviterSummaries.get(i.getInviterUserId());
      String inviterName = inviter != null ? inviter.fullName() : null;
      String inviterRole = (inviter != null && inviter.role() != null) ? inviter.role().name() : null;
      return InvitationResult.from(i, inviterName, inviterRole);
    }).toList();
  }

  @Transactional(readOnly = true)
  public List<ParentStudentLinkResult> links(CurrentUser actor) {
    requireRole(actor, "PARENT", "STUDENT");
    if (actor.hasRole("PARENT")) return links.findByParentUserIdAndStatus(actor.getUserId(), "CONFIRMED").stream().map(ParentStudentLinkResult::from).toList();
    UUID studentId = profile.getStudentIdByUserId(actor.getUserId());
    return links.findByStudentIdAndStatus(studentId, "CONFIRMED").stream().map(ParentStudentLinkResult::from).toList();
  }

  @Transactional
  public List<ConnectionRequestResult> requests(CurrentUser actor, UUID studentId) {
    requireRole(actor, "STUDENT", "PARENT");
    UUID scope = actor.hasRole("STUDENT") ? profile.getStudentIdByUserId(actor.getUserId()) : studentId;
    if (scope == null) throw new InvalidOperationException("Cần chỉ định học sinh");
    requireStudentAccess(actor, scope);
    List<ConnectionRequest> result = requests.findByStudentIdOrderByCreatedAtDesc(scope);
    Instant now = clock.instant();
    for (ConnectionRequest r : result) {
      if ("PENDING".equals(r.getStatus()) && vn.edufit.connection.application.policy.ConnectionTransitions.isDue(r.getExpiresAt(), now)) {
        expiryService.expireRequest(r.getId(), now);
        r.expire(now);
      }
    }
    return result.stream().map(ConnectionRequestResult::from).toList();
  }

  @Transactional
  public List<TutorInboxResult> tutorInbox(CurrentUser actor) {
    requireRole(actor, "TUTOR");
    UUID tutorId = profile.getTutorIdByUserId(actor.getUserId());
    List<ConnectionRequest> result = requests.findByTutorIdOrderByCreatedAtDesc(tutorId);
    Instant now = clock.instant();
    for (ConnectionRequest r : result) {
      if ("PENDING".equals(r.getStatus()) && vn.edufit.connection.application.policy.ConnectionTransitions.isDue(r.getExpiresAt(), now)) {
        expiryService.expireRequest(r.getId(), now);
        r.expire(now);
      }
    }
    if (result.isEmpty()) {
      return List.of();
    }
    List<UUID> goalIds = result.stream().map(ConnectionRequest::getGoalId).distinct().toList();
    List<UUID> sentByUserIds = result.stream().map(ConnectionRequest::getSentByUserId).distinct().toList();
    Map<UUID, ConnectionGoalDto> goalsMap = profile.findGoalsForConnection(goalIds);
    Map<UUID, UserSummaryView> usersMap = iam.findUserSummariesByIds(sentByUserIds);

    List<String> activeSlots = getActiveSlotsForTutor(tutorId);

    return result.stream().map(r -> {
      ConnectionGoalDto goal = goalsMap.get(r.getGoalId());
      if (goal == null) {
        throw new ResourceConflictException(ErrorCode.CONFLICT_DETECTED,
            "Không tìm thấy dữ liệu mục tiêu học tập liên quan");
      }
      UserSummaryView sender = usersMap.get(r.getSentByUserId());
      if (sender == null) {
        throw new ResourceConflictException(ErrorCode.CONFLICT_DETECTED,
            "Không tìm thấy thông tin người gửi yêu cầu");
      }
      return mapToTutorInboxResult(r, goal, sender, usersMap, activeSlots, tutorId);
    }).toList();
  }

  private static void validatePageAndSize(int page, int size) {
    if (page < 0) throw new InvalidOperationException("Số trang không hợp lệ: page phải >= 0");
    if (size < 1 || size > 100) throw new InvalidOperationException("Kích thước trang không hợp lệ: size phải từ 1 đến 100");
  }

  @Transactional
  public PageResponse<InvitationResult> invitationsPage(CurrentUser actor, int page, int size) {
    requireRole(actor, "PARENT", "STUDENT");
    validatePageAndSize(page, size);
    org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
    org.springframework.data.domain.Page<LinkInvitation> p = invitations.findScopedInvitations(actor.getUserId(), pageable);
    List<LinkInvitation> content = p.getContent();
    Instant now = clock.instant();
    for (LinkInvitation i : content) {
      if ("PENDING".equals(i.getStatus()) && vn.edufit.connection.application.policy.ConnectionTransitions.isDue(i.getExpiresAt(), now)) {
        expiryService.expireInvitation(i.getId(), now);
        i.expire(now);
      }
    }
    if (content.isEmpty()) {
      return PageResponse.of(List.of(), page, size, p.getTotalElements());
    }
    List<UUID> inviterUserIds = content.stream().map(LinkInvitation::getInviterUserId).distinct().toList();
    Map<UUID, UserSummaryView> inviterSummaries = iam.findUserSummariesByIds(inviterUserIds);
    List<InvitationResult> items = content.stream().map(i -> {
      UserSummaryView inviter = inviterSummaries.get(i.getInviterUserId());
      String inviterName = inviter != null ? inviter.fullName() : null;
      String inviterRole = (inviter != null && inviter.role() != null) ? inviter.role().name() : null;
      return InvitationResult.from(i, inviterName, inviterRole);
    }).toList();
    return PageResponse.of(items, page, size, p.getTotalElements());
  }

  @Transactional(readOnly = true)
  public PageResponse<ParentStudentLinkResult> linksPage(CurrentUser actor, int page, int size) {
    requireRole(actor, "PARENT", "STUDENT");
    validatePageAndSize(page, size);
    org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
    org.springframework.data.domain.Page<ParentStudentLink> p;
    if (actor.hasRole("PARENT")) {
      p = links.findByParentUserIdAndStatusConfirmed(actor.getUserId(), pageable);
    } else {
      UUID studentId = profile.getStudentIdByUserId(actor.getUserId());
      p = links.findByStudentIdAndStatusConfirmed(studentId, pageable);
    }
    List<ParentStudentLinkResult> items = p.getContent().stream().map(ParentStudentLinkResult::from).toList();
    return PageResponse.of(items, page, size, p.getTotalElements());
  }

  @Transactional(readOnly = true)
  public PageResponse<ParentStudentLinkResult> adminLinksPage(CurrentUser actor, String email, int page, int size) {
    requireRole(actor, "ADMIN");
    if (email == null || email.isBlank()) {
      throw new InvalidOperationException("Email không được để trống");
    }
    validatePageAndSize(page, size);

    Optional<UserSummaryView> userOpt = iam.findUserSummaryByEmail(email.trim());
    if (userOpt.isEmpty()) {
      return PageResponse.of(List.of(), page, size, 0L);
    }
    UserSummaryView targetUser = userOpt.get();
    org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);

    if (targetUser.role() == AccountRole.PARENT) {
      org.springframework.data.domain.Page<ParentStudentLink> p = links.findByParentUserIdAndStatusConfirmed(targetUser.id(), pageable);
      List<ParentStudentLinkResult> items = p.getContent().stream().map(ParentStudentLinkResult::from).toList();
      return PageResponse.of(items, page, size, p.getTotalElements());
    } else if (targetUser.role() == AccountRole.STUDENT) {
      Optional<StudentSummaryDto> studentOpt = profile.findStudentByUserId(targetUser.id());
      if (studentOpt.isEmpty()) {
        return PageResponse.of(List.of(), page, size, 0L);
      }
      UUID studentId = studentOpt.get().studentId();
      org.springframework.data.domain.Page<ParentStudentLink> p = links.findByStudentIdAndStatusConfirmed(studentId, pageable);
      List<ParentStudentLinkResult> items = p.getContent().stream().map(ParentStudentLinkResult::from).toList();
      return PageResponse.of(items, page, size, p.getTotalElements());
    } else {
      return PageResponse.of(List.of(), page, size, 0L);
    }
  }

  @Transactional(readOnly = true)
  public PageResponse<AdminParentStudentLinkResponse> adminGetAllLinks(
      CurrentUser actor,
      String status,
      String search,
      int page,
      int size
  ) {
    requireRole(actor, "ADMIN");
    validatePageAndSize(page, size);
    org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);

    org.springframework.data.domain.Page<ParentStudentLink> p;
    if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
      p = links.findByStatus(status.toUpperCase(), pageable);
    } else {
      p = links.findAllLinks(pageable);
    }

    List<ParentStudentLink> content = p.getContent();
    if (content.isEmpty()) {
      return PageResponse.of(List.of(), page, size, p.getTotalElements());
    }

    // Resolve Parents
    List<UUID> parentUserIds = content.stream().map(ParentStudentLink::getParentUserId).distinct().toList();
    Map<UUID, UserSummaryView> parentSummaries = iam.findUserSummariesByIds(parentUserIds);

    // Resolve Students
    List<UUID> studentIds = content.stream().map(ParentStudentLink::getStudentId).distinct().toList();
    Map<UUID, StudentSummaryDto> studentProfiles = new HashMap<>();
    List<UUID> studentUserIds = new ArrayList<>();
    for (UUID sId : studentIds) {
      profile.findStudentById(sId).ifPresent(s -> {
        studentProfiles.put(sId, s);
        if (s.userId() != null) {
          studentUserIds.add(s.userId());
        }
      });
    }
    Map<UUID, UserSummaryView> studentUserSummaries = iam.findUserSummariesByIds(studentUserIds);

    List<AdminParentStudentLinkResponse> responses = content.stream().map(link -> {
      UserSummaryView parent = parentSummaries.get(link.getParentUserId());
      String parentName = parent != null ? parent.fullName() : "Chưa cập nhật";
      String parentEmail = parent != null ? parent.email() : "";

      StudentSummaryDto studentProfile = studentProfiles.get(link.getStudentId());
      UUID studentUserId = studentProfile != null ? studentProfile.userId() : null;
      UserSummaryView studentUser = studentUserId != null ? studentUserSummaries.get(studentUserId) : null;
      String studentName = studentUser != null ? studentUser.fullName() : "Học sinh";
      String studentEmail = studentUser != null ? studentUser.email() : "";

      return new AdminParentStudentLinkResponse(
          link.getId(),
          link.getParentUserId(),
          parentName,
          parentEmail,
          link.getStudentId(),
          studentUserId,
          studentName,
          studentEmail,
          link.getStatus(),
          link.getConfirmedAt(),
          link.getRevokedAt(),
          link.getRevokeReason()
      );
    }).toList();

    if (search != null && !search.isBlank()) {
      String q = search.trim().toLowerCase();
      responses = responses.stream().filter(r ->
          (r.parentFullName() != null && r.parentFullName().toLowerCase().contains(q)) ||
          (r.parentEmail() != null && r.parentEmail().toLowerCase().contains(q)) ||
          (r.studentFullName() != null && r.studentFullName().toLowerCase().contains(q)) ||
          (r.studentEmail() != null && r.studentEmail().toLowerCase().contains(q))
      ).toList();
    }

    return PageResponse.of(responses, page, size, p.getTotalElements());
  }

  @Transactional(readOnly = true)
  public AdminParentStudentLinkStatsResponse adminGetLinkStats(CurrentUser actor) {
    requireRole(actor, "ADMIN");
    long totalLinks = links.count();
    long activeLinks = links.countByStatus("CONFIRMED");
    long revokedLinks = links.countByStatus("REVOKED");
    long uniqueParents = links.countDistinctParentUserIdByStatus("CONFIRMED");
    long uniqueStudents = links.countDistinctStudentIdByStatus("CONFIRMED");

    return new AdminParentStudentLinkStatsResponse(
        totalLinks,
        activeLinks,
        revokedLinks,
        uniqueParents,
        uniqueStudents
    );
  }

  @Transactional
  public PageResponse<ConnectionRequestResult> requestsPage(CurrentUser actor, UUID studentId, int page, int size) {
    requireRole(actor, "STUDENT", "PARENT");
    validatePageAndSize(page, size);
    UUID scope = actor.hasRole("STUDENT") ? profile.getStudentIdByUserId(actor.getUserId()) : studentId;
    if (scope == null) throw new InvalidOperationException("Cần chỉ định học sinh");
    requireStudentAccess(actor, scope);
    org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
    org.springframework.data.domain.Page<ConnectionRequest> p = requests.findByStudentIdScoped(scope, pageable);
    List<ConnectionRequest> content = p.getContent();
    Instant now = clock.instant();
    for (ConnectionRequest r : content) {
      if ("PENDING".equals(r.getStatus()) && vn.edufit.connection.application.policy.ConnectionTransitions.isDue(r.getExpiresAt(), now)) {
        expiryService.expireRequest(r.getId(), now);
        r.expire(now);
      }
    }
    List<ConnectionRequestResult> items = content.stream().map(ConnectionRequestResult::from).toList();
    return PageResponse.of(items, page, size, p.getTotalElements());
  }

  @Transactional
  public PageResponse<TutorInboxResult> tutorInboxPage(CurrentUser actor, int page, int size) {
    requireRole(actor, "TUTOR");
    validatePageAndSize(page, size);
    UUID tutorId = profile.getTutorIdByUserId(actor.getUserId());
    org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
    org.springframework.data.domain.Page<ConnectionRequest> p = requests.findByTutorIdScoped(tutorId, pageable);
    List<ConnectionRequest> content = p.getContent();
    Instant now = clock.instant();
    for (ConnectionRequest r : content) {
      if ("PENDING".equals(r.getStatus()) && vn.edufit.connection.application.policy.ConnectionTransitions.isDue(r.getExpiresAt(), now)) {
        expiryService.expireRequest(r.getId(), now);
        r.expire(now);
      }
    }
    if (content.isEmpty()) {
      return PageResponse.of(List.of(), page, size, p.getTotalElements());
    }
    List<UUID> goalIds = content.stream().map(ConnectionRequest::getGoalId).distinct().toList();
    List<UUID> sentByUserIds = content.stream().map(ConnectionRequest::getSentByUserId).distinct().toList();
    Map<UUID, ConnectionGoalDto> goalsMap = profile.findGoalsForConnection(goalIds);
    Map<UUID, UserSummaryView> usersMap = iam.findUserSummariesByIds(sentByUserIds);

    List<String> activeSlots = getActiveSlotsForTutor(tutorId);

    List<TutorInboxResult> items = content.stream().map(r -> {
      ConnectionGoalDto goal = goalsMap.get(r.getGoalId());
      if (goal == null) {
        throw new ResourceConflictException(ErrorCode.CONFLICT_DETECTED,
            "Không tìm thấy dữ liệu mục tiêu học tập liên quan");
      }
      UserSummaryView sender = usersMap.get(r.getSentByUserId());
      if (sender == null) {
        throw new ResourceConflictException(ErrorCode.CONFLICT_DETECTED,
            "Không tìm thấy thông tin người gửi yêu cầu");
      }
      return mapToTutorInboxResult(r, goal, sender, usersMap, activeSlots, tutorId);
    }).toList();
    return PageResponse.of(items, page, size, p.getTotalElements());
  }

  private List<String> getActiveSlotsForTutor(UUID tutorId) {
    List<TutoringClass> activeClasses = classes.findByTutorIdAndStatus(tutorId, "ACTIVE");
    if (activeClasses.isEmpty()) {
      return List.of();
    }
    List<UUID> reqIds = activeClasses.stream()
        .map(TutoringClass::getConnectionRequestId)
        .filter(Objects::nonNull)
        .toList();
    if (reqIds.isEmpty()) {
      return List.of();
    }
    List<ConnectionRequest> reqs = requests.findAllById(reqIds);
    return reqs.stream()
        .filter(r -> r.getDesiredSlots() != null)
        .flatMap(r -> r.getDesiredSlots().stream())
        .toList();
  }

  private static String toSafeDisplayName(String fullName) {
    if (fullName == null || fullName.isBlank()) return "Học sinh";
    String[] parts = fullName.trim().split("\\s+");
    if (parts.length == 1) return parts[0];
    if (parts.length == 2) return parts[0] + " " + parts[1];
    return parts[0] + " " + parts[parts.length - 1]; // e.g. "Nguyễn Văn Minh" -> "Nguyễn Minh"
  }

  private TutorInboxResult mapToTutorInboxResult(
      ConnectionRequest r,
      ConnectionGoalDto goal,
      UserSummaryView sender,
      Map<UUID, UserSummaryView> usersMap,
      List<String> activeSlots,
      UUID tutorId
  ) {
    String senderRole = sender.role() == AccountRole.PARENT ? "PARENT" : "STUDENT";
    String studentDisplayName;
    if (sender.role() == AccountRole.STUDENT) {
      studentDisplayName = toSafeDisplayName(sender.fullName());
    } else if (goal.studentUserId() != null && usersMap.containsKey(goal.studentUserId())) {
      studentDisplayName = toSafeDisplayName(usersMap.get(goal.studentUserId()).fullName());
    } else {
      studentDisplayName = sender.fullName() != null ? toSafeDisplayName(sender.fullName()) : "Học sinh";
    }

    List<String> conflicts = r.getDesiredSlots() != null
        ? slotConflictDetector.findConflictingSlots(r.getDesiredSlots(), activeSlots)
        : List.of();
    boolean hasConflict = !conflicts.isEmpty();
    if (!hasConflict && r.getDesiredSlots() != null
        && scheduleConflictChecker.hasConflictWithScheduledSessions(tutorId, r.getDesiredSlots())) {
      hasConflict = true;
    }

    return new TutorInboxResult(
        r.getId(),
        goal.goalType(),
        goal.subjectId(),
        goal.educationLevelId(),
        r.getDesiredSlots(),
        r.getMessage(),
        senderRole,
        r.getStatus(),
        r.getExpiresAt(),
        goal.deadline(),
        studentDisplayName,
        goal.currentLevel(),
        goal.desiredLevel(),
        goal.budgetMin(),
        goal.budgetMax(),
        goal.area(),
        goal.subjectName(),
        goal.educationLevelName(),
        hasConflict,
        conflicts
    );
  }

  @Transactional(readOnly = true)
  public List<vn.edufit.connection.application.dto.TutoringClassResult> classes(CurrentUser actor) {
    requireRole(actor, "TUTOR", "STUDENT", "PARENT");
    List<TutoringClass> classList;
    if (actor.hasRole("TUTOR")) {
      UUID tutorId = profile.getTutorIdByUserId(actor.getUserId());
      classList = tutorId != null ? classes.findByTutorId(tutorId) : List.of();
    } else if (actor.hasRole("STUDENT")) {
      UUID studentId = profile.getStudentIdByUserId(actor.getUserId());
      classList = studentId != null ? classes.findByStudentId(studentId) : List.of();
    } else {
      List<ParentStudentLinkResult> linkResults = links(actor);
      List<UUID> sIds = linkResults.stream().map(ParentStudentLinkResult::studentId).distinct().toList();
      classList = sIds.stream().flatMap(s -> classes.findByStudentId(s).stream()).toList();
    }
    if (classList.isEmpty()) {
      return List.of();
    }

    List<UUID> goalIds = classList.stream().map(TutoringClass::getGoalId).distinct().toList();
    Map<UUID, ConnectionGoalDto> goalsMap = profile.findGoalsForConnection(goalIds);

    List<UUID> classIds = classList.stream().map(TutoringClass::getId).toList();
    Map<UUID, ClassScheduleStatusProvider.ClassScheduleInfo> scheduleInfoMap =
        !classIds.isEmpty()
            ? scheduleStatusProvider.getScheduleInfoBatch(classIds)
            : Map.of();

    return classList.stream().map(c -> {
      ConnectionGoalDto goal = goalsMap.get(c.getGoalId());
      String subjectName = goal != null ? goal.subjectName() : null;
      String educationLevelName = goal != null ? goal.educationLevelName() : null;

      String studentName = null;
      if (profile != null && iam != null) {
        studentName = profile.findStudentById(c.getStudentId())
            .flatMap(s -> iam.findUserSummaryById(s.userId()))
            .map(u -> toSafeDisplayName(u.fullName()))
            .orElse("Học sinh");
      }

      String tutorName = null;
      if (profile != null) {
        tutorName = profile.findTutorById(c.getTutorId())
            .map(vn.edufit.profile.api.dto.TutorSummaryDto::displayName)
            .orElse("Gia sư");
      }

      ClassScheduleStatusProvider.ClassScheduleInfo scheduleInfo = scheduleInfoMap.get(c.getId());
      String scheduleStatus = scheduleInfo != null ? scheduleInfo.scheduleStatus() : "PROPOSED";
      int sessionCount = scheduleInfo != null ? scheduleInfo.sessionCount() : 0;
      int completedCount = scheduleInfo != null ? scheduleInfo.completedCount() : 0;
      int scheduledCount = scheduleInfo != null ? scheduleInfo.scheduledCount() : 0;
      int totalCount = scheduleInfo != null ? scheduleInfo.totalCount() : 0;

      return new vn.edufit.connection.application.dto.TutoringClassResult(
          c.getId(),
          c.getStudentId(),
          c.getTutorId(),
          c.getGoalId(),
          c.getStatus(),
          studentName,
          tutorName,
          subjectName,
          educationLevelName,
          scheduleStatus,
          sessionCount,
          completedCount,
          scheduledCount,
          totalCount,
          c.getStartedAt(),
          c.getEndedAt()
      );
    }).toList();
  }
}

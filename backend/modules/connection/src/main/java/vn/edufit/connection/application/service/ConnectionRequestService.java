package vn.edufit.connection.application.service;

import java.time.Instant;
import java.time.Clock;
import vn.edufit.connection.application.dto.TransitionResult;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.connection.application.policy.ConnectionRequestInput;
import vn.edufit.connection.application.dto.ConnectionRequestResult;
import vn.edufit.connection.application.dto.ClassResult;
import vn.edufit.connection.application.port.ConnectionLock;
import vn.edufit.connection.infra.persistence.entity.ConnectionRequest;
import vn.edufit.connection.infra.persistence.entity.TutoringClass;
import vn.edufit.connection.infra.persistence.repository.ConnectionRequestRepository;
import vn.edufit.connection.infra.persistence.repository.TutoringClassRepository;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.LearningNeedView;
import vn.edufit.profile.api.dto.TutorOfferingDto;
import vn.edufit.profile.api.fit.NeedFitEvaluator;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.notification.NotificationSink;
import org.springframework.context.ApplicationEventPublisher;
import vn.edufit.connection.api.event.ConnectionRequestChangedEvent;
import vn.edufit.connection.api.event.TutoringClassCreatedEvent;
import vn.edufit.connection.api.event.TutoringClassEndedEvent;
import vn.edufit.connection.application.dto.TutoringClassResult;
import vn.edufit.connection.application.policy.ConnectionTransitions;
import vn.edufit.connection.api.TutorScheduleConflictChecker;
import vn.edufit.connection.application.policy.SlotConflictDetector;
import vn.edufit.connection.api.ClassScheduleStatusProvider;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;
import vn.edufit.shared.exception.ErrorCode;

@Service
public class ConnectionRequestService {
  private final Clock clock;
  private final ConnectionRequestRepository requests;
  private final TutoringClassRepository classes;
  private final ProfileFacade profile;
  private final IamFacade iam;
  private final ConnectionLock lock;
  private final NotificationSink notifications;
  private final ConnectionFacade facade;
  private final ConnectionRequestInput input;
  private final ApplicationEventPublisher events;
  private final ConnectionExpiryService expiryService;
  private final SlotConflictDetector slotConflictDetector;
  private final TutorScheduleConflictChecker scheduleConflictChecker;
  private final ClassScheduleStatusProvider scheduleStatusProvider;

  public ConnectionRequestService(ConnectionRequestRepository requests, TutoringClassRepository classes,
      ProfileFacade profile, IamFacade iam, ConnectionLock lock, NotificationSink notifications,
      ConnectionFacade facade, ConnectionRequestInput input, ApplicationEventPublisher events,
      ConnectionExpiryService expiryService, SlotConflictDetector slotConflictDetector,
      TutorScheduleConflictChecker scheduleConflictChecker,
      ClassScheduleStatusProvider scheduleStatusProvider,
      Clock clock) {
    this.clock = Objects.requireNonNull(clock);
    this.requests = Objects.requireNonNull(requests);
    this.classes = Objects.requireNonNull(classes);
    this.profile = Objects.requireNonNull(profile);
    this.iam = Objects.requireNonNull(iam);
    this.lock = Objects.requireNonNull(lock);
    this.notifications = Objects.requireNonNull(notifications);
    this.facade = Objects.requireNonNull(facade);
    this.input = Objects.requireNonNull(input);
    this.events = Objects.requireNonNull(events);
    // Hết hạn, chống trùng lịch và chặn kết thúc lớp là quy tắc nghiệp vụ bắt buộc, không được tùy chọn
    this.expiryService = Objects.requireNonNull(expiryService);
    this.slotConflictDetector = Objects.requireNonNull(slotConflictDetector);
    this.scheduleConflictChecker = Objects.requireNonNull(scheduleConflictChecker);
    this.scheduleStatusProvider = Objects.requireNonNull(scheduleStatusProvider);
  }

  private static void requireRole(CurrentUser actor, String... roles) {
    for (String role : roles) if (actor.hasRole(role)) return;
    throw new ForbiddenOperationException("Không có quyền thực hiện thao tác này");
  }

  private void requireActiveActor(CurrentUser actor) {
    AccountRole expected = actor.hasRole("TUTOR") ? AccountRole.TUTOR
        : actor.hasRole("PARENT") ? AccountRole.PARENT : AccountRole.STUDENT;
    iam.findUserSummaryById(actor.getUserId())
        .filter(user -> user.id().equals(actor.getUserId()) && user.status() == AccountStatus.ACTIVE && user.role() == expected)
        .orElseThrow(() -> new ForbiddenOperationException("Tài khoản không còn quyền thực hiện thao tác"));
  }

  private void requireStudentAccess(CurrentUser actor, UUID studentId) {
    if (actor.hasRole("STUDENT") && profile.findStudentById(studentId)
        .map(s -> s.userId().equals(actor.getUserId())).orElse(false)) return;
    if (actor.hasRole("PARENT") && facade.hasConfirmedParentLink(actor.getUserId(), studentId)) return;
    throw new ForbiddenOperationException("Không có quyền với học sinh này");
  }

  @Transactional
  public TransitionResult<ConnectionRequestResult> request(CurrentUser actor, UUID needId, UUID tutorId,
      List<String> desiredSlots, String message) {
    requireRole(actor, "STUDENT", "PARENT");
    requireActiveActor(actor);
    LearningNeedView need = profile.findLearningNeed(needId)
        .orElseThrow(() -> EntityNotFoundException.of("LearningNeed", needId));
    lock.lockStudent(need.studentId());
    requireStudentAccess(actor, need.studentId());
    if (profile.findStudentById(need.studentId())
        .filter(student -> need.studentId().equals(student.studentId()) && need.studentUserId().equals(student.userId())).isEmpty())
      throw new InvalidOperationException("Tài khoản sở hữu nhu cầu học không còn hợp lệ");
    if (!need.readyForMatching())
      throw new InvalidOperationException("Nhu cầu học cần đủ thông tin trước khi gửi yêu cầu kết nối");
    input.validate(desiredSlots, message);
    TutorSummaryDto tutor = profile.findTutorById(tutorId)
        .orElseThrow(() -> EntityNotFoundException.of("Tutor", tutorId));
    // Chỉ chặn theo điều kiện tư cách (đã xác minh, có dạy môn); lệch tiêu chí khác vẫn cho gửi kèm cảnh báo
    if (!tutorId.equals(tutor.tutorId()) || !"VERIFIED".equals(tutor.status())
        || iam.findUserSummaryById(tutor.userId()).filter(user -> tutor.userId().equals(user.id())
            && user.role() == AccountRole.TUTOR && user.status() == AccountStatus.ACTIVE).isEmpty())
      throw new InvalidOperationException("Gia sư chưa được xác minh hoặc không còn hoạt động");
    if (NeedFitEvaluator.bestOffering(need, profile.findTutorOfferings(tutorId)).isEmpty())
      throw new InvalidOperationException("Gia sư không dạy môn " + need.subjectName());
    Instant now = clock.instant();
    for (ConnectionRequest r : requests.findByStudentIdAndStatus(need.studentId(), "PENDING")) {
      if (ConnectionTransitions.isDue(r.getExpiresAt(), now)) {
        expiryService.expireRequest(r.getId(), now);
      }
    }
    requests.flush();
    if (requests.existsByStudentIdAndTutorIdAndNeedIdAndStatus(need.studentId(), tutorId, needId, "PENDING"))
      return TransitionResult.conflict("PENDING", "Đã có yêu cầu chờ gia sư này cho nhu cầu học này");
    if (requests.countByStudentIdAndStatus(need.studentId(), "PENDING") >= 5)
      return TransitionResult.conflict("PENDING", "Đã đạt giới hạn 5 yêu cầu chờ");
    if (classes.existsByStudentIdAndTutorIdAndNeedIdAndStatus(need.studentId(), tutorId, needId, "ACTIVE"))
      return TransitionResult.conflict("ACTIVE", "Đã có lớp đang hoạt động cho nhu cầu học này");
    ConnectionRequest created = requests.save(new ConnectionRequest(need.studentId(), tutorId, needId,
        actor.getUserId(), desiredSlots, message, now));
    events.publishEvent(new ConnectionRequestChangedEvent(UUID.randomUUID(), created.getId(),
        actor.getUserId(), need.studentId(), null, "PENDING", now,
        actor.getUserId(), need.studentUserId(), tutor.userId(), null));
    return TransitionResult.success(ConnectionRequestResult.from(created));
  }

  @Transactional
  public TransitionResult<Void> cancelRequest(CurrentUser actor, UUID id) {
    requireRole(actor, "STUDENT", "PARENT");
    ConnectionRequest initial = requests.findById(id).orElseThrow(() -> EntityNotFoundException.of("Request", id));
    lock.lockStudent(initial.getStudentId());
    ConnectionRequest request = requests.lockById(id).orElseThrow(() -> EntityNotFoundException.of("Request", id));
    requireActiveActor(actor);
    requireStudentAccess(actor, request.getStudentId());
    Instant now = clock.instant();
    if (ConnectionTransitions.isDue(request.getExpiresAt(), now)) {
      expiryService.expireRequest(id, now);
      return TransitionResult.conflict("EXPIRED", "Không còn ở trạng thái chờ xử lý");
    }
    if (!"PENDING".equals(request.getStatus()))
      return TransitionResult.conflict(request.getStatus(), "Không còn ở trạng thái chờ xử lý");
    UUID studentUserId = profile.findStudentById(request.getStudentId())
        .map(StudentSummaryDto::userId)
        .orElseThrow(() -> new InvalidOperationException("Không thể xác định tài khoản học sinh"));
    UUID tutorUserId = profile.findTutorById(request.getTutorId())
        .map(TutorSummaryDto::userId)
        .orElseThrow(() -> new InvalidOperationException("Không thể xác định tài khoản gia sư"));
    request.cancel(now);
    events.publishEvent(new ConnectionRequestChangedEvent(UUID.randomUUID(), id, actor.getUserId(),
        request.getStudentId(), "PENDING", "CANCELLED", now, request.getSentByUserId(),
        studentUserId, tutorUserId, null));
    return TransitionResult.success(null);
  }

  @Transactional
  public TransitionResult<ClassResult> respondRequest(CurrentUser actor, UUID id, boolean accept, String reason) {
    requireRole(actor, "TUTOR");
    requireActiveActor(actor);
    ConnectionRequest initial = requests.findById(id).orElseThrow(() -> EntityNotFoundException.of("Request", id));
    UUID tutorId = profile.getTutorIdByUserId(actor.getUserId());
    if (tutorId == null || !initial.getTutorId().equals(tutorId))
      throw new ForbiddenOperationException("Không phải gia sư nhận yêu cầu");
    lock.lockStudent(initial.getStudentId());
    ConnectionRequest request = requests.lockById(id).orElseThrow(() -> EntityNotFoundException.of("Request", id));
    if (!request.getTutorId().equals(tutorId))
      throw new ForbiddenOperationException("Không phải gia sư nhận yêu cầu");
    Instant now = clock.instant();
    if (ConnectionTransitions.isDue(request.getExpiresAt(), now)) {
      expiryService.expireRequest(id, now);
      return TransitionResult.conflict("EXPIRED", "Không còn ở trạng thái chờ xử lý");
    }
    if (!"PENDING".equals(request.getStatus()))
      return TransitionResult.conflict(request.getStatus(), "Không còn ở trạng thái chờ xử lý");

    TutorSummaryDto tutor = profile.findTutorById(tutorId)
        .orElseThrow(() -> new InvalidOperationException("Gia sư không tồn tại"));
    if (!tutorId.equals(tutor.tutorId()) || !"VERIFIED".equals(tutor.status())
        || !actor.getUserId().equals(tutor.userId()))
      throw new InvalidOperationException("Gia sư chưa được xác minh");

    UUID studentUserId = profile.findStudentById(request.getStudentId())
        .map(StudentSummaryDto::userId)
        .orElseThrow(() -> new InvalidOperationException("Không thể xác định tài khoản học sinh"));

    if (!accept) {
      if (reason != null && reason.length() > 300)
        throw new InvalidOperationException("Lý do từ chối không được vượt quá 300 ký tự");
      request.reject(reason, now);
      events.publishEvent(new ConnectionRequestChangedEvent(UUID.randomUUID(), id, actor.getUserId(),
          request.getStudentId(), "PENDING", "REJECTED", now, request.getSentByUserId(),
          studentUserId, actor.getUserId(), null));
      return TransitionResult.success(null);
    }

    if (classes.existsByStudentIdAndTutorIdAndNeedIdAndStatus(request.getStudentId(), tutorId, request.getNeedId(), "ACTIVE"))
      return TransitionResult.conflict("ACTIVE", "Đã có lớp đang hoạt động cho nhu cầu học này");

    LearningNeedView need = profile.findLearningNeed(request.getNeedId())
        .orElseThrow(() -> new InvalidOperationException("Nhu cầu học không còn hợp lệ"));
    if (!request.getStudentId().equals(need.studentId()) || !need.readyForMatching())
      throw new InvalidOperationException("Nhu cầu học đã được lưu trữ hoặc thay đổi");
    // Học phí lớp được chốt theo gói dạy phù hợp nhất tại thời điểm nhận yêu cầu
    TutorOfferingDto offering = NeedFitEvaluator.bestOffering(need, profile.findTutorOfferings(tutorId))
        .orElseThrow(() -> new InvalidOperationException("Bạn không còn gói dạy môn " + need.subjectName()));

    // Gatekeeper Dual Source Conflict Check
    if (request.getDesiredSlots() != null && !request.getDesiredSlots().isEmpty()) {
      // Source 1: Active classes recurring slots
      List<TutoringClass> activeClasses = classes.findByTutorIdAndStatus(tutorId, "ACTIVE");
      if (!activeClasses.isEmpty()) {
        List<UUID> activeReqIds = activeClasses.stream()
            .map(TutoringClass::getConnectionRequestId)
            .filter(java.util.Objects::nonNull)
            .toList();
        List<ConnectionRequest> activeRequests = requests.findAllById(activeReqIds);
        List<String> activeSlots = activeRequests.stream()
            .filter(r -> r.getDesiredSlots() != null)
            .flatMap(r -> r.getDesiredSlots().stream())
            .toList();
        List<String> conflicts = slotConflictDetector.findConflictingSlots(request.getDesiredSlots(), activeSlots);
        if (!conflicts.isEmpty()) {
          throw new ResourceConflictException(ErrorCode.SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE,
              "Khung giờ đề xuất trùng với lịch của lớp học đang hoạt động: " + String.join(", ", conflicts));
        }
      }

      // Source 2: Scheduled sessions via SPI
      if (scheduleConflictChecker.hasConflictWithScheduledSessions(tutorId, request.getDesiredSlots())) {
        throw new ResourceConflictException(ErrorCode.SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE,
            "Khung giờ đề xuất trùng với buổi học đã được lên lịch của gia sư.");
      }
    }

    request.accept(now);
    requests.flush();
    TutoringClass created = classes.save(new TutoringClass(request, offering.pricePerSession(), now));
    events.publishEvent(new ConnectionRequestChangedEvent(UUID.randomUUID(), id, actor.getUserId(),
        request.getStudentId(), "PENDING", "ACCEPTED", now, request.getSentByUserId(),
        studentUserId, actor.getUserId(), created.getId()));
    events.publishEvent(new TutoringClassCreatedEvent(UUID.randomUUID(), created.getId(), actor.getUserId(),
        request.getStudentId(), null, "ACTIVE", now, id, tutorId, request.getNeedId()));
    return TransitionResult.success(ClassResult.from(created));
  }

  @Transactional
  public TransitionResult<TutoringClassResult> endClass(CurrentUser actor, UUID classId, String reason) {
    requireRole(actor, "TUTOR", "STUDENT", "PARENT");
    requireActiveActor(actor);

    if (reason == null || reason.isBlank()) {
      throw new InvalidOperationException("Lý do kết thúc lớp học không được để trống");
    }

    if (reason.length() > 300) {
      throw new InvalidOperationException("Lý do kết thúc lớp học không được vượt quá 300 ký tự");
    }

    TutoringClass tutoringClass = classes.findById(classId)
        .orElseThrow(() -> EntityNotFoundException.of("TutoringClass", classId));

    if (!"ACTIVE".equals(tutoringClass.getStatus())) {
      throw new InvalidOperationException("Chỉ có thể kết thúc lớp học đang hoạt động");
    }

    boolean isTutor = actor.hasRole("TUTOR") && (
        actor.getUserId().equals(tutoringClass.getTutorId())
        || profile.findTutorByUserId(actor.getUserId()).map(t -> t.tutorId().equals(tutoringClass.getTutorId())).orElse(false)
        || profile.findTutorById(tutoringClass.getTutorId()).map(t -> t.userId().equals(actor.getUserId())).orElse(false)
    );
    boolean isStudent = actor.hasRole("STUDENT") && (
        actor.getUserId().equals(tutoringClass.getStudentId())
        || profile.findStudentByUserId(actor.getUserId()).map(s -> s.studentId().equals(tutoringClass.getStudentId())).orElse(false)
        || profile.findStudentById(tutoringClass.getStudentId()).map(s -> s.userId().equals(actor.getUserId())).orElse(false)
    );
    boolean isParent = actor.hasRole("PARENT") && facade.hasConfirmedParentLink(actor.getUserId(), tutoringClass.getStudentId());

    if (!isTutor && !isStudent && !isParent) {
      throw new ForbiddenOperationException("Bạn không có quyền kết thúc lớp học này");
    }

    var scheduleInfo = scheduleStatusProvider.getScheduleInfoBatch(List.of(classId)).get(classId);
    if (scheduleInfo != null && scheduleInfo.scheduledCount() > 0) {
      throw new InvalidOperationException(
          "Không thể kết thúc lớp học khi vẫn còn buổi học đã lên lịch. Vui lòng hoàn thành buổi học hoặc xử lý từng buổi học trước."
      );
    }

    Instant now = clock.instant();
    tutoringClass.endClass(reason, now);
    classes.flush();

    events.publishEvent(new TutoringClassEndedEvent(
        tutoringClass.getId(),
        tutoringClass.getStudentId(),
        tutoringClass.getTutorId(),
        actor.getUserId(),
        reason,
        now
    ));

    return TransitionResult.success(new TutoringClassResult(
        tutoringClass.getId(),
        tutoringClass.getStudentId(),
        tutoringClass.getTutorId(),
        tutoringClass.getNeedId(),
        tutoringClass.getStatus(),
        null,
        null,
        null,
        null,
        "ENDED",
        0,
        tutoringClass.getStartedAt(),
        tutoringClass.getEndedAt()
    ));
  }
}

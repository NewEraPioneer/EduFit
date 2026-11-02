package vn.edufit.connection.application.service;

import java.time.Instant;
import java.time.Clock;
import vn.edufit.connection.application.dto.TransitionResult;
import vn.edufit.connection.application.policy.ConnectionTransitions;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.application.dto.ParentStudentLinkResult;
import vn.edufit.connection.application.port.ConnectionLock;
import vn.edufit.connection.infra.persistence.entity.LinkInvitation;
import vn.edufit.connection.infra.persistence.entity.ParentStudentLink;
import vn.edufit.connection.infra.persistence.repository.LinkInvitationRepository;
import vn.edufit.connection.infra.persistence.repository.ParentStudentLinkRepository;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.iam.api.dto.UserSummaryView;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.shared.auth.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import vn.edufit.connection.api.event.LinkInvitationChangedEvent;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

@Service
public class LinkInvitationService {
  private final Clock clock;
  private final LinkInvitationRepository invitations;
  private final ParentStudentLinkRepository links;
  private final IamFacade iam;
  private final ProfileFacade profile;
  private final ConnectionLock lock;
  private final InvitationQuotaService quotaService;
  private final ApplicationEventPublisher events;
  private final ConnectionExpiryService expiryService;

  @org.springframework.beans.factory.annotation.Autowired
  public LinkInvitationService(LinkInvitationRepository invitations, ParentStudentLinkRepository links,
      IamFacade iam, ProfileFacade profile, ConnectionLock lock, InvitationQuotaService quotaService,
      ApplicationEventPublisher events, ConnectionExpiryService expiryService, Clock clock) {
    this.clock = clock;
    this.invitations = invitations;
    this.links = links;
    this.iam = iam;
    this.profile = profile;
    this.lock = lock;
    this.quotaService = quotaService;
    this.events = events;
    this.expiryService = java.util.Objects.requireNonNull(expiryService);
  }

  private static void requireRole(CurrentUser actor, String... roles) {
    for (String role : roles) if (actor.hasRole(role)) return;
    throw new ForbiddenOperationException("Không có quyền thực hiện thao tác này");
  }

  private void requireActiveActor(CurrentUser actor) {
    AccountRole expected = actor.hasRole("PARENT") ? AccountRole.PARENT : AccountRole.STUDENT;
    iam.findUserSummaryById(actor.getUserId())
        .filter(user -> user.status() == AccountStatus.ACTIVE && user.role() == expected)
        .orElseThrow(() -> new ForbiddenOperationException("Tài khoản không còn quyền thực hiện thao tác"));
  }

  @Transactional(noRollbackFor = {EntityNotFoundException.class, InvalidOperationException.class, ResourceConflictException.class})
  public boolean invite(CurrentUser actor, String targetEmail) {
    requireRole(actor, "PARENT", "STUDENT");
    requireActiveActor(actor);
    var consumption = quotaService.consume(actor.getUserId());
    if (!consumption.allowed()) return false;
    Instant now = consumption.checkedAt();
    AccountRole opposite = actor.hasRole("PARENT") ? AccountRole.STUDENT : AccountRole.PARENT;
    UserSummaryView target = iam.findUserSummaryByEmail(targetEmail)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy tài khoản với email: " + targetEmail));
    if (target.id().equals(actor.getUserId())) {
      throw new InvalidOperationException("Không thể gửi lời mời liên kết cho chính mình");
    }
    if (target.status() != AccountStatus.ACTIVE) {
      throw new InvalidOperationException("Tài khoản đối phương đang bị khóa hoặc chưa kích hoạt");
    }
    if (target.role() != opposite) {
      String expectedRole = (opposite == AccountRole.STUDENT) ? "Học sinh" : "Phụ huynh";
      throw new InvalidOperationException("Tài khoản đối phương không phải vai trò " + expectedRole);
    }
    UUID a = actor.getUserId(), b = target.id();
    lock.lockInvitationPair(a, b);
    List<LinkInvitation> history = invitations.findPendingPair(a, b);
    for (LinkInvitation i : history) {
      if (ConnectionTransitions.isDue(i.getExpiresAt(), now)) {
        expiryService.expireInvitation(i.getId(), now);
      }
    }
    invitations.flush();
    if (invitations.findPendingPair(a, b).stream().anyMatch(i -> "PENDING".equals(i.getStatus()))) {
      throw new ResourceConflictException("Đã có lời mời đang chờ phản hồi giữa hai tài khoản");
    }
    if (invitations.findFirstByInviterUserIdAndInvitedUserIdAndStatusOrderByReinviteAfterDesc(a, b, "DECLINED")
        .filter(i -> ConnectionTransitions.cooldownActive(i.getReinviteAfter(), now)).isPresent()) {
      throw new ResourceConflictException("Lời mời vừa bị từ chối gần đây. Vui lòng thử lại sau thời gian chờ");
    }
    UUID parent = actor.hasRole("PARENT") ? a : b;
    UUID studentUser = actor.hasRole("STUDENT") ? a : b;
    Optional<StudentSummaryDto> student = profile.findStudentByUserId(studentUser);
    if (student.isEmpty()) {
      throw new InvalidOperationException("Hồ sơ học sinh chưa được khởi tạo hoặc không hợp lệ");
    }
    if (links.existsByParentUserIdAndStudentIdAndStatus(parent, student.get().studentId(), "CONFIRMED")) {
      throw new ResourceConflictException("Hai tài khoản đã được liên kết với nhau từ trước");
    }
    LinkInvitation invitation = invitations.save(new LinkInvitation(a, b, now));
    publish(invitation, actor.getUserId(), student.get().studentId(), null, null,
        LinkInvitationChangedEvent.Cause.USER_ACTION, now);
    return true;
  }

  @Transactional
  public TransitionResult<ParentStudentLinkResult> respondInvitation(CurrentUser actor, UUID id, boolean accept) {
    requireRole(actor, "PARENT", "STUDENT");
    LinkInvitation initial = invitations.findById(id).orElseThrow(() -> EntityNotFoundException.of("Invitation", id));
    UUID a = initial.getInviterUserId(), b = initial.getInvitedUserId();
    if (!b.equals(actor.getUserId())) throw new ForbiddenOperationException("Không phải người nhận lời mời");
    UUID studentUser = actor.hasRole("STUDENT") ? b : a;
    Optional<StudentSummaryDto> studentScope = accept ? profile.findStudentByUserId(studentUser) : Optional.empty();
    studentScope.ifPresent(student -> lock.lockStudent(student.studentId()));
    lock.lockInvitationPair(a, b);
    LinkInvitation invitation = invitations.lockById(id).orElseThrow(() -> EntityNotFoundException.of("Invitation", id));
    requireActiveActor(actor);
    Instant now = clock.instant();
    if (ConnectionTransitions.isDue(invitation.getExpiresAt(), now)) {
      expiryService.expireInvitation(id, now);
      return TransitionResult.conflict("EXPIRED", "Không còn ở trạng thái chờ xử lý");
    }
    if (!"PENDING".equals(invitation.getStatus()))
      return TransitionResult.conflict(invitation.getStatus(), "Không còn ở trạng thái chờ xử lý");
    if (!accept) {
      invitation.decline(now);
      publish(invitation, actor.getUserId(), null, "PENDING", null,
          LinkInvitationChangedEvent.Cause.USER_ACTION, now);
      return TransitionResult.success(null);
    }
    UserSummaryView inviter = iam.findUserSummaryById(a)
        .orElseThrow(() -> new InvalidOperationException("Tài khoản gửi không còn hợp lệ"));
    AccountRole expected = actor.hasRole("PARENT") ? AccountRole.STUDENT : AccountRole.PARENT;
    if (inviter.status() != AccountStatus.ACTIVE || inviter.role() != expected)
      throw new InvalidOperationException("Tài khoản gửi không còn hợp lệ");
    UUID parent = actor.hasRole("PARENT") ? b : a;
    StudentSummaryDto student = profile.findStudentByUserId(studentUser)
        .filter(current -> current.userId().equals(studentUser)
            && studentScope.map(before -> before.studentId().equals(current.studentId())).orElse(false))
        .orElseThrow(() -> new InvalidOperationException("Hồ sơ học sinh không còn hợp lệ"));
    if (links.existsByParentUserIdAndStudentIdAndStatus(parent, student.studentId(), "CONFIRMED")) {
      invitation.withdraw(now);
      publish(invitation, null, student.studentId(), "PENDING", null,
          LinkInvitationChangedEvent.Cause.SYSTEM_ALREADY_LINKED, now);
      return TransitionResult.conflict("WITHDRAWN", "Liên kết đã tồn tại; lời mời đã được đóng");
    }
    invitation.accept(now);
    ParentStudentLink link = links.save(new ParentStudentLink(parent, student.studentId(), now));
    publish(invitation, actor.getUserId(), student.studentId(), "PENDING", link.getId(),
        LinkInvitationChangedEvent.Cause.USER_ACTION, now);
    return TransitionResult.success(ParentStudentLinkResult.from(link));
  }

  private void publish(LinkInvitation invitation, UUID actor, UUID student, String oldStatus, UUID linkId,
      LinkInvitationChangedEvent.Cause cause, Instant now) {
    events.publishEvent(new LinkInvitationChangedEvent(UUID.randomUUID(), invitation.getId(), actor, student,
        oldStatus, invitation.getStatus(), now, invitation.getInviterUserId(), invitation.getInvitedUserId(), linkId, cause));
  }

  @Transactional
  public TransitionResult<Void> withdrawInvitation(CurrentUser actor, UUID id) {
    requireRole(actor, "PARENT", "STUDENT");
    LinkInvitation initial = invitations.findById(id)
        .orElseThrow(() -> EntityNotFoundException.of("Invitation", id));
    UUID a = initial.getInviterUserId(), b = initial.getInvitedUserId();
    lock.lockInvitationPair(a, b);
    LinkInvitation invitation = invitations.lockById(id)
        .orElseThrow(() -> EntityNotFoundException.of("Invitation", id));
    if (!invitation.getInviterUserId().equals(actor.getUserId()))
      throw new ForbiddenOperationException("Không phải người gửi lời mời");
    requireActiveActor(actor);
    Instant now = clock.instant();
    if (ConnectionTransitions.isDue(invitation.getExpiresAt(), now)) {
      expiryService.expireInvitation(id, now);
      return TransitionResult.conflict("EXPIRED", "Không còn ở trạng thái chờ xử lý");
    }
    if (!"PENDING".equals(invitation.getStatus()))
      return TransitionResult.conflict(invitation.getStatus(), "Không còn ở trạng thái chờ xử lý");
    invitation.withdraw(now);
    return TransitionResult.success(null);
  }
}

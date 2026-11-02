package vn.edufit.connection.application.service;

import java.time.Instant;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edufit.connection.application.port.ConnectionLock;
import vn.edufit.connection.infra.persistence.entity.ParentStudentLink;
import vn.edufit.connection.infra.persistence.repository.ParentStudentLinkRepository;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.audit.AuditSink;
import vn.edufit.shared.audit.AuditRecord;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.iam.api.AccountStatus;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import vn.edufit.connection.api.event.ParentStudentLinkChangedEvent;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.ResourceConflictException;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.InvalidOperationException;

@Service
public class ParentStudentLinkService {
  private final Clock clock;
  private final ParentStudentLinkRepository links;
  private final ProfileFacade profile;
  private final IamFacade iam;
  private final ConnectionLock lock;
  private final AuditSink audit;
  private final ApplicationEventPublisher events;

  public ParentStudentLinkService(ParentStudentLinkRepository links, ProfileFacade profile,
      ConnectionLock lock, AuditSink audit, ApplicationEventPublisher events, IamFacade iam, Clock clock) {
    this.clock = clock;
    this.links = links;
    this.profile = profile;
    this.iam = iam;
    this.lock = lock;
    this.audit = audit;
    this.events = events;
  }

  private static void requireRole(CurrentUser actor, String... roles) {
    for (String role : roles) if (actor.hasRole(role)) return;
    throw new ForbiddenOperationException("Không có quyền thực hiện thao tác này");
  }

  @Transactional
  public void revokeLink(CurrentUser actor, UUID id, String reason) {
    requireRole(actor, "STUDENT", "ADMIN", "PARENT");
    ParentStudentLink initial = links.findById(id).orElseThrow(() -> EntityNotFoundException.of("Link", id));
    lock.lockStudent(initial.getStudentId());
    ParentStudentLink link = links.lockById(id).orElseThrow(() -> EntityNotFoundException.of("Link", id));
    if (iam.findUserSummaryById(actor.getUserId()).filter(user -> user.status() == AccountStatus.ACTIVE
        && user.id().equals(actor.getUserId()) && actor.hasRole(user.role().name())).isEmpty())
      throw new ForbiddenOperationException("Tài khoản không có quyền thực hiện thao tác này");
    if (!"CONFIRMED".equals(link.getStatus())) throw new ResourceConflictException("Liên kết đã bị thu hồi");
    if (actor.hasRole("ADMIN") && (reason == null || reason.isBlank()))
      throw new InvalidOperationException("Admin phải nhập lý do thu hồi");
    var student = profile.findStudentById(link.getStudentId())
        .filter(value -> value.studentId().equals(link.getStudentId()) && value.userId() != null)
        .orElseThrow(() -> new InvalidOperationException("Không thể xác định tài khoản học sinh"));
    if (!actor.hasRole("ADMIN")) {
      boolean isOwnerParent = actor.hasRole("PARENT") && link.getParentUserId().equals(actor.getUserId());
      boolean isOwnerStudent = actor.hasRole("STUDENT") && student.userId().equals(actor.getUserId());
      if (!isOwnerParent && !isOwnerStudent) {
        if (actor.hasRole("PARENT")) {
          throw new ForbiddenOperationException("Không phải phụ huynh sở hữu liên kết");
        }
        if (actor.hasRole("STUDENT")) {
          throw new ForbiddenOperationException("Không phải học sinh sở hữu liên kết");
        }
        throw new ForbiddenOperationException("Không có quyền thực hiện thao tác này");
      }
    }
    Instant now = clock.instant();
    link.revoke(actor.getUserId(), reason, now);
    audit.record(new AuditRecord(actor.getUserId(), "LINK_REVOKED", "PARENT_STUDENT_LINK", id, reason, now,
        Map.of("parentUserId", link.getParentUserId().toString(), "studentId", link.getStudentId().toString(),
            "studentUserId", student.userId().toString())));
    events.publishEvent(new ParentStudentLinkChangedEvent(UUID.randomUUID(), id, actor.getUserId(),
        link.getStudentId(), "CONFIRMED", "REVOKED", now, link.getParentUserId(), student.userId()));
  }

}

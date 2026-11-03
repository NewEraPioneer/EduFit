package vn.edufit.progress.application.service;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Kiểm tra quyền dùng chung cho các use case theo lớp học của module Progress.
 */
@Component
public class ProgressAccessGuard {

  private final ConnectionFacade connectionFacade;
  private final ProfileFacade profileFacade;

  public ProgressAccessGuard(ConnectionFacade connectionFacade, ProfileFacade profileFacade) {
    this.connectionFacade = connectionFacade;
    this.profileFacade = profileFacade;
  }

  public ConnectionFacade.ClassDetailView requireClass(UUID classId) {
    return connectionFacade.findClassById(classId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy lớp học với ID: " + classId));
  }

  public void requireActiveClass(ConnectionFacade.ClassDetailView cls, String message) {
    if (!"ACTIVE".equals(cls.status())) {
      throw new InvalidOperationException(message);
    }
  }

  public void requireTutorOfClass(CurrentUser actor, ConnectionFacade.ClassDetailView cls) {
    if (!actor.hasRole("TUTOR")) {
      throw new ForbiddenOperationException("Chỉ tài khoản Gia sư mới có quyền thực hiện thao tác này");
    }
    UUID tutorId = profileFacade.getTutorIdByUserId(actor.getUserId());
    if (tutorId == null || !tutorId.equals(cls.tutorId())) {
      throw new ForbiddenOperationException("Chỉ Gia sư được phân công của lớp học mới có quyền thực hiện thao tác này");
    }
  }

  /**
   * @return vai trò người xem của lớp, dùng để lọc phần riêng tư (lời nhắn riêng, cảm nhận của học sinh).
   */
  public ViewerAudience requireMemberOrParentAccess(
      CurrentUser actor, ConnectionFacade.ClassDetailView cls, String deniedMessage) {
    return resolveAudience(actor, cls.tutorId(), cls.studentId())
        .orElseThrow(() -> new ForbiddenOperationException(deniedMessage));
  }

  /** Học sinh của lớp: dùng cho thao tác chỉ học sinh được làm (cảm nhận 1 chạm). */
  public void requireStudentOfClass(CurrentUser actor, ConnectionFacade.ClassDetailView cls) {
    UUID studentId = actor.hasRole("STUDENT") ? profileFacade.getStudentIdByUserId(actor.getUserId()) : null;
    if (studentId == null || !studentId.equals(cls.studentId())) {
      throw new ForbiddenOperationException("Chỉ Học sinh của lớp học mới có quyền thực hiện thao tác này");
    }
  }

  /**
   * Vai trò của người xem đối với một cặp gia sư – học sinh; rỗng nếu không có quyền.
   * Thứ tự ưu tiên: admin, gia sư của lớp, chính học sinh, phụ huynh có liên kết CONFIRMED.
   */
  public Optional<ViewerAudience> resolveAudience(CurrentUser actor, UUID tutorId, UUID studentId) {
    if (actor.hasRole("ADMIN")) return Optional.of(ViewerAudience.ADMIN);

    if (actor.hasRole("TUTOR") && tutorId != null && tutorId.equals(profileFacade.getTutorIdByUserId(actor.getUserId()))) {
      return Optional.of(ViewerAudience.TUTOR);
    }

    if (actor.hasRole("STUDENT") && studentId != null
        && studentId.equals(profileFacade.getStudentIdByUserId(actor.getUserId()))) {
      return Optional.of(ViewerAudience.STUDENT);
    }

    if (actor.hasRole("PARENT") && connectionFacade.hasConfirmedParentLink(actor.getUserId(), studentId)) {
      return Optional.of(ViewerAudience.PARENT);
    }

    return Optional.empty();
  }
}

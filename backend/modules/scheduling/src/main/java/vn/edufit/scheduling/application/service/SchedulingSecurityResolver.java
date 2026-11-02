package vn.edufit.scheduling.application.service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Thành phần giải quyết định danh và phân quyền bảo mật cho phân hệ Scheduling.
 *
 * <p>Giải quyết triệt để sự khác biệt giữa IAM Account ID ({@code account.user_id})
 * và Profile ID ({@code tutor_profile.tutor_id}, {@code student_profile.student_id}),
 * đồng thời kiểm soát quyền truy cập của Phụ huynh qua {@link ConnectionFacade#hasConfirmedParentLink}.
 */
@Slf4j
@Service
public class SchedulingSecurityResolver {

  private final ProfileFacade profileFacade;
  private final ConnectionFacade connectionFacade;

  public SchedulingSecurityResolver(ProfileFacade profileFacade, ConnectionFacade connectionFacade) {
    this.profileFacade = profileFacade;
    this.connectionFacade = connectionFacade;
  }

  /**
   * Phân giải mã hồ sơ gia sư (tutor_id) từ tài khoản đang đăng nhập.
   *
   * @param actor người dùng hiện tại
   * @return UUID của tutor_profile
   */
  public UUID resolveTutorId(CurrentUser actor) {
    validateActor(actor);
    return profileFacade.findTutorByUserId(actor.getUserId())
        .map(TutorSummaryDto::tutorId)
        .orElseThrow(() -> new ForbiddenOperationException("Bạn không có hồ sơ gia sư trong hệ thống"));
  }

  /**
   * Phân giải mã hồ sơ học sinh (student_id) từ tài khoản đang đăng nhập.
   *
   * @param actor người dùng hiện tại
   * @return UUID của student_profile
   */
  public UUID resolveStudentId(CurrentUser actor) {
    validateActor(actor);
    return profileFacade.findStudentByUserId(actor.getUserId())
        .map(StudentSummaryDto::studentId)
        .orElseThrow(() -> new ForbiddenOperationException("Bạn không có hồ sơ học sinh trong hệ thống"));
  }

  /**
   * Kiểm tra quyền truy cập của Học sinh hoặc Phụ huynh có liên kết được xác nhận.
   *
   * @param actor     người dùng hiện tại
   * @param studentId mã hồ sơ học sinh cần kiểm tra
   */
  public void requireStudentOrParentAccess(CurrentUser actor, UUID studentId) {
    validateActor(actor);
    if (studentId == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Mã học sinh không được để trống");
    }

    if (actor.hasRole("ADMIN")) {
      return;
    }

    if (isStudent(actor, studentId)) {
      return;
    }

    if (isParent(actor, studentId)) {
      return;
    }

    throw new ForbiddenOperationException("Bạn không có quyền truy cập thông tin của học sinh này");
  }

  /**
   * Kiểm tra quyền truy cập thông tin chi tiết của buổi học (Gia sư, Học sinh, Phụ huynh hoặc Admin).
   *
   * @param actor   người dùng hiện tại
   * @param session buổi học cần kiểm tra
   */
  public void requireSessionAccess(CurrentUser actor, TutoringSession session) {
    validateActor(actor);
    if (session == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Buổi học không tồn tại");
    }

    if (actor.hasRole("ADMIN")) {
      return;
    }

    if (isTutor(actor, session.getTutorId())) {
      return;
    }

    if (isStudent(actor, session.getStudentId())) {
      return;
    }

    if (isParent(actor, session.getStudentId())) {
      return;
    }

    throw new ForbiddenOperationException("Bạn không có quyền truy cập thông tin buổi học này");
  }

  /**
   * Kiểm tra quyền phản hồi đề xuất buổi học (phải là đối tác khác phía với người đề xuất).
   *
   * @param actor   người dùng hiện tại đang phản hồi
   * @param session buổi học chứa đề xuất
   */
  public void requireCounterpartyResponseAccess(CurrentUser actor, TutoringSession session) {
    requireCounterpartyResponseAccess(actor, session, session.getProposedBy());
  }

  /**
   * Kiểm tra quyền phản hồi đề xuất (dùng cho cả buổi học gốc và đề xuất đổi lịch).
   *
   * @param actor              người dùng hiện tại đang phản hồi
   * @param session            buổi học liên kết
   * @param proposalProposedBy ID tài khoản của người đã tạo đề xuất
   */
  public void requireCounterpartyResponseAccess(CurrentUser actor, TutoringSession session, UUID proposalProposedBy) {
    validateActor(actor);
    if (session == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Buổi học không tồn tại");
    }
    requireCounterpartyResponseAccess(actor, session.getTutorId(), session.getStudentId(), proposalProposedBy);
  }

  /**
   * Kiểm tra quyền phản hồi đề xuất theo tutorId và studentId (dùng cho cả đề xuất đổi lịch định kỳ UC4.8).
   *
   * @param actor              người dùng hiện tại đang phản hồi
   * @param tutorId            mã hồ sơ gia sư
   * @param studentId          mã hồ sơ học sinh
   * @param proposalProposedBy ID tài khoản của người đã tạo đề xuất
   */
  public void requireCounterpartyResponseAccess(
      CurrentUser actor,
      UUID tutorId,
      UUID studentId,
      UUID proposalProposedBy
  ) {
    validateActor(actor);
    if (tutorId == null || studentId == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Thông tin lớp học không hợp lệ");
    }

    if (Objects.equals(actor.getUserId(), proposalProposedBy)) {
      throw new ForbiddenOperationException("Bạn không thể tự phản hồi đề xuất do chính mình tạo ra");
    }

    boolean isTutor = isTutor(actor, tutorId);
    boolean isStudent = isStudent(actor, studentId);
    boolean isParent = isParent(actor, studentId);

    if (!isTutor && !isStudent && !isParent) {
      throw new ForbiddenOperationException("Bạn không phải thành viên của buổi học này");
    }

    // Xác định người đề xuất thuộc phe Gia sư hay phe Học sinh/Phụ huynh
    boolean proposerIsTutor = profileFacade.findTutorByUserId(proposalProposedBy)
        .map(t -> Objects.equals(t.tutorId(), tutorId))
        .orElse(false);

    if (proposerIsTutor) {
      // Đề xuất do Gia sư tạo -> bên phản hồi phải là Học sinh hoặc Phụ huynh
      if (!isStudent && !isParent) {
        throw new ForbiddenOperationException("Chỉ Học sinh hoặc Phụ huynh mới có quyền phản hồi đề xuất từ Gia sư");
      }
    } else {
      // Đề xuất do Học sinh hoặc Phụ huynh tạo -> bên phản hồi phải là Gia sư
      if (!isTutor) {
        throw new ForbiddenOperationException("Chỉ Gia sư của buổi học mới có quyền phản hồi đề xuất này");
      }
    }
  }

  /**
   * Kiểm tra quyền đề xuất buổi học hoặc đổi lịch (Gia sư, Học sinh hoặc Phụ huynh có liên kết).
   *
   * @param actor     người dùng hiện tại
   * @param tutorId   mã hồ sơ gia sư của lớp/buổi học
   * @param studentId mã hồ sơ học sinh của lớp/buổi học
   */
  public void requireProposeAccess(CurrentUser actor, UUID tutorId, UUID studentId) {
    validateActor(actor);
    if (actor.hasRole("ADMIN")) {
      return;
    }

    if (isTutor(actor, tutorId) || isStudent(actor, studentId) || isParent(actor, studentId)) {
      return;
    }

    throw new ForbiddenOperationException("Chỉ Gia sư hoặc Học sinh trong lớp học mới có quyền đề xuất lịch học");
  }

  /**
   * Kiểm tra quyền hủy buổi học (Gia sư, Học sinh hoặc Phụ huynh có liên kết).
   *
   * @param actor   người dùng hiện tại
   * @param session buổi học cần hủy
   */
  public void requireCancelAccess(CurrentUser actor, TutoringSession session) {
    validateActor(actor);
    if (session == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Buổi học không tồn tại");
    }

    if (actor.hasRole("ADMIN")) {
      return;
    }

    if (isTutor(actor, session.getTutorId())
        || isStudent(actor, session.getStudentId())
        || isParent(actor, session.getStudentId())) {
      return;
    }

    throw new ForbiddenOperationException("Bạn không có quyền hủy buổi học này");
  }

  /**
   * Kiểm tra quyền duy nhất của Gia sư (dùng cho ghi nhận kết quả Outcome).
   *
   * @param actor   người dùng hiện tại
   * @param tutorId mã gia sư của buổi học
   */
  public void requireTutorAccess(CurrentUser actor, UUID tutorId) {
    validateActor(actor);
    if (actor.hasRole("ADMIN")) {
      return;
    }

    if (!isTutor(actor, tutorId)) {
      throw new ForbiddenOperationException("Chỉ Gia sư của buổi học mới có quyền ghi nhận kết quả");
    }
  }

  /**
   * Phân giải Profile ID (tutor_id hoặc student_id) để tra cứu Calendar.
   *
   * @param actor              người dùng hiện tại
   * @param requestedStudentId mã học sinh do phụ huynh truyền lên (nếu có)
   * @return Profile UUID để tìm kiếm trong CSDL
   */
  public UUID resolveCalendarProfileId(CurrentUser actor, UUID requestedStudentId) {
    validateActor(actor);

    if (requestedStudentId != null) {
      requireStudentOrParentAccess(actor, requestedStudentId);
      return requestedStudentId;
    }

    if (actor.hasRole("STUDENT")) {
      return resolveStudentId(actor);
    }

    if (actor.hasRole("TUTOR")) {
      return resolveTutorId(actor);
    }

    // Trường hợp vai trò không chỉ định rõ ràng qua hasRole, thử tra cứu Profile
    Optional<StudentSummaryDto> studentOpt = profileFacade.findStudentByUserId(actor.getUserId());
    if (studentOpt.isPresent()) {
      return studentOpt.get().studentId();
    }

    Optional<TutorSummaryDto> tutorOpt = profileFacade.findTutorByUserId(actor.getUserId());
    if (tutorOpt.isPresent()) {
      return tutorOpt.get().tutorId();
    }

    if (actor.hasRole("PARENT")) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Phụ huynh cần cung cấp mã học sinh (studentId) để tra cứu lịch học"
      );
    }

    throw new ForbiddenOperationException("Bạn không có quyền truy cập lịch học");
  }

  public boolean isTutor(CurrentUser actor, UUID tutorId) {
    if (actor == null || actor.getUserId() == null || tutorId == null) {
      return false;
    }
    return profileFacade.findTutorByUserId(actor.getUserId())
        .map(t -> Objects.equals(t.tutorId(), tutorId))
        .orElse(false);
  }

  public boolean isStudent(CurrentUser actor, UUID studentId) {
    if (actor == null || actor.getUserId() == null || studentId == null) {
      return false;
    }
    return profileFacade.findStudentByUserId(actor.getUserId())
        .map(s -> Objects.equals(s.studentId(), studentId))
        .orElse(false);
  }

  public boolean isParent(CurrentUser actor, UUID studentId) {
    if (actor == null || actor.getUserId() == null || studentId == null) {
      return false;
    }
    return connectionFacade.hasConfirmedParentLink(actor.getUserId(), studentId);
  }

  private void validateActor(CurrentUser actor) {
    if (actor == null || actor.getUserId() == null) {
      throw new InvalidOperationException(ErrorCode.UNAUTHORIZED, "Phiên làm việc chưa được xác thực hoặc đã hết hạn");
    }
  }
}

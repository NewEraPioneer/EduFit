package vn.edufit.scheduling.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.scheduling.api.event.SessionProposedEvent;
import vn.edufit.scheduling.application.port.ConnectionValidationPort;
import vn.edufit.scheduling.domain.model.SessionHistory;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.policy.ConflictPolicy;
import vn.edufit.scheduling.domain.policy.ProposalWindowPolicy;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Service xử lý Use Case UC4.1: Đề xuất lịch học mới (Propose Session).
 *
 * <p>Quy tắc nghiệp vụ bảo đảm:
 * <ul>
 *   <li><b>BR-41:</b> Thời lượng buổi học từ 30 đến 240 phút; thời điểm bắt đầu phải ở tương lai.</li>
 *   <li><b>BR-42 / NFR-17:</b> Kiểm tra xung đột lịch dạy/học trước khi ghi và bẫy lỗi Exclusion Constraint ở tầng DB.</li>
 *   <li><b>BR-43:</b> Tự động tính hạn chót phản hồi (TTL: 48h hoặc trước giờ bắt đầu 2h).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProposeSessionService {

  private final TutoringSessionDomainRepository sessionRepository;
  private final SessionHistoryDomainRepository historyRepository;
  private final ConnectionValidationPort connectionValidationPort;
  private final SchedulingSecurityResolver schedulingSecurityResolver;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional
  public TutoringSession proposeSession(
      UUID classId,
      UUID tutorId,
      UUID studentId,
      Instant startAt,
      Instant endAt,
      SessionMode mode,
      String placeOrLink,
      String repeatNote,
      String message,
      CurrentUser actor
  ) {
    Instant now = clock.instant();

    if (classId == null) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Mã lớp học (classId) không được để trống"
      );
    }

    // Tự động phân giải tutorId và studentId trực tiếp từ classId (thông qua ConnectionValidationPort)
    UUID resolvedTutorId = tutorId;
    UUID resolvedStudentId = studentId;

    var activeClassOpt = connectionValidationPort.findActiveClassById(classId);
    if (activeClassOpt.isPresent()) {
      var activeClass = activeClassOpt.get();
      resolvedTutorId = activeClass.tutorId();
      resolvedStudentId = activeClass.studentId();
    } else if (resolvedTutorId == null || resolvedStudentId == null) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Lớp học không ở trạng thái hoạt động (ACTIVE) hoặc không tồn tại"
      );
    }

    // 1. Kiểm tra quyền của người đề xuất qua SchedulingSecurityResolver (Gia sư, Học sinh hoặc Phụ huynh hợp lệ)
    schedulingSecurityResolver.requireProposeAccess(actor, resolvedTutorId, resolvedStudentId);
    UUID proposedBy = actor.getUserId();

    // 2. Xác thực lớp học / kết nối giữa 2 bên qua Port
    if (!connectionValidationPort.isConnectionActive(classId, resolvedTutorId, resolvedStudentId)) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Lớp học không ở trạng thái hoạt động (ACTIVE) hoặc không tồn tại kết nối giữa hai bên"
      );
    }

    // 3. Kiểm tra tính hợp lệ của thời lượng & thời điểm bắt đầu theo BR-41
    if (startAt.isBefore(now)) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Thời điểm bắt đầu buổi học phải ở tương lai"
      );
    }
    ProposalWindowPolicy.validateDuration(startAt, endAt);

    // 4. Tính toán thời hạn phản hồi (TTL) theo BR-43
    Instant expiresAt = ProposalWindowPolicy.calculateExpiresAt(startAt, now);

    // 5. Kiểm tra sơ bộ xung đột lịch dạy của Gia sư (In-Memory Check qua ConflictPolicy)
    List<TutoringSession> tutorBusySlots = sessionRepository.findTutorBusySlots(resolvedTutorId, startAt, endAt);
    boolean tutorHasConflict = tutorBusySlots.stream().anyMatch(existing ->
        ConflictPolicy.isOverlapping(startAt, endAt, existing.getStartAt(), existing.getEndAt())
    );
    if (tutorHasConflict) {
      throw new ResourceConflictException(
          ErrorCode.SCHEDULE_OVERLAP,
          "Gia sư đã có lịch dạy bị trùng trong khung giờ này!"
      );
    }

    // 6. Kiểm tra sơ bộ xung đột lịch học của Học sinh
    List<TutoringSession> studentSessions = sessionRepository.findCalendarSessions(resolvedStudentId, startAt, endAt);
    boolean studentHasConflict = studentSessions.stream().anyMatch(existing ->
        ConflictPolicy.isOverlapping(startAt, endAt, existing.getStartAt(), existing.getEndAt())
    );
    if (studentHasConflict) {
      throw new ResourceConflictException(
          ErrorCode.SCHEDULE_OVERLAP,
          "Học sinh đã có lịch học bị trùng trong khung giờ này!"
      );
    }

    // 7. Tạo Aggregate Root POJO
    TutoringSession newSession = TutoringSession.propose(
        UUID.randomUUID(),
        classId,
        resolvedTutorId,
        resolvedStudentId,
        startAt,
        endAt,
        mode,
        placeOrLink,
        repeatNote,
        message,
        proposedBy,
        expiresAt,
        now
    );

    // 8. Lưu xuống DB với cơ chế bẫy lỗi GiST Exclusion Constraint (Tầng 2 Defense)
    TutoringSession savedSession;
    try {
      savedSession = sessionRepository.save(newSession);
    } catch (DataIntegrityViolationException ex) {
      log.warn("Bẫy lỗi GiST Exclusion Constraint khi tạo đề xuất lịch: {}", ex.getMessage());
      throw new ResourceConflictException(
          ErrorCode.SCHEDULE_OVERLAP,
          "Khung giờ đề xuất đã bị trùng với một buổi học khác trong cơ sở dữ liệu!"
      );
    }

    // 9. Ghi vết lịch sử biến động (Audit Trail)
    SessionHistory history = SessionHistory.create(
        savedSession.getSessionId(),
        null,
        "PROPOSE",
        proposedBy,
        message,
        now
    );
    historyRepository.save(history);

    // 10. Phát tán Domain Event cho hệ thống thông báo (Notification)
    eventPublisher.publishEvent(SessionProposedEvent.of(
        savedSession.getSessionId(),
        savedSession.getClassId(),
        savedSession.getTutorId(),
        savedSession.getStudentId(),
        savedSession.getProposedBy(),
        savedSession.getStartAt(),
        savedSession.getEndAt(),
        savedSession.getExpiresAt()
    ));

    log.info(
        "Đề xuất buổi học thành công: sessionId={}, classId={}, startAt={}, expiresAt={}",
        savedSession.getSessionId(),
        savedSession.getClassId(),
        savedSession.getStartAt(),
        savedSession.getExpiresAt()
    );

    return savedSession;
  }

  /**
   * Phương thức tương thích ngược chấp nhận UUID của người đề xuất.
   */
  @Transactional
  public TutoringSession proposeSession(
      UUID classId,
      UUID tutorId,
      UUID studentId,
      Instant startAt,
      Instant endAt,
      SessionMode mode,
      String placeOrLink,
      String repeatNote,
      String message,
      UUID proposedBy
  ) {
    return proposeSession(
        classId,
        tutorId,
        studentId,
        startAt,
        endAt,
        mode,
        placeOrLink,
        repeatNote,
        message,
        SimpleCurrentUser.of(proposedBy)
    );
  }
}

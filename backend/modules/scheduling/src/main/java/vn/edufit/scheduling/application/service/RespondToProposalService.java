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
import vn.edufit.scheduling.api.event.SessionScheduledEvent;
import vn.edufit.scheduling.domain.model.SessionHistory;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.policy.ConflictPolicy;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Service xử lý Use Case UC4.3: Phản hồi đề xuất buổi học (Chấp nhận hoặc Từ chối).
 *
 * <p>Đặc tính kỹ thuật trọng yếu:
 * <ul>
 *   <li><b>Pessimistic Row Locking (Tầng 1):</b> Sử dụng {@code findByIdForUpdate} để khóa hàng độc quyền,
 *       ngăn chặn mọi hành động chỉnh sửa đồng thời trong lúc giao dịch đang diễn ra.</li>
 *   <li><b>PostgreSQL GiST Exclusion Trap (Tầng 2):</b> Bắt {@link DataIntegrityViolationException}
 *       và chuyển thể thành mã lỗi chuẩn {@link ErrorCode#SCHEDULE_OVERLAP} (HTTP 409).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RespondToProposalService {

  private final TutoringSessionDomainRepository sessionRepository;
  private final SessionHistoryDomainRepository historyRepository;
  private final SchedulingSecurityResolver schedulingSecurityResolver;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional
  public TutoringSession respondToProposal(
      UUID sessionId,
      CurrentUser actor,
      boolean accept,
      String reason
  ) {
    Instant now = clock.instant();

    // 1. TẦNG 1 DEFENSE: Lấy bản ghi với Khóa bi quan (SELECT ... FOR UPDATE)
    TutoringSession session = sessionRepository.findByIdForUpdate(sessionId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy buổi học với ID: " + sessionId));

    // 2. Kiểm tra quyền của bên phản hồi qua SchedulingSecurityResolver (phải là bên đối tác)
    schedulingSecurityResolver.requireCounterpartyResponseAccess(actor, session);
    UUID responderId = actor.getUserId();

    // 3. Xử lý hành động từ chối (REJECT)
    if (!accept) {
      session.reject(responderId, reason, now);
      TutoringSession rejectedSession = sessionRepository.save(session);

      historyRepository.save(SessionHistory.create(
          sessionId,
          null,
          "REJECT",
          responderId,
          reason,
          now
      ));

      log.info("Đã từ chối đề xuất buổi học: sessionId={}, rejectedBy={}", sessionId, responderId);
      return rejectedSession;
    }

    // 4. Xử lý hành động chấp thuận (ACCEPT): Kiểm tra trùng lặp trên bộ nhớ RAM (In-Memory)
    List<TutoringSession> tutorBusySlots = sessionRepository.findTutorBusySlots(
        session.getTutorId(),
        session.getStartAt(),
        session.getEndAt()
    );
    boolean hasConflict = tutorBusySlots.stream()
        .filter(s -> !s.getSessionId().equals(sessionId))
        .anyMatch(existing -> ConflictPolicy.isOverlapping(
            session.getStartAt(),
            session.getEndAt(),
            existing.getStartAt(),
            existing.getEndAt()
        ));

    if (hasConflict) {
      throw new ResourceConflictException(
          ErrorCode.SCHEDULE_OVERLAP,
          "Gia sư đã có lịch dạy khác trong khoảng thời gian này!"
      );
    }

    // Chuyển trạng thái Aggregate Root sang SCHEDULED
    session.accept(responderId, now);

    // 5. TẦNG 2 DEFENSE: Lưu xuống cơ sở dữ liệu và bẫy lỗi Exclusion Constraint
    TutoringSession scheduledSession;
    try {
      scheduledSession = sessionRepository.save(session);
    } catch (DataIntegrityViolationException ex) {
      log.warn("Bẫy lỗi GiST Exclusion Constraint khi xác nhận buổi học: {}", ex.getMessage());
      throw new ResourceConflictException(
          ErrorCode.SCHEDULE_OVERLAP,
          "Buổi học đã bị trùng lịch với một ca dạy khác vừa được xác nhận!"
      );
    }

    // 6. Ghi Audit Trail
    historyRepository.save(SessionHistory.create(
        sessionId,
        null,
        "ACCEPT",
        responderId,
        null,
        now
    ));

    // 7. Bắn Domain Event thông báo chốt lịch thành công
    eventPublisher.publishEvent(SessionScheduledEvent.of(
        scheduledSession.getSessionId(),
        scheduledSession.getClassId(),
        scheduledSession.getTutorId(),
        scheduledSession.getStudentId(),
        scheduledSession.getStartAt(),
        scheduledSession.getEndAt()
    ));

    log.info(
        "Chốt lịch học thành công (SCHEDULED): sessionId={}, tutorId={}, studentId={}",
        scheduledSession.getSessionId(),
        scheduledSession.getTutorId(),
        scheduledSession.getStudentId()
    );

    return scheduledSession;
  }

  /**
   * Phương thức tương thích ngược chấp nhận UUID của người phản hồi.
   */
  @Transactional
  public TutoringSession respondToProposal(
      UUID sessionId,
      UUID responderId,
      boolean accept,
      String reason
  ) {
    return respondToProposal(sessionId, SimpleCurrentUser.of(responderId), accept, reason);
  }
}

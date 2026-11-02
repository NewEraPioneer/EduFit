package vn.edufit.scheduling.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.scheduling.api.event.SessionCancelledEvent;
import vn.edufit.scheduling.domain.model.CancelReason;
import vn.edufit.scheduling.domain.model.SessionHistory;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.policy.LateCancelPolicy;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;

/**
 * Service xử lý Use Case UC4.5: Hủy buổi học đã lên lịch (Cancel Session).
 *
 * <p>Quy tắc nghiệp vụ:
 * <ul>
 *   <li><b>BR-45:</b> Phải cung cấp lý do hủy từ danh mục chuẩn {@link CancelReason}.</li>
 *   <li><b>BR-46:</b> Kiểm tra tự động quy tắc Hủy trễ (dưới 12 giờ trước thời điểm bắt đầu buổi học).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelSessionService {

  private final TutoringSessionDomainRepository sessionRepository;
  private final SessionHistoryDomainRepository historyRepository;
  private final SchedulingSecurityResolver schedulingSecurityResolver;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional
  public TutoringSession cancelSession(
      UUID sessionId,
      CurrentUser actor,
      CancelReason reason,
      String comment
  ) {
    Instant now = clock.instant();

    // 1. Lấy buổi học với Khóa bi quan
    TutoringSession session = sessionRepository.findByIdForUpdate(sessionId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy buổi học với ID: " + sessionId));

    // 2. Xác thực quyền qua SchedulingSecurityResolver (Gia sư, Học sinh hoặc Phụ huynh hợp lệ)
    schedulingSecurityResolver.requireCancelAccess(actor, session);
    UUID actorId = actor.getUserId();

    // 3. Đánh giá quy tắc Hủy trễ theo BR-46 (< 12 giờ)
    boolean isLate = LateCancelPolicy.isLateCancel(session.getStartAt(), now);

    // 4. Cập nhật Aggregate Root (actorId là user_id tuân thủ ràng buộc FK CSDL)
    session.cancel(actorId, reason, comment, isLate, now);
    TutoringSession cancelledSession = sessionRepository.save(session);

    // 5. Ghi Audit Trail
    historyRepository.save(SessionHistory.create(
        sessionId,
        null,
        "CANCEL",
        actorId,
        (reason != null ? reason.name() : "") + ": " + comment,
        now
    ));

    // 6. Bắn Domain Event
    eventPublisher.publishEvent(SessionCancelledEvent.of(
        cancelledSession.getSessionId(),
        cancelledSession.getClassId(),
        cancelledSession.getTutorId(),
        cancelledSession.getStudentId(),
        actorId,
        reason != null ? reason.name() : null,
        comment,
        isLate
    ));

    log.info(
        "Hủy buổi học thành công: sessionId={}, cancelledBy={}, isLate={}",
        sessionId,
        actorId,
        isLate
    );

    return cancelledSession;
  }

  /**
   * Phương thức tương thích ngược chấp nhận UUID của người thực hiện hủy.
   */
  @Transactional
  public TutoringSession cancelSession(
      UUID sessionId,
      UUID actorId,
      CancelReason reason,
      String comment
  ) {
    return cancelSession(sessionId, SimpleCurrentUser.of(actorId), reason, comment);
  }
}

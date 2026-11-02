package vn.edufit.scheduling.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.scheduling.api.event.SessionOutcomeRecordedEvent;
import vn.edufit.scheduling.application.port.ProgressSyncPort;
import vn.edufit.scheduling.domain.model.SessionHistory;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Service xử lý Use Case UC4.6: Ghi nhận kết quả buổi học và biên bản (Record Outcome).
 *
 * <p>Quy tắc nghiệp vụ:
 * <ul>
 *   <li>Chỉ duy nhất Gia sư của buổi học mới có quyền ghi nhận kết quả.</li>
 *   <li>Kết quả hợp lệ chỉ bao gồm {@link SessionStatus#COMPLETED} hoặc {@link SessionStatus#ABSENT}.</li>
 *   <li>Tự động đồng bộ biên bản buổi học (Session Note) sang module {@code progress} qua Outbound Port.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordSessionOutcomeService {

  private final TutoringSessionDomainRepository sessionRepository;
  private final SessionHistoryDomainRepository historyRepository;
  private final SchedulingSecurityResolver schedulingSecurityResolver;
  private final ProgressSyncPort progressSyncPort;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional
  public TutoringSession recordOutcome(
      UUID sessionId,
      CurrentUser actor,
      SessionStatus outcome,
      String noteContent
  ) {
    Instant now = clock.instant();

    // 1. Lấy buổi học với Khóa bi quan
    TutoringSession session = sessionRepository.findByIdForUpdate(sessionId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy buổi học với ID: " + sessionId));

    // 2. Kiểm tra quyền của Gia sư qua SchedulingSecurityResolver
    schedulingSecurityResolver.requireTutorAccess(actor, session.getTutorId());
    UUID outcomeRecordedBy = actor.getUserId();

    // 3. Kiểm tra tính hợp lệ của outcome
    if (outcome != SessionStatus.COMPLETED && outcome != SessionStatus.ABSENT) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Kết quả ghi nhận chỉ có thể là COMPLETED hoặc ABSENT"
      );
    }

    // 4. Kiểm tra thời điểm: Buổi học phải đã bắt đầu hoặc kết thúc
    if (now.isBefore(session.getStartAt())) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Chưa thể ghi nhận kết quả khi buổi học chưa diễn ra!"
      );
    }

    // 5. Cập nhật Aggregate Root (lưu user_id tuân thủ khóa ngoại CSDL)
    session.recordOutcome(outcome, outcomeRecordedBy, now);
    TutoringSession updatedSession = sessionRepository.save(session);

    // 6. Đồng bộ biên bản học tập (SessionNote) sang module progress nếu có nội dung
    if (noteContent != null && !noteContent.isBlank()) {
      progressSyncPort.recordSessionNote(sessionId, session.getClassId(), noteContent.trim(), now);
    }

    // 7. Ghi Audit Trail
    historyRepository.save(SessionHistory.create(
        sessionId,
        null,
        "OUTCOME_" + outcome.name(),
        outcomeRecordedBy,
        noteContent,
        now
    ));

    // 8. Bắn Domain Event
    eventPublisher.publishEvent(SessionOutcomeRecordedEvent.of(
        updatedSession.getSessionId(),
        updatedSession.getClassId(),
        updatedSession.getTutorId(),
        updatedSession.getStudentId(),
        outcome.name(),
        noteContent,
        now
    ));

    log.info(
        "Ghi nhận kết quả buổi học thành công: sessionId={}, outcome={}, recordedBy={}",
        sessionId,
        outcome,
        outcomeRecordedBy
    );

    return updatedSession;
  }

  /**
   * Phương thức tương thích ngược chấp nhận UUID của người ghi nhận kết quả.
   */
  @Transactional
  public TutoringSession recordOutcome(
      UUID sessionId,
      UUID tutorId,
      SessionStatus outcome,
      String noteContent
  ) {
    return recordOutcome(sessionId, SimpleCurrentUser.of(tutorId), outcome, noteContent);
  }
}

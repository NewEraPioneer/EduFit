package vn.edufit.scheduling.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Service xử lý Use Case phản hồi hàng loạt đề xuất buổi học (Batch Respond Sessions).
 *
 * <p>Quy tắc kỹ thuật & Đảm bảo tính toàn vẹn:
 * <ul>
 *   <li><b>Pessimistic Locking & Deadlock Avoidance:</b> Sắp xếp danh sách sessionId theo thứ tự cố định
 *       và khóa bi quan từng dòng bằng {@code findByIdForUpdate} trước khi thực thi.</li>
 *   <li><b>All-or-Nothing:</b> Nếu có bất kỳ buổi học nào xung đột hoặc vi phạm điều kiện,
 *       toàn bộ giao dịch sẽ rollback ngay lập tức.</li>
 *   <li><b>PostgreSQL GiST Exclusion Trap:</b> Bắt lỗi vi phạm ràng buộc loại trừ ở mức cơ sở dữ liệu.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchRespondSessionService {

  private final TutoringSessionDomainRepository sessionRepository;
  private final SessionHistoryDomainRepository historyRepository;
  private final SchedulingSecurityResolver schedulingSecurityResolver;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;


  @Transactional
  public List<TutoringSession> respondBatch(
      List<UUID> sessionIds,
      boolean accept,
      String reason,
      CurrentUser actor
  ) {
    Instant now = clock.instant();

    if (sessionIds == null || sessionIds.isEmpty()) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Danh sách mã buổi học (sessionIds) không được để trống"
      );
    }

    // 1. Sắp xếp danh sách sessionId để tránh Deadlock khi nhiều luồng cùng khóa nhiều dòng
    List<UUID> sortedIds = sessionIds.stream().distinct().sorted().toList();
    UUID responderId = actor.getUserId();

    // 2. TẦNG 1 DEFENSE: Lấy và khóa bi quan (SELECT ... FOR UPDATE) toàn bộ các buổi học trong lô
    List<TutoringSession> lockedSessions = new ArrayList<>(sortedIds.size());
    for (UUID id : sortedIds) {
      TutoringSession session = sessionRepository.findByIdForUpdate(id)
          .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy buổi học với ID: " + id));

      // Kiểm tra quyền phản hồi của đối tác cho từng buổi học
      schedulingSecurityResolver.requireCounterpartyResponseAccess(actor, session);
      lockedSessions.add(session);
    }

    // 2.1. PRE-VALIDATION PHASE: Kiểm định trạng thái toàn bộ lô trước khi thực hiện bất kỳ biến đổi trạng thái nào
    for (TutoringSession session : lockedSessions) {
      if (session.getStatus().isTerminal()) {
        throw new InvalidOperationException(
            ErrorCode.SESSION_ALREADY_FINALIZED,
            "Tất cả các buổi học trong lô phải ở trạng thái PROPOSED. Buổi "
                + session.getSessionId() + " đã kết thúc ở trạng thái: " + session.getStatus()
        );
      }
      if (session.getStatus() != SessionStatus.PROPOSED) {
        throw new InvalidOperationException(
            ErrorCode.INVALID_OPERATION,
            "Tất cả các buổi học trong lô phải ở trạng thái PROPOSED. Buổi "
                + session.getSessionId() + " đang ở trạng thái: " + session.getStatus()
        );
      }
      if (accept && session.getExpiresAt().isBefore(now)) {
        throw new InvalidOperationException(
            ErrorCode.PROPOSAL_EXPIRED,
            "Đề xuất buổi học " + session.getSessionId() + " đã hết hạn phản hồi"
        );
      }
    }

    // 3. Xử lý hành động TỪ CHỐI (REJECT) hàng loạt
    if (!accept) {
      List<TutoringSession> rejectedSessions = new ArrayList<>(lockedSessions.size());
      for (TutoringSession session : lockedSessions) {
        session.reject(responderId, reason, now);
        TutoringSession saved = sessionRepository.save(session);
        rejectedSessions.add(saved);

        historyRepository.save(SessionHistory.create(
            saved.getSessionId(),
            null,
            "REJECT",
            responderId,
            reason,
            now
        ));
      }
      log.info("Từ chối hàng loạt đề xuất buổi học thành công: count={}", rejectedSessions.size());
      return rejectedSessions;
    }

    // 4. Xử lý hành động CHẤP THUẬN (ACCEPT) hàng loạt
    Set<UUID> batchSessionIds = new HashSet<>(sortedIds);

    // 4a. Kiểm tra xung đột chéo giữa các buổi học trong cùng lô
    for (int i = 0; i < lockedSessions.size(); i++) {
      for (int j = i + 1; j < lockedSessions.size(); j++) {
        TutoringSession s1 = lockedSessions.get(i);
        TutoringSession s2 = lockedSessions.get(j);

        boolean sameTutor = s1.getTutorId().equals(s2.getTutorId());
        boolean sameStudent = s1.getStudentId().equals(s2.getStudentId());

        if ((sameTutor || sameStudent)
            && ConflictPolicy.isOverlapping(s1.getStartAt(), s1.getEndAt(), s2.getStartAt(), s2.getEndAt())) {
          throw new ResourceConflictException(
              ErrorCode.SCHEDULE_OVERLAP,
              "Các buổi học trong danh sách chấp thuận bị trùng giờ với nhau!"
          );
        }
      }
    }

    // 4b. Kiểm tra xung đột với các ca dạy / lịch học SCHEDULED khác ngoài lô trong cơ sở dữ liệu
    for (TutoringSession session : lockedSessions) {
      // 4b.1. Kiểm tra lịch dạy bận của Gia sư
      List<TutoringSession> tutorBusySlots = sessionRepository.findTutorBusySlots(
          session.getTutorId(),
          session.getStartAt(),
          session.getEndAt()
      );

      boolean tutorHasConflict = tutorBusySlots.stream()
          .filter(busy -> !batchSessionIds.contains(busy.getSessionId()))
          .anyMatch(busy -> ConflictPolicy.isOverlapping(
              session.getStartAt(),
              session.getEndAt(),
              busy.getStartAt(),
              busy.getEndAt()
          ));

      if (tutorHasConflict) {
        throw new ResourceConflictException(
            ErrorCode.SCHEDULE_OVERLAP,
            "Gia sư đã có lịch dạy khác trong khoảng thời gian: " + session.getStartAt() + " -> " + session.getEndAt()
        );
      }

      // 4b.2. Kiểm tra lịch học bận của Học sinh
      List<TutoringSession> studentSessions = sessionRepository.findCalendarSessions(
          session.getStudentId(),
          session.getStartAt(),
          session.getEndAt()
      );

      boolean studentHasConflict = studentSessions.stream()
          .filter(busy -> !batchSessionIds.contains(busy.getSessionId()))
          .filter(busy -> busy.getStatus() == SessionStatus.SCHEDULED)
          .anyMatch(busy -> ConflictPolicy.isOverlapping(
              session.getStartAt(),
              session.getEndAt(),
              busy.getStartAt(),
              busy.getEndAt()
          ));

      if (studentHasConflict) {
        throw new ResourceConflictException(
            ErrorCode.SCHEDULE_OVERLAP,
            "Học sinh đã có lịch học khác trong khoảng thời gian: " + session.getStartAt() + " -> " + session.getEndAt()
        );
      }
    }

    // 4c. Chuyển trạng thái sang SCHEDULED và lưu với cơ chế bẫy lỗi Exclusion Constraint
    List<TutoringSession> scheduledSessions = new ArrayList<>(lockedSessions.size());
    List<SessionScheduledEvent> eventsToPublish = new ArrayList<>(lockedSessions.size());
    try {
      for (TutoringSession session : lockedSessions) {
        session.accept(responderId, now);
        TutoringSession saved = sessionRepository.save(session);
        scheduledSessions.add(saved);

        historyRepository.save(SessionHistory.create(
            saved.getSessionId(),
            null,
            "ACCEPT",
            responderId,
            null,
            now
        ));

        eventsToPublish.add(SessionScheduledEvent.of(
            saved.getSessionId(),
            saved.getClassId(),
            saved.getTutorId(),
            saved.getStudentId(),
            saved.getStartAt(),
            saved.getEndAt()
        ));
      }
    } catch (DataIntegrityViolationException ex) {
      log.warn("Bẫy lỗi GiST Exclusion Constraint khi xác nhận hàng loạt buổi học: {}", ex.getMessage());
      throw new ResourceConflictException(
          ErrorCode.SCHEDULE_OVERLAP,
          "Một hoặc nhiều buổi học đã bị trùng lịch với ca dạy khác vừa được xác nhận!"
      );
    }

    // 4d. Phát domain event chỉ khi toàn bộ lô đã được lưu thành công vào cơ sở dữ liệu
    eventsToPublish.forEach(eventPublisher::publishEvent);

    log.info("Chấp thuận hàng loạt đề xuất buổi học thành công: count={}", scheduledSessions.size());
    return scheduledSessions;
  }
}

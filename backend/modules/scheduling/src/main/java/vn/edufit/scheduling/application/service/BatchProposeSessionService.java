package vn.edufit.scheduling.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
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
import vn.edufit.scheduling.domain.model.SessionStatus;
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
 * Service xử lý Use Case đề xuất hàng loạt buổi học (Batch Propose Sessions).
 *
 * <p>Quy tắc kỹ thuật & Đảm bảo tính toàn vẹn:
 * <ul>
 *   <li><b>All-or-Nothing:</b> Toàn bộ lô đề xuất được thực thi trong một ranh giới giao dịch duy nhất
 *       ({@link Transactional}). Nếu bất kỳ buổi học nào vi phạm chính sách hoặc bị trùng lịch, toàn bộ lô sẽ bị rollback.</li>
 *   <li><b>Intra-batch Overlap Check:</b> Kiểm tra xung đột chéo giữa các buổi học trong cùng lô trước khi lưu.</li>
 *   <li><b>GiST Exclusion Trap:</b> Bẫy lỗi tranh chấp mức cơ sở dữ liệu nếu có giao thoa đồng thời.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchProposeSessionService {

  public record ProposeItemCommand(
      Instant startAt,
      Instant endAt,
      SessionMode mode,
      String placeOrLink,
      String repeatNote,
      String message
  ) {}

  private final TutoringSessionDomainRepository sessionRepository;
  private final SessionHistoryDomainRepository historyRepository;
  private final ConnectionValidationPort connectionValidationPort;
  private final SchedulingSecurityResolver schedulingSecurityResolver;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional
  public List<TutoringSession> proposeBatch(
      UUID classId,
      UUID tutorId,
      UUID studentId,
      List<ProposeItemCommand> items,
      CurrentUser actor
  ) {
    Instant now = clock.instant();

    if (classId == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Mã lớp học (classId) không được để trống");
    }
    if (items == null || items.isEmpty()) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Danh sách các buổi học đề xuất không được để trống"
      );
    }

    // 1. Xác thực lớp học qua ConnectionValidationPort và phân giải tutorId / studentId nếu chưa có
    UUID resolvedTutorId = tutorId;
    UUID resolvedStudentId = studentId;

    if (resolvedTutorId == null || resolvedStudentId == null) {
      var activeClass = connectionValidationPort.findActiveClassById(classId)
          .orElseThrow(() -> new InvalidOperationException(
              ErrorCode.INVALID_OPERATION,
              "Lớp học không ở trạng thái hoạt động (ACTIVE) hoặc không tồn tại"
          ));
      resolvedTutorId = activeClass.tutorId();
      resolvedStudentId = activeClass.studentId();
    } else {
      if (!connectionValidationPort.isConnectionActive(classId, resolvedTutorId, resolvedStudentId)) {
        throw new InvalidOperationException(
            ErrorCode.INVALID_OPERATION,
            "Lớp học không ở trạng thái hoạt động (ACTIVE) hoặc không tồn tại kết nối giữa hai bên"
        );
      }
    }

    // 2. Xác thực quyền đề xuất của người gọi qua SchedulingSecurityResolver
    schedulingSecurityResolver.requireProposeAccess(actor, resolvedTutorId, resolvedStudentId);
    UUID proposedBy = actor.getUserId();

    // 3. Kiểm định thời gian của từng buổi học trong lô
    for (int i = 0; i < items.size(); i++) {
      ProposeItemCommand item = items.get(i);
      if (item.startAt().isBefore(now)) {
        throw new InvalidOperationException(
            ErrorCode.VALIDATION_FAILED,
            "Thời điểm bắt đầu buổi học thứ " + (i + 1) + " phải ở tương lai"
        );
      }
      ProposalWindowPolicy.validateDuration(item.startAt(), item.endAt());
    }

    // 4. Kiểm tra xung đột nội bộ trong cùng lô (Intra-batch Overlaps)
    for (int i = 0; i < items.size(); i++) {
      for (int j = i + 1; j < items.size(); j++) {
        ProposeItemCommand item1 = items.get(i);
        ProposeItemCommand item2 = items.get(j);
        if (ConflictPolicy.isOverlapping(item1.startAt(), item1.endAt(), item2.startAt(), item2.endAt())) {
          throw new ResourceConflictException(
              ErrorCode.SCHEDULE_OVERLAP,
              "Các buổi học trong danh sách đề xuất bị trùng giờ với nhau (buổi " + (i + 1) + " và buổi " + (j + 1) + ")!"
          );
        }
      }
    }

    // 5. Kiểm tra xung đột với lịch đã có trong cơ sở dữ liệu
    for (ProposeItemCommand item : items) {
      // Kiểm tra lịch dạy bận của Gia sư
      List<TutoringSession> tutorBusySlots = sessionRepository.findTutorBusySlots(
          resolvedTutorId,
          item.startAt(),
          item.endAt()
      );
      boolean tutorHasConflict = tutorBusySlots.stream().anyMatch(existing ->
          ConflictPolicy.isOverlapping(item.startAt(), item.endAt(), existing.getStartAt(), existing.getEndAt())
      );
      if (tutorHasConflict) {
        throw new ResourceConflictException(
            ErrorCode.SCHEDULE_OVERLAP,
            "Gia sư đã có lịch dạy bị trùng trong khung giờ: " + item.startAt() + " -> " + item.endAt()
        );
      }

      // Kiểm tra lịch học bận của Học sinh
      List<TutoringSession> studentSessions = sessionRepository.findCalendarSessions(
          resolvedStudentId,
          item.startAt(),
          item.endAt()
      );
      boolean studentHasConflict = studentSessions.stream()
          .filter(s -> s.getStatus() == SessionStatus.SCHEDULED)
          .anyMatch(existing ->
              ConflictPolicy.isOverlapping(item.startAt(), item.endAt(), existing.getStartAt(), existing.getEndAt())
          );
      if (studentHasConflict) {
        throw new ResourceConflictException(
            ErrorCode.SCHEDULE_OVERLAP,
            "Học sinh đã có lịch học bị trùng trong khung giờ: " + item.startAt() + " -> " + item.endAt()
        );
      }
    }

    // 6. Lưu hàng loạt vào cơ sở dữ liệu (Atomic Transaction)
    List<TutoringSession> createdSessions = new ArrayList<>(items.size());
    try {
      for (ProposeItemCommand item : items) {
        Instant expiresAt = ProposalWindowPolicy.calculateExpiresAt(item.startAt(), now);
        TutoringSession session = TutoringSession.propose(
            UUID.randomUUID(),
            classId,
            resolvedTutorId,
            resolvedStudentId,
            item.startAt(),
            item.endAt(),
            item.mode(),
            item.placeOrLink(),
            item.repeatNote(),
            item.message(),
            proposedBy,
            expiresAt,
            now
        );

        TutoringSession saved = sessionRepository.save(session);
        createdSessions.add(saved);

        historyRepository.save(SessionHistory.create(
            saved.getSessionId(),
            null,
            "PROPOSE",
            proposedBy,
            item.message(),
            now
        ));

        eventPublisher.publishEvent(SessionProposedEvent.of(
            saved.getSessionId(),
            saved.getClassId(),
            saved.getTutorId(),
            saved.getStudentId(),
            proposedBy,
            saved.getStartAt(),
            saved.getEndAt(),
            saved.getExpiresAt()
        ));
      }
    } catch (DataIntegrityViolationException ex) {
      log.warn("Bẫy lỗi GiST Exclusion Constraint khi tạo hàng loạt đề xuất lịch: {}", ex.getMessage());
      throw new ResourceConflictException(
          ErrorCode.SCHEDULE_OVERLAP,
          "Khung giờ đề xuất đã bị trùng với một buổi học khác trong cơ sở dữ liệu!"
      );
    }

    log.info(
        "Đề xuất hàng loạt thành công: classId={}, count={}, proposedBy={}",
        classId,
        createdSessions.size(),
        proposedBy
    );
    return createdSessions;
  }
}

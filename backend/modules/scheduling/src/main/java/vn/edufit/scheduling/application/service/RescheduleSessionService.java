package vn.edufit.scheduling.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.scheduling.api.event.SessionRescheduleProposedEvent;
import vn.edufit.scheduling.api.event.SessionRescheduleRejectedEvent;
import vn.edufit.scheduling.api.event.SessionRescheduledEvent;
import vn.edufit.scheduling.domain.model.ProposalStatus;
import vn.edufit.scheduling.domain.model.SessionHistory;
import vn.edufit.scheduling.domain.model.SessionRescheduleProposal;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.policy.ConflictPolicy;
import vn.edufit.scheduling.domain.policy.ProposalWindowPolicy;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.domain.repository.SessionRescheduleProposalDomainRepository;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Service xử lý Use Case UC4.4: Đề xuất và Phản hồi Đổi lịch buổi học (Reschedule Session).
 *
 * <p>Quy tắc nghiệp vụ cốt lõi:
 * <ul>
 *   <li><b>BR-44:</b> Mỗi buổi học tại một thời điểm chỉ cho phép tối đa 1 đề xuất đổi lịch ở trạng thái {@code PENDING}.</li>
 *   <li><b>Nguyên tắc bảo toàn lịch cũ:</b> Buổi học gốc vẫn giữ nguyên khung giờ và trạng thái {@code SCHEDULED}
 *       cho đến khi đề xuất đổi lịch được đối tác chính thức chấp nhận.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RescheduleSessionService {

  private final TutoringSessionDomainRepository sessionRepository;
  private final SessionRescheduleProposalDomainRepository proposalRepository;
  private final SessionHistoryDomainRepository historyRepository;
  private final SchedulingSecurityResolver schedulingSecurityResolver;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * Tạo đề xuất đổi lịch buổi học sang khung giờ mới.
   */
  @Transactional
  public SessionRescheduleProposal proposeReschedule(
      UUID sessionId,
      CurrentUser actor,
      Instant newStartAt,
      Instant newEndAt,
      String reason
  ) {
    Instant now = clock.instant();

    // 1. Kiểm tra buổi học gốc
    TutoringSession session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy buổi học với ID: " + sessionId));

    if (session.getStatus() != SessionStatus.SCHEDULED) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Chỉ có thể đề xuất đổi lịch khi buổi học đang ở trạng thái SCHEDULED. Hiện tại: " + session.getStatus()
      );
    }

    // 2. Kiểm tra quyền người đề xuất qua SchedulingSecurityResolver (Gia sư, Học sinh hoặc Phụ huynh hợp lệ)
    schedulingSecurityResolver.requireProposeAccess(actor, session.getTutorId(), session.getStudentId());
    UUID proposedBy = actor.getUserId();

    // 3. Kiểm tra BR-44: Không cho phép tạo đề xuất mới nếu đang có đề xuất PENDING
    if (proposalRepository.findPendingBySessionId(sessionId).isPresent()) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Buổi học này đã có 1 đề xuất đổi lịch đang chờ xử lý, không thể tạo thêm (BR-44)!"
      );
    }

    // 4. Kiểm tra thời lượng & tính thời hạn phản hồi
    if (newStartAt.isBefore(now)) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Thời gian mới phải ở tương lai");
    }
    ProposalWindowPolicy.validateDuration(newStartAt, newEndAt);
    Instant expiresAt = ProposalWindowPolicy.calculateExpiresAt(newStartAt, now);

    // 5. Kiểm tra sơ bộ xung đột lịch mới của Gia sư
    List<TutoringSession> tutorBusySlots = sessionRepository.findTutorBusySlots(session.getTutorId(), newStartAt, newEndAt);
    boolean tutorHasConflict = tutorBusySlots.stream()
        .filter(s -> !s.getSessionId().equals(sessionId))
        .anyMatch(existing -> ConflictPolicy.isOverlapping(newStartAt, newEndAt, existing.getStartAt(), existing.getEndAt()));
    if (tutorHasConflict) {
      throw new ResourceConflictException(ErrorCode.SCHEDULE_OVERLAP, "Gia sư đã có lịch dạy khác trong khung giờ mới!");
    }

    // 6. Tạo Entity đề xuất đổi lịch
    SessionRescheduleProposal proposal = SessionRescheduleProposal.create(
        UUID.randomUUID(),
        sessionId,
        proposedBy,
        newStartAt,
        newEndAt,
        reason,
        expiresAt,
        now
    );
    SessionRescheduleProposal savedProposal = proposalRepository.save(proposal);

    // 7. Ghi Audit Trail
    historyRepository.save(SessionHistory.create(
        sessionId,
        savedProposal.getProposalId(),
        "RESCHEDULE_PROPOSE",
        proposedBy,
        reason,
        now
    ));

    // 8. Bắn Domain Event
    eventPublisher.publishEvent(SessionRescheduleProposedEvent.of(
        savedProposal.getProposalId(),
        sessionId,
        session.getClassId(),
        session.getTutorId(),
        session.getStudentId(),
        proposedBy,
        newStartAt,
        newEndAt,
        reason,
        expiresAt
    ));

    log.info("Tạo đề xuất đổi lịch thành công: proposalId={}, sessionId={}", savedProposal.getProposalId(), sessionId);
    return savedProposal;
  }

  /**
   * Phương thức tương thích ngược chấp nhận UUID của người đề xuất đổi lịch.
   */
  @Transactional
  public SessionRescheduleProposal proposeReschedule(
      UUID sessionId,
      UUID proposedBy,
      Instant newStartAt,
      Instant newEndAt,
      String reason
  ) {
    return proposeReschedule(sessionId, SimpleCurrentUser.of(proposedBy), newStartAt, newEndAt, reason);
  }

  /**
   * Phản hồi đề xuất đổi lịch (Chấp nhận hoặc Từ chối).
   */
  @Transactional
  public SessionRescheduleProposal respondToReschedule(
      UUID proposalId,
      CurrentUser actor,
      boolean accept,
      String responseReason
  ) {
    Instant now = clock.instant();

    // 1. Lấy thông tin đề xuất đổi lịch
    SessionRescheduleProposal proposal = proposalRepository.findById(proposalId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy đề xuất đổi lịch ID: " + proposalId));

    if (proposal.getStatus() != ProposalStatus.PENDING) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Đề xuất đổi lịch không còn ở trạng thái PENDING. Trạng thái hiện tại: " + proposal.getStatus()
      );
    }

    // 2. Lấy buổi học với Khóa bi quan
    TutoringSession session = sessionRepository.findByIdForUpdate(proposal.getSessionId())
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy buổi học liên kết với đề xuất"));

    // 3. Kiểm tra quyền của người phản hồi qua SchedulingSecurityResolver (phải là bên đối tác)
    schedulingSecurityResolver.requireCounterpartyResponseAccess(actor, session, proposal.getProposedBy());
    UUID responderId = actor.getUserId();

    // 4. Nếu từ chối đề xuất đổi lịch -> Giữ nguyên lịch học cũ
    if (!accept) {
      proposal.reject(responderId, responseReason, now);
      SessionRescheduleProposal updatedProposal = proposalRepository.save(proposal);

      historyRepository.save(SessionHistory.create(
          session.getSessionId(),
          proposalId,
          "RESCHEDULE_REJECT",
          responderId,
          responseReason,
          now
      ));

      log.info("Đã từ chối đề xuất đổi lịch: proposalId={}", proposalId);

      eventPublisher.publishEvent(SessionRescheduleRejectedEvent.of(
          proposalId,
          session.getSessionId(),
          session.getClassId(),
          session.getTutorId(),
          session.getStudentId(),
          proposal.getProposedBy(),
          responderId,
          responseReason
      ));

      return updatedProposal;
    }

    // 5. Nếu chấp nhận đề xuất đổi lịch: Kiểm tra trạng thái buổi học, xung đột & Cập nhật thời gian buổi học
    if (session.getStatus() != SessionStatus.SCHEDULED) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Chỉ có thể chấp nhận đổi lịch khi buổi học đang ở trạng thái SCHEDULED. Hiện tại: " + session.getStatus()
      );
    }

    List<TutoringSession> tutorBusySlots = sessionRepository.findTutorBusySlots(
        session.getTutorId(),
        proposal.getNewStartAt(),
        proposal.getNewEndAt()
    );
    boolean tutorHasConflict = tutorBusySlots.stream()
        .filter(s -> !s.getSessionId().equals(session.getSessionId()))
        .anyMatch(existing -> ConflictPolicy.isOverlapping(
            proposal.getNewStartAt(),
            proposal.getNewEndAt(),
            existing.getStartAt(),
            existing.getEndAt()
        ));
    if (tutorHasConflict) {
      throw new ResourceConflictException(
          ErrorCode.SCHEDULE_OVERLAP,
          "Gia sư đã có lịch dạy khác trong khung giờ mới này!"
      );
    }

    proposal.accept(responderId, now);
    SessionRescheduleProposal updatedProposal = proposalRepository.save(proposal);

    session.updateTime(proposal.getNewStartAt(), proposal.getNewEndAt());

    try {
      sessionRepository.save(session);
    } catch (DataIntegrityViolationException ex) {
      log.warn("Bẫy lỗi GiST Exclusion Constraint khi cập nhật thời gian đổi lịch: {}", ex.getMessage());
      throw new ResourceConflictException(
          ErrorCode.SCHEDULE_OVERLAP,
          "Khung giờ mới bị trùng với lịch học khác trong cơ sở dữ liệu!"
      );
    }

    historyRepository.save(SessionHistory.create(
        session.getSessionId(),
        proposalId,
        "RESCHEDULE_ACCEPT",
        responderId,
        null,
        now
    ));

    eventPublisher.publishEvent(SessionRescheduledEvent.of(
        proposalId,
        session.getSessionId(),
        session.getClassId(),
        session.getTutorId(),
        session.getStudentId(),
        proposal.getNewStartAt(),
        proposal.getNewEndAt(),
        responderId
    ));

    log.info(
        "Chấp nhận đổi lịch thành công: proposalId={}, sessionId={}, newStartAt={}",
        proposalId,
        session.getSessionId(),
        proposal.getNewStartAt()
    );

    return updatedProposal;
  }

  /**
   * Phương thức tương thích ngược chấp nhận UUID của người phản hồi đổi lịch.
   */
  @Transactional
  public SessionRescheduleProposal respondToReschedule(
      UUID proposalId,
      UUID responderId,
      boolean accept,
      String responseReason
  ) {
    return respondToReschedule(proposalId, SimpleCurrentUser.of(responderId), accept, responseReason);
  }

  /**
   * Tra cứu đề xuất đổi lịch đang chờ xử lý (PENDING) của một buổi học kèm kiểm tra quyền truy cập.
   */
  @Transactional(readOnly = true)
  public Optional<SessionRescheduleProposal> findActiveProposalBySessionId(UUID sessionId, CurrentUser actor) {
    TutoringSession session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy buổi học với ID: " + sessionId));

    schedulingSecurityResolver.requireSessionAccess(actor, session);
    return proposalRepository.findPendingBySessionId(sessionId);
  }
}

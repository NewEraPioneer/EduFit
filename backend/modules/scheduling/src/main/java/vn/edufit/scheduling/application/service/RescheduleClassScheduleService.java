package vn.edufit.scheduling.application.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.scheduling.api.event.SessionCancelledEvent;
import vn.edufit.scheduling.api.event.SessionScheduledEvent;
import vn.edufit.scheduling.application.port.ConnectionValidationPort;
import vn.edufit.scheduling.domain.model.CancelReason;
import vn.edufit.scheduling.domain.model.ProposalStatus;
import vn.edufit.scheduling.domain.model.RecurringRescheduleProposal;
import vn.edufit.scheduling.domain.model.SessionHistory;
import vn.edufit.scheduling.domain.model.SessionMode;
import vn.edufit.scheduling.domain.model.SessionStatus;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.model.WeeklySlot;
import vn.edufit.scheduling.domain.policy.ConflictPolicy;
import vn.edufit.scheduling.domain.policy.RecurringSlotParser;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.shared.exception.ResourceConflictException;

/**
 * Service xử lý Use Case UC4.8: Điều chỉnh lịch học định kỳ của lớp học (Modify Class Recurring Schedule).
 *
 * <p>Quy tắc nghiệp vụ bảo đảm:
 * <ul>
 *   <li><b>Bounded Horizon (R3):</b> Khoảng thời gian áp dụng giữa {@code effectiveFrom} và {@code effectiveTo}
 *       bắt buộc phải nằm trong giới hạn từ 4 đến 8 tuần (28 đến 56 ngày). Bất kỳ đề xuất nào ngoài khoảng này
 *       đều bị từ chối với lỗi {@link ErrorCode#VALIDATION_FAILED}.</li>
 *   <li><b>Mutual Consent:</b> Người phản hồi bắt buộc phải là đối tác phía bên kia (Gia sư $\leftrightarrow$ Học sinh/Phụ huynh).
 *       Không ai được phép tự duyệt đề xuất do chính mình gửi.</li>
 *   <li><b>Database Invariant (R4):</b> Không tạo bảng mới; các đề xuất in-flight được lưu trên {@link ConcurrentMap}
 *       có cơ chế hết hạn (TTL). Khi được chấp thuận, các buổi học cụ thể mới được lưu bền vững vào bảng {@code tutoring_session}.</li>
 *   <li><b>Atomic Regeneration:</b> Khi chấp thuận, dưới 1 giao dịch {@link Transactional} duy nhất:
 *       toàn bộ các buổi học SCHEDULED sắp tới trong khoảng thời gian sẽ bị hủy, và các buổi học mới tương ứng
 *       với các khung giờ định kỳ sẽ được sinh ra và bảo vệ bởi PostgreSQL GiST exclusion constraint.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RescheduleClassScheduleService {

  public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private final TutoringSessionDomainRepository sessionRepository;
  private final SessionHistoryDomainRepository historyRepository;
  private final ConnectionValidationPort connectionValidationPort;
  private final SchedulingSecurityResolver schedulingSecurityResolver;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * Kho lưu trữ in-memory quản lý trạng thái các đề xuất đổi lịch định kỳ (In-flight Proposals - R4).
   */
  private final ConcurrentMap<UUID, RecurringRescheduleProposal> proposalStore = new ConcurrentHashMap<>();

  public record RecurringScheduleResult(
      RecurringRescheduleProposal proposal,
      int cancelledSessionCount,
      List<TutoringSession> generatedSessions
  ) {}

  /**
   * Đề xuất đổi lịch học định kỳ mới cho lớp học (overload tham số chi tiết).
   */
  public RecurringRescheduleProposal proposeRecurringSchedule(
      UUID classId,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      List<String> newWeeklySlots,
      SessionMode mode,
      String placeOrLink,
      String message,
      CurrentUser actor
  ) {
    Instant now = clock.instant();
    LocalDate today = LocalDate.ofInstant(now, VIETNAM_ZONE);

    if (classId == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Mã lớp học (classId) không được để trống");
    }
    if (effectiveFrom == null || effectiveTo == null) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "effectiveFrom và effectiveTo không được để trống"
      );
    }

    // 1. Kiểm tra Bounded Horizon: Phải nằm trong khoảng 4 đến 8 tuần (28 đến 56 ngày)
    if (effectiveFrom.isBefore(today)) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Ngày bắt đầu áp dụng (" + effectiveFrom + ") không được ở trong quá khứ so với hôm nay (" + today + ")"
      );
    }

    long days = ChronoUnit.DAYS.between(effectiveFrom, effectiveTo);
    if (days < 28 || days > 56) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Khoảng thời gian áp dụng lịch định kỳ phải từ 4 đến 8 tuần (28 đến 56 ngày). Hiện tại: " + days + " ngày."
      );
    }

    // 2. Kiểm tra định dạng và không bị trùng lặp nội tại trong danh sách khung giờ định kỳ mới
    RecurringSlotParser.parseAndValidateSlots(newWeeklySlots);

    // 3. Xác thực lớp học đang ACTIVE và lấy thông tin gia sư, học sinh
    var activeClass = connectionValidationPort.findActiveClassById(classId)
        .orElseThrow(() -> new InvalidOperationException(
            ErrorCode.INVALID_OPERATION,
            "Lớp học không ở trạng thái hoạt động (ACTIVE) hoặc không tồn tại"
        ));

    // 4. Kiểm tra quyền của người đề xuất (Gia sư, Học sinh hoặc Phụ huynh hợp lệ)
    schedulingSecurityResolver.requireProposeAccess(actor, activeClass.tutorId(), activeClass.studentId());
    UUID proposedBy = actor.getUserId();

    // 5. Tính hạn chót phản hồi (TTL: 48h hoặc mốc bắt đầu ngày effectiveFrom, tùy mốc nào sớm hơn)
    Instant effectiveFromStart = effectiveFrom.atStartOfDay(VIETNAM_ZONE).toInstant();
    Instant expiresAt = now.plus(Duration.ofHours(48));
    if (expiresAt.isAfter(effectiveFromStart)) {
      expiresAt = effectiveFromStart;
    }
    if (expiresAt.isBefore(now)) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED,
          "Thời điểm bắt đầu áp dụng lịch mới quá gần, không đủ thời gian phản hồi"
      );
    }

    // 6. Xử lý đề xuất cũ đang chờ (nếu có) của lớp học này
    for (RecurringRescheduleProposal existing : proposalStore.values()) {
      if (existing.getClassId().equals(classId) && existing.isPending(now)) {
        existing.expire(now);
      }
    }

    // 7. Tạo đề xuất mới và lưu trên bộ nhớ RAM
    RecurringRescheduleProposal proposal = RecurringRescheduleProposal.create(
        UUID.randomUUID(),
        classId,
        activeClass.tutorId(),
        activeClass.studentId(),
        proposedBy,
        effectiveFrom,
        effectiveTo,
        newWeeklySlots,
        mode,
        placeOrLink,
        message,
        expiresAt,
        now
    );

    proposalStore.put(proposal.getProposalId(), proposal);
    log.info(
        "Tạo đề xuất đổi lịch định kỳ UC4.8 thành công: proposalId={}, classId={}, horizon=[{} -> {}], slots={}",
        proposal.getProposalId(),
        classId,
        effectiveFrom,
        effectiveTo,
        newWeeklySlots
    );

    return proposal;
  }

  /**
   * Phản hồi đề xuất đổi lịch học định kỳ (Chấp nhận hoặc Từ chối).
   */
  @Transactional
  public RecurringScheduleResult respondRecurringSchedule(
      UUID proposalId,
      boolean accept,
      String reason,
      CurrentUser actor
  ) {
    Instant now = clock.instant();

    if (proposalId == null) {
      throw new InvalidOperationException(ErrorCode.VALIDATION_FAILED, "Mã đề xuất (proposalId) không được để trống");
    }

    // 1. Tìm kiếm đề xuất in-flight trên bộ nhớ RAM
    RecurringRescheduleProposal proposal = proposalStore.get(proposalId);
    if (proposal == null) {
      throw new EntityNotFoundException("Không tìm thấy đề xuất đổi lịch định kỳ với ID: " + proposalId);
    }

    if (!proposal.isPending(now)) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Đề xuất đổi lịch định kỳ đã được xử lý hoặc đã hết hạn"
      );
    }

    // 2. Kiểm tra nguyên tắc đồng thuận hai phía (Mutual Consent)
    schedulingSecurityResolver.requireCounterpartyResponseAccess(
        actor,
        proposal.getTutorId(),
        proposal.getStudentId(),
        proposal.getProposedBy()
    );
    UUID responderId = actor.getUserId();

    // 2b. Kiểm tra lớp học vẫn đang ở trạng thái ACTIVE (tránh chấp thuận lịch cho lớp đã kết thúc)
    connectionValidationPort.findActiveClassById(proposal.getClassId())
        .orElseThrow(() -> new InvalidOperationException(
            ErrorCode.INVALID_OPERATION,
            "Lớp học không ở trạng thái hoạt động (ACTIVE) hoặc không tồn tại"
        ));

    // 3. Xử lý trường hợp TỪ CHỐI (REJECT)
    if (!accept) {
      proposal.reject(responderId, reason, now);
      log.info("Đã từ chối đề xuất đổi lịch định kỳ: proposalId={}, responderId={}", proposalId, responderId);
      return new RecurringScheduleResult(
          proposal,
          0,
          Collections.emptyList()
      );
    }

    // 4. Xử lý trường hợp CHẤP THUẬN (ACCEPT) - Thực thi giao dịch nguyên tử (All-or-Nothing)
    // 4a. Xác định khoảng thời gian cần hủy các buổi học SCHEDULED sắp diễn ra
    Instant horizonStart = proposal.getEffectiveFrom().atStartOfDay(VIETNAM_ZONE).toInstant();
    Instant windowStart = now.isAfter(horizonStart) ? now : horizonStart;
    Instant horizonEnd = proposal.getEffectiveTo().plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant();

    // 4b. Hủy toàn bộ các buổi học SCHEDULED trong tương lai của lớp học trong khoảng thời gian này
    List<TutoringSession> existingScheduledSessions = sessionRepository.findScheduledSessionsByClass(
        proposal.getClassId(),
        windowStart,
        horizonEnd
    );

    for (TutoringSession session : existingScheduledSessions) {
      session.cancel(
          responderId,
          CancelReason.SCHEDULE_CONFLICT,
          "Hủy để áp dụng lịch học định kỳ mới (UC4.8)",
          false,
          now
      );
      sessionRepository.save(session);

      historyRepository.save(SessionHistory.create(
          session.getSessionId(),
          null,
          "CANCEL_FOR_RECURRING",
          responderId,
          "Hủy do áp dụng lịch định kỳ mới UC4.8",
          now
      ));

      eventPublisher.publishEvent(SessionCancelledEvent.of(
          session.getSessionId(),
          session.getClassId(),
          session.getTutorId(),
          session.getStudentId(),
          responderId,
          CancelReason.SCHEDULE_CONFLICT.name(),
          "Hủy để áp dụng lịch học định kỳ mới (UC4.8)",
          false
      ));
    }
    int cancelledCount = existingScheduledSessions.size();

    // 4c. Phân tích danh sách các khung giờ hàng tuần mới
    List<WeeklySlot> weeklySlots = RecurringSlotParser.parseAndValidateSlots(proposal.getNewWeeklySlots());

    // 4d. Tái sinh (Regenerate) các buổi học cụ thể mới trong toàn bộ khoảng thời gian [effectiveFrom, effectiveTo]
    List<TutoringSession> generatedSessions = new ArrayList<>();
    try {
      for (LocalDate date = proposal.getEffectiveFrom(); !date.isAfter(proposal.getEffectiveTo()); date = date.plusDays(1)) {
        DayOfWeek dow = date.getDayOfWeek();
        for (WeeklySlot slot : weeklySlots) {
          if (slot.dayOfWeek() == dow) {
            Instant startAt = date.atTime(slot.startTime()).atZone(VIETNAM_ZONE).toInstant();
            Instant endAt = date.atTime(slot.endTime()).atZone(VIETNAM_ZONE).toInstant();

            // Chỉ sinh các buổi học có thời điểm bắt đầu ở tương lai
            if (startAt.isAfter(now)) {
              // Kiểm tra xung đột lịch dạy của Gia sư (loại trừ các ca của lớp này vừa bị hủy)
              List<TutoringSession> tutorBusySlots = sessionRepository.findTutorBusySlots(
                  proposal.getTutorId(),
                  startAt,
                  endAt
              );
              boolean tutorConflict = tutorBusySlots.stream().anyMatch(existing ->
                  ConflictPolicy.isOverlapping(startAt, endAt, existing.getStartAt(), existing.getEndAt())
              );
              if (tutorConflict) {
                throw new ResourceConflictException(
                    ErrorCode.SCHEDULE_OVERLAP,
                    "Khung giờ " + slot.raw() + " vào ngày " + date + " bị trùng với lịch dạy khác của Gia sư!"
                );
              }

              // Kiểm tra xung đột lịch học của Học sinh
              List<TutoringSession> studentSessions = sessionRepository.findCalendarSessions(
                  proposal.getStudentId(),
                  startAt,
                  endAt
              );
              boolean studentConflict = studentSessions.stream()
                  .filter(s -> s.getStatus() == SessionStatus.SCHEDULED)
                  .anyMatch(existing ->
                      ConflictPolicy.isOverlapping(startAt, endAt, existing.getStartAt(), existing.getEndAt())
                  );
              if (studentConflict) {
                throw new ResourceConflictException(
                    ErrorCode.SCHEDULE_OVERLAP,
                    "Khung giờ " + slot.raw() + " vào ngày " + date + " bị trùng với lịch học khác của Học sinh!"
                );
              }

              // Khởi tạo buổi học đã được xác nhận (SCHEDULED)
              TutoringSession newSession = TutoringSession.createScheduled(
                  UUID.randomUUID(),
                  proposal.getClassId(),
                  proposal.getTutorId(),
                  proposal.getStudentId(),
                  startAt,
                  endAt,
                  proposal.getDefaultMode() != null ? proposal.getDefaultMode() : SessionMode.ONLINE,
                  proposal.getPlaceOrLink(),
                  "Định kỳ: " + slot.raw(),
                  proposal.getMessage(),
                  proposal.getProposedBy(),
                  responderId,
                  now
              );

              TutoringSession saved = sessionRepository.save(newSession);
              generatedSessions.add(saved);

              historyRepository.save(SessionHistory.create(
                  saved.getSessionId(),
                  null,
                  "SCHEDULE_RECURRING",
                  responderId,
                  "Sinh tự động từ lịch định kỳ UC4.8",
                  now
              ));

              eventPublisher.publishEvent(SessionScheduledEvent.of(
                  saved.getSessionId(),
                  saved.getClassId(),
                  saved.getTutorId(),
                  saved.getStudentId(),
                  saved.getStartAt(),
                  saved.getEndAt()
              ));
            }
          }
        }
      }
    } catch (DataIntegrityViolationException ex) {
      log.warn("Bẫy lỗi GiST Exclusion Constraint khi sinh lịch định kỳ UC4.8: {}", ex.getMessage());
      throw new ResourceConflictException(
          ErrorCode.SCHEDULE_OVERLAP,
          "Một buổi học mới trong lịch định kỳ bị trùng lịch với ca dạy/học khác trong cơ sở dữ liệu!"
      );
    }

    // 4e. Đánh dấu đề xuất là ACCEPTED
    proposal.accept(responderId, now);

    log.info(
        "Chấp thuận đổi lịch định kỳ UC4.8 thành công: proposalId={}, cancelledCount={}, generatedCount={}",
        proposalId,
        cancelledCount,
        generatedSessions.size()
    );

    return new RecurringScheduleResult(
        proposal,
        cancelledCount,
        generatedSessions
    );
  }

  /**
   * Tra cứu thông tin đề xuất in-flight theo proposalId.
   */
  public Optional<RecurringRescheduleProposal> findProposalById(UUID proposalId) {
    if (proposalId == null) {
      return Optional.empty();
    }
    RecurringRescheduleProposal proposal = proposalStore.get(proposalId);
    if (proposal != null) {
      proposal.isPending(clock.instant()); // Kích hoạt kiểm tra TTL
    }
    return Optional.ofNullable(proposal);
  }

  /**
   * Tra cứu đề xuất PENDING hiện tại của lớp học (nếu có).
   */
  public Optional<RecurringRescheduleProposal> findActiveProposalByClassId(UUID classId) {
    if (classId == null) {
      return Optional.empty();
    }
    Instant now = clock.instant();
    return proposalStore.values().stream()
        .filter(p -> p.getClassId().equals(classId) && p.isPending(now))
        .findFirst();
  }

  /**
   * Hủy các đề xuất đổi lịch học định kỳ đang chờ (In-flight Proposals) khi lớp học kết thúc (Cascade Termination).
   */
  public void cancelActiveProposalForClass(UUID classId, String reason, Instant now) {
    if (classId == null) {
      return;
    }
    for (RecurringRescheduleProposal p : proposalStore.values()) {
      if (p != null && Objects.equals(p.getClassId(), classId) && p.isPending(now)) {
        p.expire(now);
        log.info("Đã hủy đề xuất đổi lịch định kỳ do lớp học kết thúc: proposalId={}, classId={}, reason={}",
            p.getProposalId(), classId, reason);
      }
    }
  }
}

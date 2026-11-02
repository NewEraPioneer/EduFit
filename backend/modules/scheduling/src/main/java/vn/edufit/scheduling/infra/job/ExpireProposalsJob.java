package vn.edufit.scheduling.infra.job;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.scheduling.api.event.SessionExpiredEvent;
import vn.edufit.scheduling.domain.model.SessionHistory;
import vn.edufit.scheduling.domain.model.SessionRescheduleProposal;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.SessionHistoryDomainRepository;
import vn.edufit.scheduling.domain.repository.SessionRescheduleProposalDomainRepository;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;

/**
 * Tác vụ ngầm (Background Scheduled Job) định kỳ quét và thu hồi các đề xuất buổi học hoặc đổi lịch đã hết hạn.
 *
 * <p>Mục đích thiết kế & Liên kết quy tắc nghiệp vụ:
 * <ul>
 *   <li><b>FR-17 (Hết hạn đề xuất tự động):</b> Đề xuất buổi học (Session Proposal) nếu không được phản hồi
 *       trong thời hạn cho phép (tối đa 48 giờ hoặc trước giờ bắt đầu buổi học theo BR-43) sẽ tự động
 *       chuyển sang trạng thái {@code EXPIRED}.</li>
 *   <li><b>BR-43 (Thời hạn phản hồi đề xuất):</b> Thời hạn phản hồi được tính toán khi tạo đề xuất
 *       và lưu tại trường {@code expires_at}. Job này quét tất cả bản ghi có trạng thái {@code PROPOSED}
 *       và {@code expires_at < now}.</li>
 *   <li><b>BR-44 (Đề xuất đổi lịch hết hạn):</b> Các đề xuất dời lịch (Reschedule Proposals) đang ở trạng thái
 *       {@code PENDING} nếu quá thời hạn phản hồi {@code expires_at} cũng tự động chuyển sang {@code EXPIRED},
 *       giải phóng cờ chặn để các bên có thể đề xuất lại nếu cần.</li>
 *   <li><b>Chu kỳ thực thi (Cron):</b> Mặc định chạy mỗi 15 phút ({@code 0 *&#47;15 * * * *}), có thể tinh chỉnh
 *       linh hoạt qua cấu hình {@code edufit.scheduling.jobs.expire-proposals.cron}.</li>
 *   <li><b>Bảo toàn tính liên tục:</b> Xử lý từng bản ghi với khối {@code try-catch} cục bộ để lỗi trên
 *       1 bản ghi không làm gián đoạn việc quét các bản ghi còn lại trong batch.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpireProposalsJob {

  private final TutoringSessionDomainRepository sessionRepository;
  private final SessionRescheduleProposalDomainRepository proposalRepository;
  private final SessionHistoryDomainRepository historyRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * Phương thức điều phối chính của tác vụ ngầm quét hết hạn đề xuất.
   *
   * <p>Được Spring Task Scheduler kích hoạt tự động theo biểu thức Cron đã cấu hình.
   */
  @Scheduled(cron = "${edufit.scheduling.jobs.expire-proposals.cron:0 */15 * * * *}")
  @Transactional
  public void sweepExpiredProposals() {
    Instant now = clock.instant();
    log.debug("Bắt đầu tác vụ quét đề xuất hết hạn tại mốc thời gian: {}", now);

    sweepExpiredSessionProposals(now);
    sweepExpiredRescheduleProposals(now);
  }

  /**
   * Quét và chuyển trạng thái các đề xuất buổi học (Session Proposal) quá hạn sang EXPIRED (FR-17, BR-43).
   *
   * @param now Mốc thời gian hiện tại
   */
  private void sweepExpiredSessionProposals(Instant now) {
    List<TutoringSession> expiredSessions = sessionRepository.findExpiredProposals(now);
    if (expiredSessions.isEmpty()) {
      return;
    }

    log.info("Tìm thấy {} đề xuất buổi học quá hạn cần chuyển sang EXPIRED", expiredSessions.size());
    for (TutoringSession session : expiredSessions) {
      try {
        session.expire(now);
        sessionRepository.save(session);

        historyRepository.save(SessionHistory.create(
            session.getSessionId(),
            null,
            "EXPIRE",
            null,
            "Tự động hết hạn do quá thời gian phản hồi (FR-17)",
            now
        ));

        eventPublisher.publishEvent(SessionExpiredEvent.of(
            session.getSessionId(),
            session.getClassId(),
            session.getTutorId(),
            session.getStudentId()
        ));

        log.info("Đã đánh dấu hết hạn đề xuất buổi học: sessionId={}", session.getSessionId());
      } catch (Exception ex) {
        log.error("Lỗi khi xử lý hết hạn đề xuất sessionId={}: {}", session.getSessionId(), ex.getMessage(), ex);
      }
    }
  }

  /**
   * Quét và chuyển trạng thái các đề xuất đổi lịch (Reschedule Proposal) quá hạn sang EXPIRED.
   *
   * @param now Mốc thời gian hiện tại
   */
  private void sweepExpiredRescheduleProposals(Instant now) {
    List<SessionRescheduleProposal> expiredProposals = proposalRepository.findExpiredPendingProposals(now);
    if (expiredProposals.isEmpty()) {
      return;
    }

    log.info("Tìm thấy {} đề xuất đổi lịch quá hạn cần chuyển sang EXPIRED", expiredProposals.size());
    for (SessionRescheduleProposal proposal : expiredProposals) {
      try {
        proposal.expire(now);
        proposalRepository.save(proposal);

        historyRepository.save(SessionHistory.create(
            proposal.getSessionId(),
            proposal.getProposalId(),
            "RESCHEDULE_EXPIRE",
            null,
            "Tự động hết hạn đề xuất đổi lịch",
            now
        ));

        log.info("Đã đánh dấu hết hạn đề xuất đổi lịch: proposalId={}, sessionId={}",
            proposal.getProposalId(), proposal.getSessionId());
      } catch (Exception ex) {
        log.error("Lỗi khi xử lý hết hạn đề xuất đổi lịch proposalId={}: {}", proposal.getProposalId(), ex.getMessage(), ex);
      }
    }
  }
}

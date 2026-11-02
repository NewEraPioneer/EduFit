package vn.edufit.scheduling.infra.job;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.scheduling.api.event.OverdueOutcomeReminderEvent;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;

/**
 * Tác vụ ngầm (Background Scheduled Job) định kỳ nhắc nhở Gia sư ghi nhận kết quả buổi học (FR-20).
 *
 * <p>Mục đích thiết kế & Quy tắc nghiệp vụ:
 * <ul>
 *   <li><b>FR-20 (Ghi nhận kết quả buổi học):</b> Gia sư có nghĩa vụ đánh dấu kết quả ca học
 *       ({@code COMPLETED} hoặc {@code ABSENT}) trong vòng 24 giờ sau khi ca học kết thúc.</li>
 *   <li><b>Cơ chế hoạt động:</b> Quét các buổi học có trạng thái {@code SCHEDULED} nhưng thời gian kết thúc
 *       ({@code end_at}) đã qua mốc hiện tại ({@code end_at < now}).</li>
 *   <li><b>Giới hạn cửa sổ nhắc nhở (Grace Window):</b> Chỉ nhắc nhở các buổi học đã kết thúc trong vòng 48 giờ trở lại,
 *       tránh việc gửi thông báo lặp lại vô tận cho các buổi học quá cũ.</li>
 *   <li><b>Chu kỳ thực thi (Cron):</b> Mặc định chạy mỗi 1 giờ ({@code 0 0 * * * *}), có thể cấu hình qua
 *       {@code edufit.scheduling.jobs.overdue-outcome.cron}.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueOutcomeReminderJob {

  private static final int MAX_LOOKBACK_HOURS = 48;

  private final TutoringSessionDomainRepository sessionRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * Quét và gửi thông báo nhắc nhở Gia sư hoàn thành ghi nhận kết quả ca học.
   */
  @Scheduled(cron = "${edufit.scheduling.jobs.overdue-outcome.cron:0 0 * * * *}")
  @Transactional(readOnly = true)
  public void scanAndRemindOverdueOutcomes() {
    Instant now = clock.instant();
    log.debug("Bắt đầu tác vụ quét nhắc nhở ghi nhận kết quả buổi học tại: {}", now);

    List<TutoringSession> endedSessions = sessionRepository.findOverdueOutcomeSessions(now);
    if (endedSessions.isEmpty()) {
      return;
    }

    Instant lookbackCutoff = now.minus(MAX_LOOKBACK_HOURS, ChronoUnit.HOURS);

    List<TutoringSession> eligibleSessions = endedSessions.stream()
        .filter(s -> s.getEndAt().isAfter(lookbackCutoff))
        .toList();

    if (eligibleSessions.isEmpty()) {
      return;
    }

    log.info("Tìm thấy {} buổi học đã kết thúc chưa ghi nhận kết quả", eligibleSessions.size());

    for (TutoringSession session : eligibleSessions) {
      try {
        eventPublisher.publishEvent(OverdueOutcomeReminderEvent.of(
            session.getSessionId(),
            session.getClassId(),
            session.getTutorId(),
            session.getEndAt()
        ));

        log.info("Đã gửi sự kiện nhắc ghi nhận kết quả ca học: sessionId={}, tutorId={}, endAt={}",
            session.getSessionId(), session.getTutorId(), session.getEndAt());
      } catch (Exception ex) {
        log.error("Lỗi khi phát sự kiện nhắc ghi nhận kết quả sessionId={}: {}", session.getSessionId(), ex.getMessage(), ex);
      }
    }
  }
}

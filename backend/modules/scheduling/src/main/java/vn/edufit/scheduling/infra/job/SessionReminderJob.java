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
import vn.edufit.scheduling.api.event.SessionUpcomingReminderEvent;
import vn.edufit.scheduling.domain.model.TutoringSession;
import vn.edufit.scheduling.domain.repository.TutoringSessionDomainRepository;

/**
 * Tác vụ ngầm (Background Scheduled Job) định kỳ quét các buổi học sắp diễn ra để gửi thông báo nhắc nhở (FR-31).
 *
 * <p>Mục đích thiết kế & Nguyên tắc giải thuật:
 * <ul>
 *   <li><b>FR-31 (Nhắc nhở buổi học):</b> Tự động thông báo nhắc lịch học trước 24 giờ và trước 2 giờ cho cả Gia sư và Học sinh.</li>
 *   <li><b>Giải thuật Cửa sổ trượt không giao thoa (Non-overlapping Sliding Windows):</b>
 *       Với chu kỳ quét mỗi 10 phút, công việc chia thành 2 khung cửa sổ thời gian:
 *       <ol>
 *         <li><b>Khung nhắc trước 24h:</b> {@code [now + 24h, now + 24h + 10m)}</li>
 *         <li><b>Khung nhắc trước 2h:</b> {@code [now + 2h, now + 2h + 10m)}</li>
 *       </ol>
 *       Do cửa sổ bằng đúng chu kỳ kích hoạt của Cron (10 phút), mỗi buổi học có mốc {@code start_at} cố định
 *       sẽ chỉ rơi vào đúng 1 lần quét duy nhất tại mỗi mốc, loại bỏ triệt để nguy cơ gửi trùng thông báo (Spam).
 *   </li>
 *   <li><b>Chu kỳ thực thi (Cron):</b> Mặc định chạy mỗi 10 phút ({@code 0 *&#47;10 * * * *}), có thể cấu hình qua
 *       {@code edufit.scheduling.jobs.session-reminder.cron}.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionReminderJob {

  private static final int JOB_INTERVAL_MINUTES = 10;

  private final TutoringSessionDomainRepository sessionRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * Quét và gửi thông báo nhắc nhở cho các buổi học sắp diễn ra.
   */
  @Scheduled(cron = "${edufit.scheduling.jobs.session-reminder.cron:0 */10 * * * *}")
  @Transactional(readOnly = true)
  public void scanAndSendSessionReminders() {
    Instant now = clock.instant();
    log.debug("Bắt đầu tác vụ quét nhắc nhở lịch học tại mốc thời gian: {}", now);

    // 1. Quét cửa sổ nhắc trước 24 giờ
    scanWindow(now, 24, ChronoUnit.HOURS);

    // 2. Quét cửa sổ nhắc trước 2 giờ
    scanWindow(now, 2, ChronoUnit.HOURS);
  }

  /**
   * Quét một khung cửa sổ thời gian cụ thể và phát sinh sự kiện nhắc nhở.
   *
   * @param now Mốc thời gian hiện tại
   * @param amount Lượng thời gian trước buổi học
   * @param unit Đơn vị thời gian (HOURS)
   */
  private void scanWindow(Instant now, int amount, ChronoUnit unit) {
    Instant windowStart = now.plus(amount, unit);
    Instant windowEnd = windowStart.plus(JOB_INTERVAL_MINUTES, ChronoUnit.MINUTES);

    List<TutoringSession> upcomingSessions = sessionRepository.findUpcomingSessions(windowStart, windowEnd);
    if (upcomingSessions.isEmpty()) {
      return;
    }

    log.info("Tìm thấy {} buổi học sắp diễn ra trước {} giờ (khung [{} -> {}])",
        upcomingSessions.size(), amount, windowStart, windowEnd);

    for (TutoringSession session : upcomingSessions) {
      try {
        eventPublisher.publishEvent(SessionUpcomingReminderEvent.of(
            session.getSessionId(),
            session.getClassId(),
            session.getTutorId(),
            session.getStudentId(),
            session.getStartAt(),
            amount
        ));

        log.info("Đã gửi sự kiện nhắc lịch học trước {}h: sessionId={}, startAt={}",
            amount, session.getSessionId(), session.getStartAt());
      } catch (Exception ex) {
        log.error("Lỗi khi phát sự kiện nhắc lịch học sessionId={}: {}", session.getSessionId(), ex.getMessage(), ex);
      }
    }
  }
}

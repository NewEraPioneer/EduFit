package vn.edufit.progress.infra.job;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.edufit.progress.application.service.FeedbackReminderService;

/**
 * Quét định kỳ các buổi học đã hoàn thành nhưng chưa có phản hồi 10 giây để nhắc gia sư lúc +12h/+24h.
 * Chu kỳ mặc định 15 phút, cấu hình qua {@code edufit.progress.jobs.feedback-reminder.cron}.
 */
@Component
@RequiredArgsConstructor
public class FeedbackReminderJob {

  private final FeedbackReminderService reminderService;

  @Scheduled(cron = "${edufit.progress.jobs.feedback-reminder.cron:0 */15 * * * *}")
  public void remind() {
    reminderService.sendDueReminders();
  }
}

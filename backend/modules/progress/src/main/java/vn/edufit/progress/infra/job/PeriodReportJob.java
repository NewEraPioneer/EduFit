package vn.edufit.progress.infra.job;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.edufit.progress.application.service.PeriodReportService;

/**
 * Lập bản nháp báo cáo kỳ khi một kỳ 30 ngày kết thúc và tự gửi bản nháp đã quá 48 giờ.
 * Chu kỳ mặc định mỗi giờ, cấu hình qua {@code edufit.progress.jobs.period-report.cron}.
 */
@Component
@RequiredArgsConstructor
public class PeriodReportJob {

  private final PeriodReportService reportService;

  @Scheduled(cron = "${edufit.progress.jobs.period-report.cron:0 5 * * * *}")
  public void run() {
    reportService.generateDueDrafts();
    reportService.sendOverdueDrafts();
  }
}

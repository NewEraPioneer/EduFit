package vn.edufit.progress.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import vn.edufit.progress.domain.model.PeriodReport;

/**
 * Báo cáo kỳ 30 ngày (mục 3.13). {@code periodEnd} là ngày cuối kỳ (bao gồm). Học sinh và phụ huynh chỉ nhận báo cáo
 * đã gửi; gia sư thấy cả bản nháp để viết câu tổng kết trước {@code autoSendAt}.
 */
public record PeriodReportView(
    UUID reportId,
    UUID classId,
    int periodIndex,
    LocalDate periodStart,
    LocalDate periodEnd,
    String onTrackStatus,
    String onTrackExplanation,
    Integer progressPercentage,
    Integer progressDelta,
    int attendedCount,
    int countedCount,
    String highlight,
    String nextFocusTitle,
    List<String> achievedMilestones,
    String tutorSummary,
    String status,
    Instant autoSendAt,
    Instant sentAt,
    int version
) {
  public static PeriodReportView from(PeriodReport r) {
    PeriodReport.Snapshot s = r.getSnapshot();
    return new PeriodReportView(
        r.getReportId(),
        r.getClassId(),
        r.getPeriodIndex(),
        r.getPeriodStart(),
        r.getPeriodEnd(),
        s.onTrackStatus(),
        s.onTrackExplanation(),
        s.progressPercentage(),
        s.progressDelta(),
        s.attendedCount(),
        s.countedCount(),
        s.highlight(),
        s.nextFocusTitle(),
        s.achievedMilestones(),
        r.getTutorSummary(),
        r.getStatus().name(),
        r.getAutoSendAt(),
        r.getSentAt(),
        r.getVersion());
  }
}

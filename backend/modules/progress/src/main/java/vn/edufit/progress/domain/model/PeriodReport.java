package vn.edufit.progress.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Báo cáo kỳ 30 ngày của một lớp (mục 3.13): ảnh chụp 3 thẻ tại cuối kỳ, cột mốc đạt trong kỳ, bước tiến lớn nhất,
 * trọng tâm kỳ tới và một câu tổng kết tùy chọn của gia sư. Bản nháp tự gửi tại {@code autoSendAt} (48h sau khi tạo)
 * kể cả khi gia sư không viết thêm.
 */
public class PeriodReport {

  public static final int MAX_SUMMARY_LENGTH = 300;

  public enum Status { DRAFT, SENT }

  /** Ảnh chụp số liệu tại thời điểm tạo báo cáo; không đổi sau đó. */
  public record Snapshot(
      String onTrackStatus,
      String onTrackExplanation,
      Integer progressPercentage,
      Integer progressDelta,
      int attendedCount,
      int countedCount,
      String highlight,
      String nextFocusTitle,
      List<String> achievedMilestones
  ) {
    public Snapshot {
      Objects.requireNonNull(onTrackStatus, "onTrackStatus");
      Objects.requireNonNull(onTrackExplanation, "onTrackExplanation");
      achievedMilestones = achievedMilestones != null ? List.copyOf(achievedMilestones) : List.of();
    }
  }

  private final UUID reportId;
  private final UUID classId;
  private final int periodIndex;
  private final LocalDate periodStart;
  private final LocalDate periodEnd;
  private final Snapshot snapshot;
  private String tutorSummary;
  private Status status;
  private final Instant autoSendAt;
  private Instant sentAt;
  private final int version;
  private final Instant createdAt;

  public PeriodReport(
      UUID reportId,
      UUID classId,
      int periodIndex,
      LocalDate periodStart,
      LocalDate periodEnd,
      Snapshot snapshot,
      String tutorSummary,
      Status status,
      Instant autoSendAt,
      Instant sentAt,
      int version,
      Instant createdAt
  ) {
    this.reportId = reportId != null ? reportId : UUID.randomUUID();
    this.classId = Objects.requireNonNull(classId, "classId");
    this.periodIndex = periodIndex;
    this.periodStart = Objects.requireNonNull(periodStart, "periodStart");
    this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd");
    this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    this.tutorSummary = tutorSummary;
    this.status = Objects.requireNonNull(status, "status");
    this.autoSendAt = Objects.requireNonNull(autoSendAt, "autoSendAt");
    this.sentAt = sentAt;
    this.version = version;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  public static PeriodReport draft(UUID classId, int periodIndex, LocalDate start, LocalDate end,
      Snapshot snapshot, Instant autoSendAt, Instant now) {
    return new PeriodReport(UUID.randomUUID(), classId, periodIndex, start, end, snapshot, null,
        Status.DRAFT, autoSendAt, null, 0, now);
  }

  /** Gia sư viết/sửa câu tổng kết khi báo cáo còn là bản nháp; chuỗi rỗng là bỏ câu tổng kết. */
  public void editSummary(String summary) {
    requireDraft();
    String trimmed = summary != null ? summary.trim() : "";
    if (trimmed.length() > MAX_SUMMARY_LENGTH) {
      throw new InvalidOperationException(
          ErrorCode.VALIDATION_FAILED, "Câu tổng kết tối đa " + MAX_SUMMARY_LENGTH + " ký tự");
    }
    this.tutorSummary = trimmed.isEmpty() ? null : trimmed;
  }

  public void send(Instant now) {
    requireDraft();
    this.status = Status.SENT;
    this.sentAt = now;
  }

  public boolean isDueForAutoSend(Instant now) {
    return status == Status.DRAFT && !autoSendAt.isAfter(now);
  }

  public boolean isSent() {
    return status == Status.SENT;
  }

  private void requireDraft() {
    if (status != Status.DRAFT) {
      throw new InvalidOperationException(ErrorCode.INVALID_OPERATION, "Báo cáo kỳ đã được gửi, không thể thay đổi");
    }
  }

  public UUID getReportId() { return reportId; }
  public UUID getClassId() { return classId; }
  public int getPeriodIndex() { return periodIndex; }
  public LocalDate getPeriodStart() { return periodStart; }
  public LocalDate getPeriodEnd() { return periodEnd; }
  public Snapshot getSnapshot() { return snapshot; }
  public String getTutorSummary() { return tutorSummary; }
  public Status getStatus() { return status; }
  public Instant getAutoSendAt() { return autoSendAt; }
  public Instant getSentAt() { return sentAt; }
  public int getVersion() { return version; }
  public Instant getCreatedAt() { return createdAt; }
}

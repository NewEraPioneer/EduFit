package vn.edufit.progress.infra.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/** JPA Entity ánh xạ bảng {@code period_report}; cột mốc đạt trong kỳ lưu ở {@code period_report_achievement}. */
@Entity
@Table(name = "period_report")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PeriodReportJpaEntity {

  @Id
  @Column(name = "report_id", nullable = false, updatable = false)
  private UUID reportId;

  @Column(name = "class_id", nullable = false, updatable = false)
  private UUID classId;

  @Column(name = "period_index", nullable = false, updatable = false)
  private int periodIndex;

  @Column(name = "period_start", nullable = false, updatable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false, updatable = false)
  private LocalDate periodEnd;

  @Column(name = "on_track_status", nullable = false, updatable = false, length = 30)
  private String onTrackStatus;

  @Column(name = "on_track_explanation", nullable = false, updatable = false, length = 500)
  private String onTrackExplanation;

  @Column(name = "progress_percentage", updatable = false)
  private Short progressPercentage;

  @Column(name = "progress_delta", updatable = false)
  private Short progressDelta;

  @Column(name = "attended_count", nullable = false, updatable = false)
  private int attendedCount;

  @Column(name = "counted_count", nullable = false, updatable = false)
  private int countedCount;

  @Column(name = "highlight", updatable = false, length = 500)
  private String highlight;

  @Column(name = "next_focus_title", updatable = false, length = 200)
  private String nextFocusTitle;

  @Column(name = "tutor_summary", length = 300)
  private String tutorSummary;

  @Column(name = "status", nullable = false, length = 10)
  private String status;

  @Column(name = "auto_send_at", nullable = false, updatable = false)
  private Instant autoSendAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Version
  @Column(name = "version", nullable = false)
  private int version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @ElementCollection
  @CollectionTable(name = "period_report_achievement", joinColumns = @JoinColumn(name = "report_id"))
  @OrderColumn(name = "sort_order")
  @Column(name = "milestone_title", nullable = false, length = 200)
  @BatchSize(size = 100)
  @Builder.Default
  private List<String> achievedMilestones = new ArrayList<>();
}

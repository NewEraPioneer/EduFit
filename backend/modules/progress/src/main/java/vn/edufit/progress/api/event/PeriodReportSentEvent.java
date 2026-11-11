package vn.edufit.progress.api.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import vn.edufit.shared.domain.BaseDomainEvent;

/** Báo cáo kỳ vừa được gửi tới học sinh và phụ huynh. */
public record PeriodReportSentEvent(
    UUID eventId,
    Instant occurredAt,
    UUID reportId,
    UUID classId,
    UUID studentId,
    int periodIndex,
    LocalDate periodStart,
    LocalDate periodEnd
) implements BaseDomainEvent {

  public static PeriodReportSentEvent of(
      UUID reportId, UUID classId, UUID studentId, int periodIndex, LocalDate periodStart, LocalDate periodEnd) {
    return new PeriodReportSentEvent(
        UUID.randomUUID(), Instant.now(), reportId, classId, studentId, periodIndex, periodStart, periodEnd);
  }

  @Override
  public UUID getEventId() {
    return eventId;
  }

  @Override
  public Instant getOccurredAt() {
    return occurredAt;
  }
}

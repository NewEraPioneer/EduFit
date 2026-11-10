package vn.edufit.scheduling.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.edufit.scheduling.domain.model.SessionRescheduleProposal;
import vn.edufit.shared.exception.InvalidOperationException;

class RescheduleDeadlineTest {
  private final Instant deadline = Instant.parse("2026-11-10T10:00:00Z");

  @Test void rejectsAcceptanceAtDeadlineWithoutWaitingForSweep() {
    var proposal = SessionRescheduleProposal.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        deadline.plusSeconds(86400), deadline.plusSeconds(90000), "Đổi lịch", deadline, deadline.minusSeconds(3600));
    assertThrows(InvalidOperationException.class, () -> proposal.accept(UUID.randomUUID(), deadline));
  }

  @Test void rejectsResponseAfterDeadlineWithoutWaitingForSweep() {
    var proposal = SessionRescheduleProposal.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        deadline.plusSeconds(86400), deadline.plusSeconds(90000), "Đổi lịch", deadline, deadline.minusSeconds(3600));
    assertThrows(InvalidOperationException.class, () -> proposal.reject(UUID.randomUUID(), "Không thể", deadline.plusSeconds(1)));
  }
}

package vn.edufit.payment.application;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.payment.domain.SessionCharge;

public final class PaymentViews {
  private PaymentViews() {}

  public record Wallet(long available, long held) {}

  public record Charge(
      UUID sessionId,
      UUID classId,
      UUID studentId,
      UUID payerId,
      UUID tutorUserId,
      int commissionBps,
      SessionCharge state) {}

  public record Session(
      UUID sessionId,
      UUID classId,
      UUID studentId,
      String tutorName,
      String sessionStatus,
      Instant startAt,
      Instant endAt,
      long amount,
      int commissionBps,
      UUID payerId,
      String status,
      boolean learnerAccepted,
      boolean tutorAccepted,
      boolean autoAccepted,
      Instant disputeDeadline,
      String disputeReason,
      Long learnerAmount,
      Long tutorAmount,
      String learnerReport,
      String tutorReport,
      long settledAmount,
      boolean canFund,
      boolean canActAsLearner,
      boolean canActAsTutor,
      Instant reviewDeadline, boolean canSubmitProposal, boolean isPayer,
      String disputeCategory, String disputeEvidence, UUID disputeReporterId,
      String resolutionNote, Instant resolvedAt, java.util.List<java.util.Map<String, Object>> comments) {
    public Session(UUID sessionId,
      UUID classId,
      UUID studentId,
      String tutorName,
      String sessionStatus,
      Instant startAt,
      Instant endAt,
      long amount,
      int commissionBps,
      UUID payerId,
      String status,
      boolean learnerAccepted,
      boolean tutorAccepted,
      boolean autoAccepted,
      Instant disputeDeadline,
      String disputeReason,
      Long learnerAmount,
      Long tutorAmount,
      String learnerReport,
      String tutorReport,
      long settledAmount,
      boolean canFund,
      boolean canActAsLearner,
      boolean canActAsTutor) {
      this(sessionId, classId, studentId, tutorName, sessionStatus, startAt, endAt, amount, commissionBps, payerId, status, learnerAccepted, tutorAccepted, autoAccepted, disputeDeadline, disputeReason, learnerAmount, tutorAmount, learnerReport, tutorReport, settledAmount, canFund, canActAsLearner, canActAsTutor, null, false, false, null, null, null, null, null, java.util.List.of());
    }
  }
}

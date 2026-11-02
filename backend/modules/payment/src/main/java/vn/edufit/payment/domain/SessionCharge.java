package vn.edufit.payment.domain;

import java.time.Instant;

/** Financial state only. Repository transactions serialize all state and wallet movements. */
public final class SessionCharge {
  public static final long CONFIRMATION_SECONDS = 86400;
  public static final long DISPUTE_SECONDS = 172800;
  public final long amount;
  public Instant endAt;
  public Instant reviewStartedAt;
  public String disputeCategory = "OTHER", disputeEvidence, resolutionNote;
  public java.util.UUID disputeReporterId;
  public Instant resolvedAt;
  public String status = "FUNDED";
  public boolean learnerAccepted, tutorAccepted, autoAccepted;
  public String disputeReason, learnerReport, tutorReport;
  public Instant disputeOpenedAt;
  public Long learnerAmount, tutorAmount;
  public long settledAmount;

  public SessionCharge(long amount, Instant endAt) {
    require(amount > 0 && amount <= 100_000_000, "Học phí không hợp lệ");
    this.amount = amount;
    this.endAt = endAt;
  }

  public boolean terminal() {
    return status.equals("SETTLED") || status.equals("REFUNDED") || status.equals("EXPIRED");
  }

  public boolean awaitingConfirmation() {
    return status.equals("FUNDED") || status.equals("AWAITING_CONFIRMATION");
  }

  public void confirm(boolean tutor, Instant now, boolean completed) {
    if (terminal()) return;
    require(awaitingConfirmation(), "Khoản học phí đang tranh chấp hoặc chờ Admin");
    require(completed && !now.isBefore(endAt), "Buổi học chưa hoàn thành");
    status = "AWAITING_CONFIRMATION";
    if (tutor) tutorAccepted = true;
    else learnerAccepted = true;
  }

  public void beginReview(Instant now) {
    if (awaitingConfirmation() && reviewStartedAt == null) {
      reviewStartedAt = now.isAfter(endAt) ? now : endAt;
      status = "AWAITING_CONFIRMATION";
    }
  }

  public Instant reviewDeadline() {
    return reviewStartedAt == null ? null : reviewStartedAt.plusSeconds(CONFIRMATION_SECONDS);
  }

  public void tick(Instant now, boolean completed) {
    if (status.equals("DISPUTED") && !now.isBefore(disputeOpenedAt.plusSeconds(DISPUTE_SECONDS))) {
      status = "ADMIN_REVIEW";
      return;
    }
    if (awaitingConfirmation() && completed && !now.isBefore(endAt)) {
      status = "AWAITING_CONFIRMATION";
      if (reviewDeadline() != null && !now.isBefore(reviewDeadline())) {
        autoAccepted = true;
        finish(amount);
      }
    }
  }

  public void dispute(String reason, Instant now) {
    require(
        awaitingConfirmation() && !now.isBefore(endAt)
            && (reviewDeadline() == null || now.isBefore(reviewDeadline())),
        "Chỉ có thể khiếu nại trong 24 giờ sau buổi học, trước khi tất toán");
    require(
        reason != null && !reason.isBlank() && reason.length() <= 2000,
        "Cần mô tả tranh chấp tối đa 2000 ký tự");
    status = "DISPUTED";
    disputeReason = reason.trim();
    disputeOpenedAt = now;
  }

  public void report(boolean tutor, long proposal, String text, Instant now) {
    require(
        status.equals("DISPUTED") && now.isBefore(disputeOpenedAt.plusSeconds(DISPUTE_SECONDS)),
        "Đã hết hạn gửi báo cáo hoặc đang chờ Admin");
    require(proposal >= 0 && proposal <= amount, "Số tiền đề nghị phải nằm trong học phí đã giữ");
    require(
        text != null && !text.isBlank() && text.length() <= 4000,
        "Cần báo cáo giải quyết tối đa 4000 ký tự");
    if (tutor) {
      tutorAmount = proposal;
      tutorReport = text.trim();
    } else {
      learnerAmount = proposal;
      learnerReport = text.trim();
    }
    if (learnerAmount != null && learnerAmount.equals(tutorAmount)) status = "AWAITING_ADMIN";
  }

  public void approve() {
    require(
        status.equals("AWAITING_ADMIN")
            && learnerAmount != null
            && learnerAmount.equals(tutorAmount),
        "Cần báo cáo thống nhất của hai bên");
    finish(learnerAmount);
  }

  public void resolve(long value) {
    require(status.equals("AWAITING_ADMIN") || status.equals("ADMIN_REVIEW"), "Hồ sơ chưa chuyển Admin");
    require(value >= 0 && value <= amount, "Số tiền phân xử phải nằm trong khoản học phí đang giữ");
    finish(value);
  }

  public void refund() {
    require(!terminal(), "Khoản học phí đã kết thúc");
    finish(0);
  }

  private void finish(long value) {
    settledAmount = value;
    status = value == 0 ? "REFUNDED" : "SETTLED";
  }

  private static void require(boolean valid, String message) {
    if (!valid) throw new IllegalStateException(message);
  }
}

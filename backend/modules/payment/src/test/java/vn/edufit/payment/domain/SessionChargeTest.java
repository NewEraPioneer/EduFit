package vn.edufit.payment.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionChargeTest {
  final Instant end = Instant.parse("2026-10-21T12:00:00Z");

  SessionCharge charge() {
    var charge = new SessionCharge(300_000, end);
    charge.beginReview(end);
    return charge;
  }

  @Test
  void noAutomaticAcceptanceAtOneHourAndSettlementAtExactly24HoursWithoutEitherConfirmation() {
    var c = charge();
    c.tick(end.plusSeconds(3599), true);
    assertFalse(c.learnerAccepted);
    c.tick(end.plusSeconds(3600), true);
    assertFalse(c.learnerAccepted);
    assertFalse(c.terminal());
    c.tick(end.plusSeconds(86399), true);
    assertFalse(c.terminal());
    c.tick(end.plusSeconds(86400), true);
    assertEquals("SETTLED", c.status);
    assertEquals(300_000, c.settledAmount);
  }

  @Test
  void noChargeBeforeEndOrForUncompletedSession() {
    var c = charge();
    assertThrows(RuntimeException.class, () -> c.confirm(true, end.minusSeconds(1), true));
    c.tick(end.plusSeconds(90000), false);
    assertFalse(c.learnerAccepted);
    assertFalse(c.terminal());
  }

  @Test
  void bothAttendanceConfirmationsKeepTheFullReviewWindow() {
    var c = charge();
    c.confirm(true, end, true);
    c.confirm(false, end, true);
    assertFalse(c.terminal());
    c.dispute("Vẫn có quyền báo vấn đề", end.plusSeconds(1));
    assertEquals("DISPUTED", c.status);
  }

  @Test
  void disputeStopsAutoSettlementAndExpiresAtExactly48HoursAfterOpening() {
    var c = charge();
    c.confirm(true, end, true);
    c.dispute("Thiếu thời lượng", end);
    c.tick(end.plusSeconds(3600), true);
    assertFalse(c.learnerAccepted);
    c.tick(end.plusSeconds(86400), true);
    assertEquals("DISPUTED", c.status);
    c.tick(end.plusSeconds(172799), true);
    assertEquals("DISPUTED", c.status);
    c.tick(end.plusSeconds(172800), true);
    assertEquals("ADMIN_REVIEW", c.status);
    assertEquals(0, c.settledAmount);
    assertThrows(
        RuntimeException.class, () -> c.report(false, 100_000, "Đồng ý", end.plusSeconds(172800)));
  }

  @Test
  void onlyMatchingReportsBeforeDeadlineReachAdmin() {
    var c = charge();
    c.dispute("Vấn đề", end);
    c.report(false, 100_000, "Đề nghị", end);
    c.report(true, 200_000, "Phản hồi", end);
    assertEquals("DISPUTED", c.status);
    c.report(true, 100_000, "Đã thống nhất", end.plusSeconds(172799));
    assertEquals("AWAITING_ADMIN", c.status);
    c.tick(end.plusSeconds(200000), true);
    assertFalse(c.terminal());
    c.approve();
    assertEquals(100_000, c.settledAmount);
  }

  @Test
  void invalidAmountsCannotBeSubmittedOrApproved() {
    var c = charge();
    c.dispute("Vấn đề", end);
    assertThrows(RuntimeException.class, () -> c.report(true, 300_001, "Sai", end));
    assertThrows(RuntimeException.class, c::approve);
  }

  @Test
  void zeroAgreementRefundsEntireReservation() {
    var c = charge();
    c.dispute("Không học", end);
    c.report(false, 0, "Hoàn đủ", end);
    c.report(true, 0, "Đồng ý", end);
    c.approve();
    assertEquals("REFUNDED", c.status);
    assertEquals(0, c.settledAmount);
  }

  @Test
  void complaintAtTheLastSecondFreezesMoneyFor48HoursAndLateComplaintsAreRejected() {
    var c = charge();
    c.dispute("Cần kiểm tra", end.plusSeconds(86399));
    c.tick(end.plusSeconds(86400), true);
    assertEquals("DISPUTED", c.status);
    c.tick(end.plusSeconds(86399 + 172799), true);
    assertEquals("DISPUTED", c.status);
    c.tick(end.plusSeconds(86399 + 172800), true);
    assertEquals("ADMIN_REVIEW", c.status);
    assertThrows(RuntimeException.class, () -> charge().dispute("Quá hạn", end.plusSeconds(86400)));
  }
  @Test
  void lateCompletionStartsAFullWindowOnceAndAdminCanResolveAnUnmatchedDispute() {
    var c = new SessionCharge(300000, end);
    c.beginReview(end.plusSeconds(90000));
    c.beginReview(end.plusSeconds(100000));
    c.tick(end.plusSeconds(90000 + 86399), true);
    assertFalse(c.terminal());
    c.dispute("Vấn đề", end.plusSeconds(90001));
    c.report(false, 100000, "Đề nghị", end.plusSeconds(90002));
    c.tick(end.plusSeconds(90001 + 172800), true);
    assertEquals("ADMIN_REVIEW", c.status);
    assertFalse(c.terminal());
    assertThrows(RuntimeException.class, () -> c.resolve(300001));
    c.resolve(150000);
    assertEquals(150000, c.settledAmount);
  }
}

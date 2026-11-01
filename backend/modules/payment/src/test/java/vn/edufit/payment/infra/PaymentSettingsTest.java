package vn.edufit.payment.infra;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import vn.edufit.payment.TestPaymentSettings;
class PaymentSettingsTest {
 @Test void blankOrPlaceholderKeysNeverEnableLivePayments() {
  var empty=TestPaymentSettings.builder().build(); assertFalse(empty.payos()); assertFalse(empty.payout());
  assertFalse(TestPaymentSettings.builder().payos("...","...","...").bankAccount("12345678").build().payos());
  assertTrue(TestPaymentSettings.builder().payos("client","api","checksum").bankAccount("12345678").build().payos());
  assertFalse(TestPaymentSettings.builder().payout(false,"payout-client","payout-api","payout-checksum").build().payout());
  var live=TestPaymentSettings.builder().payos("client","api","checksum").bankAccount("12345678")
      .payout(true,"payout-client","payout-api","payout-checksum").build();
  assertTrue(live.payout());
  String status=live.readiness().toString();assertFalse(status.contains("payout-api"));assertFalse(status.contains("checksum"));
 }
}

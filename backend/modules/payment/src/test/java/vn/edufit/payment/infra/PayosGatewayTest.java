package vn.edufit.payment.infra;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PayosGatewayTest {
  PaymentSettings config() {
    return vn.edufit.payment.TestPaymentSettings.builder()
        .payos("client", "api", "test-checksum")
        .bankAccount("12345678")
        .build();
  }

  String payload(Map<String, Object> data) throws Exception {
    return new ObjectMapper()
        .writeValueAsString(
            Map.of(
                "success",
                true,
                "data",
                data,
                "signature",
                PayosGateway.sign(PayosGateway.canonical(new TreeMap<>(data)), "test-checksum")));
  }

  Map<String, Object> data() {
    return new HashMap<>(
        Map.of(
            "orderCode",
            42,
            "amount",
            100000,
            "reference",
            "BANK123",
            "accountNumber",
            "12345678",
            "paymentLinkId",
            "link42",
            "currency",
            "VND",
            "code",
            "00"));
  }

  @Test
  void authenticReceiptHasOrderAmountAndReference() throws Exception {
    var r = new PayosGateway(config(), new PayosTransport()).verify(payload(data()));
    assertEquals(42, r.orderCode());
    assertEquals(100000, r.amount());
    assertEquals("BANK123", r.reference());
  }

  @Test
  void alteredAmountCannotCreditWallet() throws Exception {
    var text = payload(data()).replace("100000", "200000");
    assertThrows(
        vn.edufit.shared.exception.InvalidOperationException.class,
        () -> new PayosGateway(config(), new PayosTransport()).verify(text));
  }

  @Test
  void missingSignatureCannotCreditWallet() {
    assertThrows(
        vn.edufit.shared.exception.InvalidOperationException.class,
        () -> new PayosGateway(config(), new PayosTransport()).verify("{\"success\":true,\"data\":{}}"));
  }

  @Test
  void failedOrFractionalReceiptCannotCreditWallet() throws Exception {
    var d = data();
    d.put("code", "01");
    var failed = payload(d);
    assertThrows(
        vn.edufit.shared.exception.InvalidOperationException.class,
        () -> new PayosGateway(config(), new PayosTransport()).verify(failed));
    d = data();
    d.put("amount", 100000.5);
    var fractional = payload(d);
    assertThrows(
        vn.edufit.shared.exception.InvalidOperationException.class,
        () -> new PayosGateway(config(), new PayosTransport()).verify(fractional));
  }

  Map<String, Object> checkoutData() {
    return new HashMap<>(Map.of(
        "checkoutUrl", "https://pay.payos.vn/web/link42",
        "paymentLinkId", "link42",
        "orderCode", 42L,
        "amount", 100000L,
        "currency", "VND",
        "accountNumber", "VQR123456789"));
  }

  PayosGateway checkoutGateway(Map<String, Object> data) {
    var transport = mock(PayosTransport.class);
    when(transport.call(eq("POST"), eq("/v2/payment-requests"), anyMap(),
        anyString(), anyString(), anyString(), eq(false), isNull()))
        .thenReturn(new ObjectMapper().valueToTree(data));
    return new PayosGateway(config(), transport);
  }

  @Test
  void signedCheckoutCanUseAnOrderSpecificVirtualAccount() {
    var checkout = checkoutGateway(checkoutData())
        .create(42, 100000, "/student/payments", Instant.parse("2026-10-24T17:00:00Z"));
    assertEquals("VQR123456789", checkout.accountNumber());
    assertEquals("link42", checkout.paymentLinkId());
  }

  @Test
  void checkoutStillRejectsWrongOrderAmountCurrencyOrDestinationUrl() {
    for (var change : List.of(
        Map.<String, Object>of("orderCode", 43L),
        Map.<String, Object>of("amount", 99999L),
        Map.<String, Object>of("currency", "USD"),
        Map.<String, Object>of("accountNumber", ""),
        Map.<String, Object>of("checkoutUrl", "https://pay.payos.vn.evil.example/web/link42"),
        Map.<String, Object>of("checkoutUrl", "https://attacker@pay.payos.vn/web/link42"))) {
      var data = checkoutData();
      data.putAll(change);
      assertThrows(RuntimeException.class, () -> checkoutGateway(data)
          .create(42, 100000, "/student/payments", Instant.parse("2026-10-24T17:00:00Z")));
    }
  }

  @Test
  void webhookRetainsMainAndVirtualAccountForMatchingTheStoredCheckout() throws Exception {
    var data = data();
    data.put("virtualAccountNumber", "VQR123456789");
    var receipt = new PayosGateway(config(), new PayosTransport()).verify(payload(data));
    assertTrue(receipt.matchesAccount("VQR123456789"));
    assertTrue(receipt.matchesAccount("12345678"));
    assertFalse(receipt.matchesAccount("VQR_DIFFERENT"));
    assertFalse(receipt.matchesAccount(""));
  }
}

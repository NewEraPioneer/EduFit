package vn.edufit.payment.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Cấu hình PayOS / chuyển khoản thủ công / payout. Bất biến sau khi khởi tạo; khóa bí mật không lộ qua {@link #readiness()}. */
@Component
public class PaymentSettings {
  private final String clientId;
  private final String apiKey;
  private final String checksumKey;
  private final String bankBin;
  private final String bankAccount;
  private final String bankHolder;
  private final String frontendUrl;
  private final boolean payoutEnabled;
  private final String payoutClientId;
  private final String payoutApiKey;
  private final String payoutChecksumKey;

  public PaymentSettings(
      @Value("${PAYOS_CLIENT_ID:}") String clientId,
      @Value("${PAYOS_API_KEY:}") String apiKey,
      @Value("${PAYOS_CHECKSUM_KEY:}") String checksumKey,
      @Value("${PAYMENT_BANK_BIN:}") String bankBin,
      @Value("${PAYMENT_BANK_ACCOUNT:}") String bankAccount,
      @Value("${PAYMENT_BANK_HOLDER:}") String bankHolder,
      @Value("${PAYMENT_FRONTEND_URL:http://localhost:3000}") String frontendUrl,
      @Value("${PAYOS_PAYOUT_ENABLED:false}") boolean payoutEnabled,
      @Value("${PAYOS_PAYOUT_CLIENT_ID:}") String payoutClientId,
      @Value("${PAYOS_PAYOUT_API_KEY:}") String payoutApiKey,
      @Value("${PAYOS_PAYOUT_CHECKSUM_KEY:}") String payoutChecksumKey) {
    this.clientId = orEmpty(clientId);
    this.apiKey = orEmpty(apiKey);
    this.checksumKey = orEmpty(checksumKey);
    this.bankBin = orEmpty(bankBin);
    this.bankAccount = orEmpty(bankAccount);
    this.bankHolder = orEmpty(bankHolder);
    this.frontendUrl = orEmpty(frontendUrl);
    this.payoutEnabled = payoutEnabled;
    this.payoutClientId = orEmpty(payoutClientId);
    this.payoutApiKey = orEmpty(payoutApiKey);
    this.payoutChecksumKey = orEmpty(payoutChecksumKey);
  }

  private static String orEmpty(String value) { return value == null ? "" : value; }

  public String clientId() { return clientId; }
  public String apiKey() { return apiKey; }
  public String checksumKey() { return checksumKey; }
  public String bankBin() { return bankBin; }
  public String bankAccount() { return bankAccount; }
  public String bankHolder() { return bankHolder; }
  public String frontendUrl() { return frontendUrl; }
  public boolean payoutEnabled() { return payoutEnabled; }
  public String payoutClientId() { return payoutClientId; }
  public String payoutApiKey() { return payoutApiKey; }
  public String payoutChecksumKey() { return payoutChecksumKey; }

  public boolean payout() {
    return payoutEnabled && configured(payoutClientId) && configured(payoutApiKey) && configured(payoutChecksumKey);
  }
  private boolean configured(String value) { return value != null && !value.isBlank() && !value.equals("..."); }
  public java.util.Map<String,Object> readiness() {
    return java.util.Map.of("topupReady", payos(), "manualReady", manual(), "payoutReady", payout(),
        "payoutEnabled", payoutEnabled, "webhookPath", "/api/v1/payment/webhooks/payos",
        "frontendUrl", frontendUrl);
  }
  public boolean payos() {
    return configured(clientId)
        && configured(apiKey)
        && configured(checksumKey)
        && bankAccount.matches("[0-9]{5,34}");
  }

  public boolean manual() {
    return bankBin.matches("[0-9]{6}")
        && bankAccount.matches("[0-9]{5,34}")
        && !bankHolder.isBlank();
  }
}

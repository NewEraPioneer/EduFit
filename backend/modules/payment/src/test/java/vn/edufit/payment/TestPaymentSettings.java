package vn.edufit.payment;

import vn.edufit.payment.infra.PaymentSettings;

/** Dựng {@link PaymentSettings} bất biến cho test; mặc định giống môi trường chưa cấu hình. */
public final class TestPaymentSettings {
  private String clientId = "";
  private String apiKey = "";
  private String checksumKey = "";
  private String bankBin = "";
  private String bankAccount = "";
  private String bankHolder = "";
  private String frontendUrl = "http://localhost:3000";
  private boolean payoutEnabled;
  private String payoutClientId = "";
  private String payoutApiKey = "";
  private String payoutChecksumKey = "";

  private TestPaymentSettings() {}

  public static TestPaymentSettings builder() { return new TestPaymentSettings(); }

  public TestPaymentSettings payos(String clientId, String apiKey, String checksumKey) {
    this.clientId = clientId;
    this.apiKey = apiKey;
    this.checksumKey = checksumKey;
    return this;
  }

  public TestPaymentSettings bankAccount(String bankAccount) {
    this.bankAccount = bankAccount;
    return this;
  }

  public TestPaymentSettings manualBank(String bankBin, String bankHolder) {
    this.bankBin = bankBin;
    this.bankHolder = bankHolder;
    return this;
  }

  public TestPaymentSettings payout(boolean enabled, String clientId, String apiKey, String checksumKey) {
    this.payoutEnabled = enabled;
    this.payoutClientId = clientId;
    this.payoutApiKey = apiKey;
    this.payoutChecksumKey = checksumKey;
    return this;
  }

  public PaymentSettings build() {
    return new PaymentSettings(clientId, apiKey, checksumKey, bankBin, bankAccount, bankHolder,
        frontendUrl, payoutEnabled, payoutClientId, payoutApiKey, payoutChecksumKey);
  }
}

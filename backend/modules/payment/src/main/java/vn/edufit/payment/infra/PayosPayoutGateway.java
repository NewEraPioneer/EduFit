package vn.edufit.payment.infra;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.edufit.shared.exception.InvalidOperationException;

@Component
public class PayosPayoutGateway {
  private final PaymentSettings settings;
  private final PayosTransport transport;

  public PayosPayoutGateway(PaymentSettings settings, PayosTransport transport) {
    this.settings = settings;
    this.transport = transport;
  }

  public JsonNode create(Map<String, Object> withdrawal) {
    String reference = withdrawal.get("id").toString();
    return request(
        "POST",
        "/v1/payouts",
        Map.of(
            "referenceId",
            reference,
            "amount",
            withdrawal.get("amount"),
            "description",
            "EduFit payout",
            "toBin",
            withdrawal.get("bank_bin"),
            "toAccountNumber",
            withdrawal.get("bank_account")),
        reference);
  }

  public JsonNode lookup(Map<String, Object> withdrawal) {
    Object providerId = withdrawal.get("provider_id");
    if (providerId != null)
      return request("GET", "/v1/payouts/" + path(providerId.toString()), null, null);
    var data =
        request(
            "GET",
            "/v1/payouts?referenceId=" + withdrawal.get("id") + "&limit=2&offset=0",
            null,
            null);
    var list = data.path("payouts");
    if (!list.isArray() || list.size() != 1)
      throw new InvalidOperationException(
          "Chưa tìm thấy kết quả chi tiền duy nhất; tiếp tục giữ tiền để đối soát");
    return list.get(0);
  }

  private JsonNode request(
      String method, String path, Map<String, Object> body, String idempotency) {
    if (!settings.payout())
      throw new InvalidOperationException("Kênh chi payOS chưa được cấu hình/bật");
    return transport.call(
        method,
        path,
        body,
        settings.payoutClientId(),
        settings.payoutApiKey(),
        settings.payoutChecksumKey(),
        true,
        idempotency);
  }

  private String path(String value) {
    if (!value.matches("[A-Za-z0-9_-]{1,150}"))
      throw new InvalidOperationException("Mã lệnh chi không hợp lệ");
    return value;
  }
}

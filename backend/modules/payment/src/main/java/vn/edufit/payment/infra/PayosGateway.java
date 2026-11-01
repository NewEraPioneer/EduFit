package vn.edufit.payment.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import vn.edufit.shared.exception.InvalidOperationException;

@Component
public class PayosGateway {
  private final PaymentSettings settings;
  private final PayosTransport transport;
  private final ObjectMapper json = new ObjectMapper();

  @org.springframework.beans.factory.annotation.Autowired
  public PayosGateway(PaymentSettings settings, PayosTransport transport) {
    this.settings = settings;
    this.transport = transport;
  }

  public record Checkout(String url, String id, String accountNumber) {}

  public record Receipt(
      long orderCode,
      long amount,
      String reference,
      String account,
      String paymentLinkId,
      String currency,
      String virtualAccount) {
    public Receipt(long orderCode, long amount, String reference, String account,
        String paymentLinkId, String currency) {
      this(orderCode, amount, reference, account, paymentLinkId, currency, "");
    }

    public boolean matchesAccount(String expected) {
      return matchesReceivingAccount(expected, account, virtualAccount);
    }
  }

  public static boolean matchesReceivingAccount(String expected, String account, String virtualAccount) {
    return expected != null && !expected.isBlank()
        && (expected.equals(account) || expected.equals(virtualAccount));
  }

  public Checkout create(long order, long amount, String returnPath, Instant expires) {
    if (!settings.payos()) throw new InvalidOperationException("Chưa cấu hình payOS");
    String base = settings.frontendUrl().replaceAll("/+$", "");
    String callback = base + returnPath;
    String description = "EF" + Long.toString(order, 36).toUpperCase(Locale.ROOT);
    var data = new TreeMap<String, Object>();
    data.put("amount", amount);
    data.put("cancelUrl", callback);
    data.put("description", description);
    data.put("orderCode", order);
    data.put("returnUrl", callback);
    String signature = sign(canonical(data), settings.checksumKey());
    data.put("signature", signature);
    data.put("expiredAt", expires.getEpochSecond());
    var result =
        transport.call(
            "POST",
            "/v2/payment-requests",
            data,
            settings.clientId(),
            settings.apiKey(),
            settings.checksumKey(),
            false,
            null);
    String url = result.path("checkoutUrl").asText(), id = result.path("paymentLinkId").asText();
    URI checkout;
    try {
      checkout = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new InvalidOperationException("URL thanh toán payOS không hợp lệ");
    }
    if (!"https".equalsIgnoreCase(checkout.getScheme())
        || !"pay.payos.vn".equalsIgnoreCase(checkout.getHost())
        || checkout.getUserInfo() != null
        || checkout.getPort() != -1 && checkout.getPort() != 443)
      throw new InvalidOperationException("URL thanh toán payOS không hợp lệ");
    if (!id.matches("[A-Za-z0-9_-]{1,150}"))
      throw new InvalidOperationException("payOS không trả mã link thanh toán hợp lệ");
    if (!result.path("orderCode").isIntegralNumber()
        || !result.path("orderCode").canConvertToLong()
        || result.path("orderCode").longValue() != order
        || !result.path("amount").isIntegralNumber()
        || !result.path("amount").canConvertToLong()
        || result.path("amount").longValue() != amount
        || !"VND".equals(result.path("currency").asText()))
      throw new InvalidOperationException("Checkout payOS không khớp mã yêu cầu/số tiền/VND");
    // A signed checkout may contain an order-specific virtual account, not the merchant's bank account.
    String account = result.path("accountNumber").asText();
    if (!account.matches("[A-Za-z0-9]{1,100}"))
      throw new InvalidOperationException("payOS không trả tài khoản nhận hợp lệ cho yêu cầu nạp");
    return new Checkout(url, id, account);
  }

  public JsonNode lookup(long order) {
    if (!settings.payos()) throw new InvalidOperationException("Chưa cấu hình payOS");
    return transport.call(
        "GET",
        "/v2/payment-requests/" + order,
        null,
        settings.clientId(),
        settings.apiKey(),
        settings.checksumKey(),
        false,
        null);
  }

  public Receipt verify(String payload) {
    if (!settings.payos()) throw new InvalidOperationException("Chưa cấu hình payOS");
    if (payload == null || payload.length() > 65536)
      throw new InvalidOperationException("Webhook quá lớn");
    try {
      JsonNode body = json.readTree(payload), data = body.path("data");
      if (!data.isObject()) throw new InvalidOperationException("Webhook không hợp lệ");
      var values = new TreeMap<String, Object>();
      data.fields()
          .forEachRemaining(
              e -> {
                if (e.getValue().isContainerNode())
                  throw new InvalidOperationException("Webhook không hỗ trợ dữ liệu lồng");
                values.put(e.getKey(), e.getValue().isNull() ? "" : e.getValue().asText());
              });
      String received = body.path("signature").asText();
      String expected = sign(canonical(values), settings.checksumKey());
      if (!received.matches("[0-9a-fA-F]{64}")
          || !MessageDigest.isEqual(
              expected.getBytes(StandardCharsets.UTF_8),
              received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)))
        throw new InvalidOperationException("Chữ ký webhook không hợp lệ");
      if (!"00".equals(data.path("code").asText()) || !body.path("success").asBoolean())
        throw new InvalidOperationException("Giao dịch chưa thành công");
      if (!data.path("orderCode").isIntegralNumber()
          || !data.path("amount").isIntegralNumber()
          || !data.path("orderCode").canConvertToLong()
          || !data.path("amount").canConvertToLong())
        throw new InvalidOperationException("Số tiền/mã đơn không hợp lệ");
      String reference = data.path("reference").asText();
      if (reference.isBlank() || reference.length() > 200)
        throw new InvalidOperationException("Thiếu mã giao dịch");
      return new Receipt(
          data.path("orderCode").longValue(),
          data.path("amount").longValue(),
          reference,
          data.path("accountNumber").asText(),
          data.path("paymentLinkId").asText(),
          data.path("currency").asText(),
          data.path("virtualAccountNumber").asText());
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new InvalidOperationException("Webhook không phải JSON hợp lệ");
    }
  }

  public static String canonical(SortedMap<String, ?> data) {
    return data.entrySet().stream()
        .map(e -> e.getKey() + "=" + (e.getValue() == null ? "" : e.getValue()))
        .collect(java.util.stream.Collectors.joining("&"));
  }

  public static String sign(String value, String key) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("Không khởi tạo được HMAC", e);
    }
  }
}

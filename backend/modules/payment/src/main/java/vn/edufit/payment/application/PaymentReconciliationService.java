package vn.edufit.payment.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edufit.payment.infra.*;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.*;

@Service
public class PaymentReconciliationService {
  private final PaymentStore store;
  private final PaymentAccess access;
  private final PaymentSettings settings;
  private final PayosGateway gateway;
  private final PaymentTime time;

  public PaymentReconciliationService(
      PaymentStore store,
      PaymentAccess access,
      PaymentSettings settings,
      PayosGateway gateway,
      PaymentTime time) {
    this.store = store;
    this.access = access;
    this.settings = settings;
    this.gateway = gateway;
    this.time = time;
  }

  public Map<String, Object> refresh(CurrentUser actor, UUID id) {
    access.authenticated(actor);
    var t = store.topup(id);
    if (!actor.getUserId().equals(t.get("user_id")) && !actor.hasRole("ADMIN"))
      throw new ForbiddenOperationException("Không được xem khoản nạp này");
    reconcile(id);
    return store.topup(id);
  }

  public void reconcile(UUID id) {
    if (!settings.payos()) return;
    var t = store.locked(() -> store.claimTopup(id, time.instant()) ? store.topup(id) : null);
    if (t == null) return;
    long order = ((Number) t.get("order_code")).longValue();
    JsonNode data;
    try {
      data = gateway.lookup(order);
    } catch (PayosRejectedException e) {
      // payOS không có mã này: server sập trước khi link được tạo. Hết hạn thì không còn QR nào
      // nhận tiền được, đóng khoản nạp. Chưa hết hạn thì có thể yêu cầu tạo link vẫn đang chạy.
      if (PayosRejectedException.NOT_FOUND.equals(e.code()) && expired(t))
        store.locked(() -> store.cancelTopup(id));
      if (!PayosRejectedException.NOT_FOUND.equals(e.code())) throw e;
      return;
    }
    long amount = ((Number) t.get("amount")).longValue();
    if (!data.path("orderCode").isIntegralNumber()
        || data.path("orderCode").longValue() != order
        || !data.path("amount").isIntegralNumber()
        || data.path("amount").longValue() != amount)
      throw new InvalidOperationException("Kết quả payOS không khớp khoản nạp");
    String link = data.path("id").asText();
    if (t.get("provider_id") != null && !link.equals(t.get("provider_id")))
      throw new InvalidOperationException("Mã thanh toán không khớp");
    // Chưa lưu được link (sập giữa chừng): chỉ nhận đơn payOS tạo sau khoản nạp này, để một đơn cũ
    // trùng mã từ CSDL trước không bao giờ được cộng vào khoản nạp mới.
    if (t.get("provider_id") == null && !createdAfter(data.path("createdAt").asText(), t))
      throw new InvalidOperationException("Đơn payOS cũ hơn khoản nạp; cần đối soát thủ công");
    String state = data.path("status").asText();
    if ("EXPIRED".equals(state) || "CANCELLED".equals(state)) {
      if (!data.path("amountPaid").isIntegralNumber() || data.path("amountPaid").longValue() != 0)
        throw new InvalidOperationException("Link đã đóng nhưng có tiền chuyển vào; cần đối soát");
      store.locked(() -> store.cancelTopup(id));
      return;
    }
    if (!"PAID".equals(state)) return;
    if (!data.path("amountPaid").isIntegralNumber()
        || data.path("amountPaid").longValue() != amount)
      throw new InvalidOperationException("Số tiền đã nhận chưa khớp; cần đối soát");
    var transactions = data.path("transactions");
    if (!transactions.isArray() || transactions.size() != 1)
      throw new InvalidOperationException("Cần đối soát các khoản chuyển nhiều lần");
    var receipt = transactions.get(0);
    String expectedAccount = t.get("provider_account_number") instanceof String account
        ? account : settings.bankAccount();
    if (!PayosGateway.matchesReceivingAccount(expectedAccount,
            receipt.path("accountNumber").asText(), receipt.path("virtualAccountNumber").asText())
        || !receipt.path("amount").isIntegralNumber()
        || receipt.path("amount").longValue() != amount
        || receipt.path("reference").asText().isBlank()
        || receipt.path("reference").asText().length() > 200)
      throw new InvalidOperationException("Giao dịch ngân hàng không khớp yêu cầu nạp");
    if (link.isBlank() || link.length() > 150)
      throw new InvalidOperationException("Mã thanh toán không khớp");
    store.locked(
        () -> {
          var current = store.topup(id);
          if (current.get("provider_id") != null && !link.equals(current.get("provider_id")))
            throw new InvalidOperationException("Mã thanh toán đã đổi");
          String currentAccount = current.get("provider_account_number") instanceof String account
              ? account : settings.bankAccount();
          if (!PayosGateway.matchesReceivingAccount(currentAccount,
              receipt.path("accountNumber").asText(), receipt.path("virtualAccountNumber").asText()))
            throw new InvalidOperationException("Tài khoản nhận của yêu cầu nạp đã đổi");
          if (current.get("provider_id") == null) store.checkout(id, null, link);
          store.creditTopup(id, receipt.path("reference").asText(), null);
          return null;
        });
  }

  private boolean expired(Map<String, Object> topup) {
    return topup.get("expires_at") instanceof java.sql.Timestamp expires
        && !time.instant().isBefore(expires.toInstant());
  }

  /** payOS ghi createdAt theo giờ máy chủ của họ; cho lệch đồng hồ tối đa 5 phút. */
  private static boolean createdAfter(String providerCreatedAt, Map<String, Object> topup) {
    if (!(topup.get("created_at") instanceof java.sql.Timestamp created)) return false;
    try {
      return !java.time.OffsetDateTime.parse(providerCreatedAt).toInstant()
          .isBefore(created.toInstant().minusSeconds(300));
    } catch (java.time.DateTimeException e) {
      return false;
    }
  }

  public void process() {
    if (!settings.payos()) return;
    for (var t : store.reconcileTopups(time.instant())) {
      try {
        reconcile((UUID) t.get("id"));
      } catch (RuntimeException e) {
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("Payment topup reconciliation pending: {}", t.get("id"));
      }
    }
  }
}

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
    JsonNode data = gateway.lookup(order);
    long amount = ((Number) t.get("amount")).longValue();
    if (!data.path("orderCode").isIntegralNumber()
        || data.path("orderCode").longValue() != order
        || !data.path("amount").isIntegralNumber()
        || data.path("amount").longValue() != amount)
      throw new InvalidOperationException("Kết quả payOS không khớp khoản nạp");
    if (!"PAID".equals(data.path("status").asText())) return;
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
    String link = data.path("id").asText();
    if (link.isBlank()
        || link.length() > 150
        || t.get("provider_id") != null && !link.equals(t.get("provider_id")))
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

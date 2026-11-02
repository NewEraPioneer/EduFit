package vn.edufit.payment.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edufit.payment.infra.*;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.InvalidOperationException;

@Service
public class PaymentPayoutService {
  private final PaymentStore store;
  private final PaymentAccess access;
  private final PaymentSettings settings;
  private final PayosPayoutGateway gateway;
  private final PaymentTime time;

  public PaymentPayoutService(
      PaymentStore store,
      PaymentAccess access,
      PaymentSettings settings,
      PayosPayoutGateway gateway,
      PaymentTime time) {
    this.store = store;
    this.access = access;
    this.settings = settings;
    this.gateway = gateway;
    this.time = time;
  }

  public Map<String, Object> approve(CurrentUser actor, UUID id, String note) {
    access.admin(actor);
    if (!settings.payout()) throw new InvalidOperationException("Chưa bật/cấu hình kênh chi payOS");
    if (note == null || note.isBlank() || note.length() > 2000)
      throw new InvalidOperationException("Cần ghi chú duyệt chi");
    var w =
        store.locked(
            () ->
                store.startPayout(id, note, actor.getUserId(), time.instant())
                    ? store.withdrawal(id)
                    : null);
    if (w != null) {
      try {
        apply(w, gateway.create(w));
      } catch (RuntimeException e) {
        store.locked(
            () -> {
              store.payoutUnknown(id);
              return null;
            });
      }
    }
    return store.withdrawal(id);
  }

  public Map<String, Object> refresh(CurrentUser actor, UUID id) {
    access.admin(actor);
    reconcile(id);
    return store.withdrawal(id);
  }

  private void reconcile(UUID id) {
    if (!settings.payout()) return;
    var w = store.locked(() -> store.claimPayout(id, time.instant()) ? store.withdrawal(id) : null);
    if (w == null) return;
    try {
      apply(w, gateway.lookup(w));
    } catch (RuntimeException e) {
      store.locked(
          () -> {
            store.payoutUnknown(id);
            return null;
          });
    }
  }

  private void apply(Map<String, Object> w, JsonNode data) {
    UUID id = (UUID) w.get("id");
    String provider = data.path("id").asText();
    if (!provider.matches("[A-Za-z0-9_-]{1,150}")
        || !id.toString().equals(data.path("referenceId").asText())
        || w.get("provider_id") != null && !provider.equals(w.get("provider_id"))) throw mismatch();
    var list = data.path("transactions");
    if (!list.isArray() || list.size() != 1) throw mismatch();
    var transaction = list.get(0);
    if (!id.toString().equals(transaction.path("referenceId").asText())
        || !transaction.path("amount").isIntegralNumber()
        || transaction.path("amount").longValue() != ((Number) w.get("amount")).longValue()
        || !w.get("bank_bin").equals(transaction.path("toBin").asText())
        || !w.get("bank_account").equals(transaction.path("toAccountNumber").asText()))
      throw mismatch();
    String state = transaction.path("state").asText();
    if (!Set.of("RECEIVED", "PROCESSING", "CANCELLED", "SUCCEEDED", "ON_HOLD", "REVERSED", "FAILED")
        .contains(state)) throw mismatch();
    store.locked(
        () -> {
          store.payoutResult(id, provider, state);
          return null;
        });
  }

  private InvalidOperationException mismatch() {
    return new InvalidOperationException("Kết quả chi hộ không khớp; giữ tiền chờ đối soát");
  }

  public void process() {
    if (!settings.payout()) return;
    for (var w : store.reconcilePayouts(time.instant())) reconcile((UUID) w.get("id"));
  }
}

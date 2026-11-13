package vn.edufit.payment.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edufit.payment.infra.*;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.*;

@Service
public class WalletService {
  private final PaymentStore store;
  private final PaymentAccess access;
  private final PaymentSettings settings;
  private final PayosGateway gateway;
  private final PaymentTime clock;

  public WalletService(
      PaymentStore store,
      PaymentAccess access,
      PaymentSettings settings,
      PayosGateway gateway,
      PaymentTime clock) {
    this.store = store;
    this.access = access;
    this.settings = settings;
    this.gateway = gateway;
    this.clock = clock;
  }

  public Map<String, Object> overview(CurrentUser a, int offset) {
    access.authenticated(a);
    UUID user = a.getUserId();
    var result = new HashMap<String, Object>(Map.of(
        "wallet",
        store.wallet(user),
        "entries",
        store.entries(user, offset),
        "topups",
        store.topups(user, offset),
        "withdrawals",
        store.withdrawals(user, offset),
        "topupEnabled",
        settings.payos() || settings.manual(),
        "topupProvider",
        settings.payos() ? "PAYOS" : "MANUAL"));
    if (a.hasRole("TUTOR")) result.put("payoutAccount", store.payoutAccount(user).orElse(null));
    if (a.hasRole("TUTOR")) result.put("pendingIncome", store.pendingIncome(user));
    return result;
  }

  /** Số lần gửi payOS tối đa cho một khoản nạp khi gặp mã đơn trùng. */
  static final int MAX_ORDER_ATTEMPTS = 3;

  /** Hai mục lịch sử cho người dùng: khoản nạp đã cộng ví, và tiền buổi học đã chốt. */
  public List<Map<String, Object>> history(CurrentUser a, String section, int offset) {
    access.authenticated(a);
    return switch (section) {
      case "topups" -> store.topupHistory(a.getUserId(), offset);
      case "sessions" -> store.sessionHistory(a.getUserId(), offset);
      default -> throw new InvalidOperationException("Mục lịch sử không hợp lệ");
    };
  }

  public Map<String, Object> topup(CurrentUser a, long amount) {
    access.learnerRole(a);
    money(amount);
    if (!settings.payos() && !settings.manual())
      throw new InvalidOperationException(
          "Chưa cấu hình tài khoản nhận tiền hoặc payOS; liên hệ Admin");
    String provider = settings.payos() ? "PAYOS" : "MANUAL";
    Instant expires = clock.instant().plusSeconds(1800);
    var t = store.locked(() -> store.createTopup(a.getUserId(), amount, provider, expires));
    UUID id = (UUID) t.get("id");
    long order = ((Number) t.get("order_code")).longValue();
    // External HTTP is deliberately outside the database lock/transaction.
    if (settings.payos()) {
      String path = a.hasRole("PARENT") ? "/parent/billing" : "/student/payments";
      PayosGateway.Checkout checkout = null;
      for (int attempt = 1; checkout == null; attempt++) {
        try {
          checkout = gateway.create(order, amount, path, expires);
        } catch (PayosRejectedException e) {
          // Chỉ 231 chứng minh payOS chưa tạo link cho mã này, nên đổi mã và gửi lại là an toàn.
          // Timeout hay lỗi mạng không vào đây: khoản nạp giữ nguyên mã để job đối soát tra cứu.
          if (!PayosRejectedException.DUPLICATE_ORDER.equals(e.code()))
            throw new InvalidOperationException(
                "payOS từ chối tạo mã thanh toán (mã " + e.code() + "). Vui lòng thử lại sau.");
          if (attempt >= MAX_ORDER_ATTEMPTS)
            throw new InvalidOperationException(
                "payOS từ chối tạo mã thanh toán vì trùng mã đơn. Vui lòng thử lại sau.");
          order = store.locked(() -> store.renewOrderCode(id));
        }
      }
      var link = checkout;
      store.locked(
          () -> {
            store.checkout(id, link.url(), link.paymentLinkId(), link.accountNumber());
            return null;
          });
      return Map.of("id", id, "orderCode", order, "checkoutUrl", link.url(), "provider", provider);
    }
    String info = "EF" + Long.toString(order, 36).toUpperCase(Locale.ROOT);
    String qr =
        "https://img.vietqr.io/image/"
            + settings.bankBin()
            + "-"
            + settings.bankAccount()
            + "-compact2.png?amount="
            + amount
            + "&addInfo="
            + enc(info)
            + "&accountName="
            + enc(settings.bankHolder());
    store.locked(
        () -> {
          store.checkout(id, qr, null);
          return null;
        });
    return Map.of(
        "id",
        id,
        "qrUrl",
        qr,
        "bankAccount",
        settings.bankAccount(),
        "bankHolder",
        settings.bankHolder(),
        "description",
        info,
        "amount",
        amount,
        "provider",
        provider);
  }

  public void webhook(String payload) {
    var receipt = gateway.verify(payload);
    store.locked(
        () -> {
          var found = store.findTopupByOrder(receipt.orderCode());
          // payOS sends a signed sample when registering a webhook. Unknown orders never credit a
          // wallet.
          if (found.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger(WalletService.class)
                .warn("Verified payOS webhook has no matching order: {}", receipt.orderCode());
            return null;
          }
          var t = found.get();
          if (!"PAYOS".equals(t.get("provider"))
              || ((Number) t.get("amount")).longValue() != receipt.amount()
              || !"VND".equals(receipt.currency())
              || !receipt.paymentLinkId().equals(t.get("provider_id")))
            throw new InvalidOperationException("Webhook không khớp khoản nạp");
          String expectedAccount = t.get("provider_account_number") instanceof String account
              ? account : settings.bankAccount();
          if (!receipt.matchesAccount(expectedAccount))
            throw new InvalidOperationException("Sai tài khoản nhận tiền");
          store.creditTopup((UUID) t.get("id"), receipt.reference(), null);
          return null;
        });
  }

  public void confirmManual(CurrentUser a, UUID id, String reference) {
    access.admin(a);
    reference(reference);
    store.locked(
        () -> {
          var t = store.topup(id);
          if (!"MANUAL".equals(t.get("provider")))
            throw new InvalidOperationException("Khoản payOS phải được xác minh bằng webhook");
          store.creditTopup(id, reference, a.getUserId());
          return null;
        });
  }

  public Map<String, Object> withdraw(
      CurrentUser a, long amount, String bank, String account, String holder) {
    return withdraw(a, amount, bank, account, holder, null);
  }

  public Map<String, Object> withdraw(
      CurrentUser a, long amount, String bank, String account, String holder, String bankBin) {
    access.tutorRole(a);
    money(amount);
    if (bankBin != null && !bankBin.matches("[0-9]{6}"))
      throw new InvalidOperationException("Mã BIN ngân hàng gồm 6 số");
    if (bank == null
        || bank.isBlank()
        || bank.length() > 100
        || account == null
        || !account.matches("[0-9]{5,34}")
        || holder == null
        || holder.isBlank()
        || holder.length() > 150)
      throw new InvalidOperationException("Thông tin ngân hàng không hợp lệ");
    return store.locked(
        () -> {
          var w =
              store.createWithdrawal(
                  a.getUserId(), amount, bank.trim(), account, holder.trim(), bankBin);
          store.audit(a.getUserId(), (UUID) w.get("id"), "WITHDRAW_REQUESTED", "VND=" + amount);
          return w;
        });
  }

  public Map<String, Object> savePayoutAccount(CurrentUser actor, String bank, String account,
      String holder, String bin, UUID expectedId) {
    access.tutorRole(actor);
    if (bank == null || bank.isBlank() || bank.length() > 100
        || account == null || !account.matches("[0-9]{5,34}")
        || holder == null || holder.isBlank() || holder.length() > 150
        || bin == null || !bin.matches("[0-9]{6}"))
      throw new InvalidOperationException("Thông tin tài khoản nhận tiền không hợp lệ");
    return store.locked(() -> store.savePayoutAccount(actor.getUserId(), expectedId,
        bank.trim(), account, holder.trim(), bin));
  }

  public Map<String, Object> withdrawSaved(CurrentUser actor, long amount, UUID accountId) {
    access.tutorRole(actor);
    money(amount);
    return store.locked(() -> {
      var recipient = store.payoutAccount(actor.getUserId())
          .filter(row -> Objects.equals(accountId, row.get("id")))
          .orElseThrow(() -> new InvalidOperationException(
              "Tài khoản nhận tiền đã thay đổi hoặc chưa được lưu; hãy tải lại"));
      var withdrawal = store.createWithdrawal(actor.getUserId(), amount,
          (String) recipient.get("bank_name"), (String) recipient.get("bank_account"),
          (String) recipient.get("bank_holder"), (String) recipient.get("bank_bin"));
      store.audit(actor.getUserId(), (UUID) withdrawal.get("id"), "WITHDRAW_REQUESTED", "VND=" + amount);
      return withdrawal;
    });
  }

  public void reviewWithdrawal(
      CurrentUser a, UUID id, boolean paid, String reference, String note) {
    access.admin(a);
    if (paid) reference(reference);
    if (note == null || note.isBlank() || note.length() > 2000)
      throw new InvalidOperationException("Cần ghi chú đối soát");
    store.locked(
        () -> {
          store.reviewWithdrawal(id, paid, reference, note, a.getUserId());
          return null;
        });
  }

  public Map<String, Object> configuration(CurrentUser a) {
    access.admin(a);
    return settings.readiness();
  }

  public Map<String, Object> admin(CurrentUser a, int offset) {
    access.admin(a);
    return Map.of(
        "topups",
        store.topups(null, offset),
        "withdrawals",
        store.withdrawals(null, offset),
        "configuration",
        settings.readiness());
  }

  private static void money(long amount) {
    if (amount < 10000 || amount > 100_000_000)
      throw new InvalidOperationException("Số tiền từ 10.000đ đến 100.000.000đ");
  }

  private static void reference(String value) {
    if (value == null || value.trim().length() < 6 || value.length() > 200)
      throw new InvalidOperationException("Cần mã giao dịch ngân hàng thực tế (6–200 ký tự)");
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}

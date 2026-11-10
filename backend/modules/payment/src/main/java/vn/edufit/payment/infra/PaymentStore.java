package vn.edufit.payment.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edufit.payment.application.PaymentViews.*;
import vn.edufit.payment.domain.SessionCharge;
import vn.edufit.shared.exception.InvalidOperationException;

@Repository
public class PaymentStore {
  private final JdbcTemplate db;
  private final TransactionTemplate tx;
  private final vn.edufit.shared.notification.NotificationSink notifications;
  private final org.springframework.context.ApplicationEventPublisher events;

  public PaymentStore(JdbcTemplate db, org.springframework.transaction.PlatformTransactionManager manager,
      vn.edufit.shared.notification.NotificationSink notifications,
      org.springframework.context.ApplicationEventPublisher events) {
    this.db = Objects.requireNonNull(db);
    this.tx = new TransactionTemplate(Objects.requireNonNull(manager));
    // Thông báo và PaymentSessionChangedEvent là một phần của nghiệp vụ thanh toán, không được nuốt mất
    this.notifications = Objects.requireNonNull(notifications);
    this.events = Objects.requireNonNull(events);
  }

  public void notice(UUID user, UUID ref, String type, String title, String text) {
    notifications.notify(user, null, "PAYMENT_" + type, title, text, "PAYMENT_WALLET", ref);
  }

  // One DB row serializes monetary mutations across application instances. No network calls inside.
  public <T> T locked(Supplier<T> work) {
    return tx.execute(
        s -> {
          db.queryForObject("select id from payment_mutex where id=true for update", Boolean.class);
          return work.get();
        });
  }

  public Wallet wallet(UUID user) {
    return db
        .query(
            "select available,held from payment_wallet where user_id=?",
            (r, n) -> new Wallet(r.getLong(1), r.getLong(2)),
            user)
        .stream()
        .findFirst()
        .orElse(new Wallet(0, 0));
  }

  public long pendingIncome(UUID tutor) {
    Long value = db.queryForObject("select coalesce(sum(amount-(amount*commission_bps/10000)),0)"
        + " from payment_charge where tutor_user_id=? and status in"
        + " ('FUNDED','AWAITING_CONFIRMATION','DISPUTED','AWAITING_ADMIN','ADMIN_REVIEW')", Long.class, tutor);
    return value == null ? 0 : value;
  }

  public void move(UUID user, long available, long held, String key, String kind, UUID ref) {
    if (db.queryForObject("select count(*) from payment_entry where event_key=?", Long.class, key)
        > 0) throw new InvalidOperationException("Giao dịch đã được ghi sổ");
    if (user != null) {
      db.update("insert into payment_wallet(user_id) values (?) on conflict do nothing", user);
      var w = wallet(user);
      if (Math.addExact(w.available(), available) < 0 || Math.addExact(w.held(), held) < 0)
        throw new InvalidOperationException("Số dư khả dụng không đủ");
      db.update(
          "update payment_wallet set available=available+?,held=held+? where user_id=?",
          available,
          held,
          user);
    }
    db.update(
        "insert into payment_entry(user_id,event_key,kind,available_delta,held_delta,reference_id)"
            + " values (?,?,?,?,?,?)",
        user,
        key,
        kind,
        available,
        held,
        ref);
    if (user != null) {
      String title =
          switch (kind) {
            case "TOPUP" -> "Nạp tiền thành công";
            case "WITHDRAW_RESERVED" -> "Đã tiếp nhận yêu cầu rút tiền";
            case "WITHDRAW_PAID" -> "Rút tiền thành công";
            case "WITHDRAW_REJECTED", "WITHDRAW_FAILED" -> "Khoản rút đã được hoàn về ví";
            default -> null;
          };
      if (title != null)
        notice(
            user,
            ref,
            kind,
            title,
            "Số dư thay đổi "
                + available
                + " VND; đang giữ thay đổi "
                + held
                + " VND. Xem sổ ví để biết chi tiết.");
    }
  }

  public void audit(UUID actor, UUID ref, String action, String detail) {
    db.update(
        "insert into payment_audit(actor_id,reference_id,action,detail) values (?,?,?,?)",
        actor,
        ref,
        action,
        detail);
  }

  public List<Map<String, Object>> entries(UUID owner, int offset) {
    return db.queryForList(
        "select id,kind,available_delta,held_delta,reference_id,created_at::text as created_at from"
            + " payment_entry where user_id=? order by created_at desc,id limit 50 offset ?",
        owner,
        offset);
  }

  public Optional<Charge> find(UUID id) {
    return db.query("select * from payment_charge where session_id=?", this::mapCharge, id).stream()
        .findFirst();
  }

  public List<UUID> payerClassIds(UUID user) {
    return db.query("select distinct class_id from payment_charge where payer_user_id=?",
        (r,n) -> r.getObject("class_id", UUID.class), user);
  }

  public List<Charge> pending() {
    return db.query(
        "select * from payment_charge where status not in ('SETTLED','REFUNDED','EXPIRED') order by"
            + " updated_at limit 200",
        this::mapCharge);
  }

  public List<Charge> adminCharges(int offset) {
    return db.query(
        "select * from payment_charge where status in ('AWAITING_ADMIN','ADMIN_REVIEW') order by updated_at limit 50"
            + " offset ?",
        this::mapCharge,
        offset);
  }

  public List<Map<String, Object>> caseMessages(UUID id) {
    return db.queryForList("select id,side,message,created_at::text as created_at from payment_case_message"
        + " where session_id=? order by created_at,id limit 100", id);
  }

  public void addCaseMessage(Charge c, UUID author, UUID id, String side, String text) {
    var previous = db.queryForList("select session_id,author_id,message from payment_case_message where id=?", id);
    if (!previous.isEmpty()) {
      var row = previous.getFirst();
      if (!c.sessionId().equals(row.get("session_id")) || !author.equals(row.get("author_id"))
          || !text.equals(row.get("message"))) throw new InvalidOperationException("Mã phản hồi đã được sử dụng");
      return;
    }
    if (db.queryForObject("select count(*) from payment_case_message where session_id=? and author_id=?",
        Long.class, c.sessionId(), author) >= 20)
      throw new InvalidOperationException("Đã đủ 20 phản hồi cho hồ sơ này; vui lòng liên hệ hỗ trợ");
    db.update("insert into payment_case_message(id,session_id,author_id,side,message) values (?,?,?,?,?)",
        id, c.sessionId(), author, side, text);
    events.publishEvent(new vn.edufit.payment.api.event.PaymentSessionChangedEvent(UUID.randomUUID(),
        Instant.now(), c.sessionId(), c.payerId(), c.tutorUserId(), c.studentId(), "CASE_MESSAGE", c.state().reviewDeadline()));
  }

  public void insert(Charge c) {
    db.update(
        "insert into"
            + " payment_charge(session_id,class_id,student_id,tutor_user_id,payer_user_id,amount,commission_bps,end_at,status)"
            + " values (?,?,?,?,?,?,?,?,?)",
        c.sessionId(),
        c.classId(),
        c.studentId(),
        c.tutorUserId(),
        c.payerId(),
        c.state().amount,
        c.commissionBps(),
        Timestamp.from(c.state().endAt),
        c.state().status);
  }

  public void save(Charge c) {
    var s = c.state();
    var old = db.queryForMap("select status,review_started_at from payment_charge where session_id=?", c.sessionId());
    db.update(
        "update payment_charge set"
            + " end_at=?,status=?,learner_accepted=?,tutor_accepted=?,auto_accepted=?,dispute_reason=?,dispute_opened_at=?,learner_report=?,tutor_report=?,learner_amount=?,tutor_amount=?,settled_amount=?,review_started_at=?,dispute_category=?,dispute_evidence=?,dispute_reporter_id=?,resolution_note=?,resolved_at=?,updated_at=now()"
            + " where session_id=?",
        ts(s.endAt),
        s.status,
        s.learnerAccepted,
        s.tutorAccepted,
        s.autoAccepted,
        s.disputeReason,
        ts(s.disputeOpenedAt),
        s.learnerReport,
        s.tutorReport,
        s.learnerAmount,
        s.tutorAmount,
        s.settledAmount,
        ts(s.reviewStartedAt), s.disputeCategory, s.disputeEvidence, s.disputeReporterId,
        s.resolutionNote, ts(s.resolvedAt), c.sessionId());
    boolean opened = old.get("review_started_at") == null && s.reviewStartedAt != null;
    if (opened || !s.status.equals(old.get("status")))
      events.publishEvent(new vn.edufit.payment.api.event.PaymentSessionChangedEvent(
          UUID.randomUUID(), Instant.now(), c.sessionId(), c.payerId(), c.tutorUserId(), c.studentId(),
          s.status, s.reviewDeadline()));
  }

  private Charge mapCharge(ResultSet r, int n) throws SQLException {
    var s = new SessionCharge(r.getLong("amount"), r.getTimestamp("end_at").toInstant());
    s.status = r.getString("status");
    var review = r.getTimestamp("review_started_at");
    s.reviewStartedAt = review == null ? null : review.toInstant();
    s.disputeCategory = r.getString("dispute_category");
    s.disputeEvidence = r.getString("dispute_evidence");
    s.disputeReporterId = r.getObject("dispute_reporter_id", UUID.class);
    s.resolutionNote = r.getString("resolution_note");
    var resolved = r.getTimestamp("resolved_at");
    s.resolvedAt = resolved == null ? null : resolved.toInstant();
    s.learnerAccepted = r.getBoolean("learner_accepted");
    s.tutorAccepted = r.getBoolean("tutor_accepted");
    s.autoAccepted = r.getBoolean("auto_accepted");
    s.disputeReason = r.getString("dispute_reason");
    var opened = r.getTimestamp("dispute_opened_at");
    s.disputeOpenedAt = opened == null ? null : opened.toInstant();
    s.learnerReport = r.getString("learner_report");
    s.tutorReport = r.getString("tutor_report");
    s.learnerAmount = (Long) r.getObject("learner_amount");
    s.tutorAmount = (Long) r.getObject("tutor_amount");
    s.settledAmount = r.getLong("settled_amount");
    return new Charge(
        r.getObject("session_id", UUID.class),
        r.getObject("class_id", UUID.class),
        r.getObject("student_id", UUID.class),
        r.getObject("payer_user_id", UUID.class),
        r.getObject("tutor_user_id", UUID.class),
        r.getInt("commission_bps"),
        s);
  }

  public Map<String, Object> createTopup(UUID user, long amount, String provider, Instant expires) {
    return db.queryForMap(
        "insert into payment_topup(id,user_id,amount,provider,status,expires_at) values"
            + " (?,?,?,?,'PENDING',?) returning *",
        UUID.randomUUID(),
        user,
        amount,
        provider,
        ts(expires));
  }

  public void checkout(UUID id, String url, String providerId) {
    checkout(id, url, providerId, null);
  }

  public void checkout(UUID id, String url, String providerId, String accountNumber) {
    var current = topup(id);
    if (current.get("provider_id") != null && !Objects.equals(current.get("provider_id"), providerId))
      throw new InvalidOperationException("Không được thay mã link của yêu cầu nạp");
    if (accountNumber != null && current.get("provider_account_number") != null
        && !accountNumber.equals(current.get("provider_account_number")))
      throw new InvalidOperationException("Không được thay tài khoản nhận của yêu cầu nạp");
    db.update(
        "update payment_topup set checkout_url=?,provider_id=?,"
            + "provider_account_number=coalesce(provider_account_number,?) where id=? and status='PENDING'",
        url,
        providerId,
        accountNumber,
        id);
  }

  public Map<String, Object> topup(UUID id) {
    return one("select * from payment_topup where id=?", id);
  }

  public Optional<Map<String, Object>> findTopupByOrder(long order) {
    return db.queryForList("select * from payment_topup where order_code=?", order).stream()
        .findFirst();
  }

  public List<Map<String, Object>> topups(UUID owner, int offset) {
    return owner == null
        ? db.queryForList(
            "select"
                + " id,user_id,order_code,amount,provider,status,checkout_url,provider_id,transaction_reference,created_at::text"
                + " as created_at,expires_at::text as expires_at from payment_topup order by"
                + " payment_topup.created_at desc,id limit 50 offset ?",
            offset)
        : db.queryForList(
            "select"
                + " id,user_id,order_code,amount,provider,status,checkout_url,provider_id,transaction_reference,created_at::text"
                + " as created_at,expires_at::text as expires_at from payment_topup where user_id=?"
                + " order by payment_topup.created_at desc,id limit 50 offset ?",
            owner,
            offset);
  }

  public void creditTopup(UUID id, String reference, UUID actor) {
    reference = reference.trim();
    var t = topup(id);
    if ("PAID".equals(t.get("status"))) {
      if (!reference.equals(t.get("transaction_reference")))
        throw new InvalidOperationException("Khoản nạp đã có mã giao dịch khác");
      return;
    }
    if (!"PENDING".equals(t.get("status")))
      throw new InvalidOperationException("Khoản nạp không còn hiệu lực");
    UUID owner = (UUID) t.get("user_id");
    long amount = ((Number) t.get("amount")).longValue();
    db.update(
        "update payment_topup set status='PAID',transaction_reference=? where id=?", reference, id);
    move(owner, amount, 0, "topup:" + id, "TOPUP", id);
    audit(actor, id, "TOPUP_CONFIRMED", reference);
  }

  public Map<String, Object> createWithdrawal(
      UUID user, long amount, String bank, String account, String holder) {
    return createWithdrawal(user, amount, bank, account, holder, null);
  }

  public Map<String, Object> createWithdrawal(
      UUID user, long amount, String bank, String account, String holder, String bin) {
    var pending =
        db.queryForList(
            "select * from payment_withdrawal where user_id=? and status in"
                + " ('PENDING','PROCESSING')",
            user);
    if (!pending.isEmpty()) {
      var previous = pending.getFirst();
      if (((Number) previous.get("amount")).longValue() == amount
          && bank.equals(previous.get("bank_name"))
          && account.equals(previous.get("bank_account"))
          && holder.equals(previous.get("bank_holder"))
          && Objects.equals(bin, previous.get("bank_bin"))) return previous;
      throw new InvalidOperationException("Đã có yêu cầu rút đang chờ xử lý");
    }
    UUID id = UUID.randomUUID();
    move(user, -amount, amount, "withdraw-reserve:" + id, "WITHDRAW_RESERVED", id);
    db.update(
        "insert into"
            + " payment_withdrawal(id,user_id,amount,status,bank_name,bank_account,bank_holder,bank_bin)"
            + " values (?,?,?,'PENDING',?,?,?,?)",
        id,
        user,
        amount,
        bank,
        account,
        holder,
        bin);
    return withdrawal(id);
  }

  public Optional<Map<String, Object>> payoutAccount(UUID user) {
    return db.queryForList("select id,bank_name,bank_bin,bank_account,bank_holder,updated_at::text"
        + " as updated_at from payment_payout_account where user_id=?", user).stream().findFirst();
  }

  // Called under the same money mutex as withdrawal creation. The new ID invalidates stale forms.
  public Map<String, Object> savePayoutAccount(UUID user, UUID expectedId, String bank,
      String account, String holder, String bin) {
    var current = payoutAccount(user);
    if (!Objects.equals(current.map(row -> (UUID) row.get("id")).orElse(null), expectedId))
      throw new InvalidOperationException("Tài khoản nhận tiền đã thay đổi; hãy tải lại trước khi lưu");
    UUID id = UUID.randomUUID();
    db.update("insert into payment_payout_account(user_id,id,bank_name,bank_bin,bank_account,bank_holder)"
        + " values (?,?,?,?,?,?) on conflict(user_id) do update set id=excluded.id,"
        + "bank_name=excluded.bank_name,bank_bin=excluded.bank_bin,bank_account=excluded.bank_account,"
        + "bank_holder=excluded.bank_holder,updated_at=now()", user, id, bank, bin, account, holder);
    audit(user, id, "PAYOUT_ACCOUNT_SAVED", "Recipient updated");
    return payoutAccount(user).orElseThrow();
  }

  public Map<String, Object> withdrawal(UUID id) {
    return one("select * from payment_withdrawal where id=?", id);
  }

  public List<Map<String, Object>> withdrawals(UUID owner, int offset) {
    return owner == null
        ? db.queryForList(
            "select"
                + " id,user_id,amount,status,bank_name,bank_account,bank_holder,bank_bin,provider_id,provider_state,provider_message,transaction_reference,admin_note,created_at::text"
                + " as created_at from payment_withdrawal order by payment_withdrawal.created_at"
                + " desc,id limit 50 offset ?",
            offset)
        : db.queryForList(
            "select"
                + " id,user_id,amount,status,bank_name,bank_account,bank_holder,bank_bin,provider_id,provider_state,provider_message,transaction_reference,admin_note,created_at::text"
                + " as created_at from payment_withdrawal where user_id=? order by"
                + " payment_withdrawal.created_at desc,id limit 50 offset ?",
            owner,
            offset);
  }

  public void reviewWithdrawal(UUID id, boolean paid, String reference, String note, UUID actor) {
    reference = reference == null ? null : reference.trim();
    var w = withdrawal(id);
    if (!"PENDING".equals(w.get("status")))
      throw new InvalidOperationException("Yêu cầu rút đã được xử lý");
    long amount = ((Number) w.get("amount")).longValue();
    move(
        (UUID) w.get("user_id"),
        paid ? 0 : amount,
        -amount,
        "withdraw-result:" + id,
        paid ? "WITHDRAW_PAID" : "WITHDRAW_REJECTED",
        id);
    db.update(
        "update payment_withdrawal set status=?,transaction_reference=?,admin_note=? where id=?",
        paid ? "PAID" : "REJECTED",
        paid ? reference : null,
        note,
        id);
    audit(actor, id, paid ? "WITHDRAW_PAID" : "WITHDRAW_REJECTED", note + " | " + reference);
  }

  public boolean claimTopup(UUID id, Instant now) {
    return db.update(
            "update payment_topup set reconcile_after=? where id=? and status='PENDING' and"
                + " provider='PAYOS' and reconcile_after<=?",
            ts(now.plusSeconds(30)),
            id,
            ts(now))
        == 1;
  }

  public List<Map<String, Object>> reconcileTopups(Instant now) {
    return db.queryForList(
        "select * from payment_topup where status='PENDING' and provider='PAYOS' and"
            + " reconcile_after<=? order by reconcile_after,created_at limit 20",
        ts(now));
  }

  public boolean startPayout(UUID id, String note, UUID actor, Instant now) {
    var w = withdrawal(id);
    if ("PROCESSING".equals(w.get("status"))) return false;
    if (!"PENDING".equals(w.get("status")))
      throw new InvalidOperationException("Yêu cầu rút đã kết thúc");
    if (w.get("bank_bin") == null)
      throw new InvalidOperationException(
          "Yêu cầu cũ thiếu mã BIN; chỉ được đối soát thủ công hoặc từ chối để gửi lại");
    db.update(
        "update payment_withdrawal set"
            + " status='PROCESSING',payout_requested_at=?,reconcile_after=?,admin_note=?,provider_state='SUBMITTING'"
            + " where id=?",
        ts(now),
        ts(now.plusSeconds(60)),
        note,
        id);
    audit(actor, id, "PAYOUT_APPROVED", note);
    return true;
  }

  public List<Map<String, Object>> reconcilePayouts(Instant now) {
    return db.queryForList(
        "select * from payment_withdrawal where status='PROCESSING' and reconcile_after<=? order by"
            + " reconcile_after,created_at limit 20",
        ts(now));
  }

  public boolean claimPayout(UUID id, Instant now) {
    return db.update(
            "update payment_withdrawal set reconcile_after=? where id=? and status='PROCESSING' and"
                + " reconcile_after<=?",
            ts(now.plusSeconds(60)),
            id,
            ts(now))
        == 1;
  }

  public void payoutUnknown(UUID id) {
    db.update(
        "update payment_withdrawal set provider_message=? where id=? and status='PROCESSING'",
        "Chưa xác nhận kết quả. Tiền đang được giữ để đối soát tự động.",
        id);
  }

  public void payoutResult(UUID id, String providerId, String state) {
    var w = withdrawal(id);
    if (!"PROCESSING".equals(w.get("status"))) return;
    boolean paid = "SUCCEEDED".equals(state),
        failed = Set.of("FAILED", "CANCELLED", "REVERSED").contains(state);
    db.update(
        "update payment_withdrawal set provider_id=?,provider_state=?,provider_message=null where"
            + " id=?",
        providerId,
        state,
        id);
    if (!paid && !failed) return;
    long amount = ((Number) w.get("amount")).longValue();
    move(
        (UUID) w.get("user_id"),
        paid ? 0 : amount,
        -amount,
        "withdraw-result:" + id,
        paid ? "WITHDRAW_PAID" : "WITHDRAW_FAILED",
        id);
    db.update(
        "update payment_withdrawal set status=?,transaction_reference=? where id=?",
        paid ? "PAID" : "FAILED",
        providerId,
        id);
    audit(null, id, "PAYOUT_" + state, "Đã xác minh phản hồi payOS");
  }

  private Map<String, Object> one(String sql, Object id) {
    var rows = db.queryForList(sql, id);
    if (rows.isEmpty()) throw new InvalidOperationException("Không tìm thấy giao dịch");
    return rows.getFirst();
  }

  private static Timestamp ts(Instant time) {
    return time == null ? null : Timestamp.from(time);
  }
}

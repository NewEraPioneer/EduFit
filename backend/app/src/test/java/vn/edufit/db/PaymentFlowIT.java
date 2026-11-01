package vn.edufit.db;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.payment.application.*;
import vn.edufit.payment.infra.PaymentStore;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.*;
import vn.edufit.scheduling.api.SessionFacade;
import vn.edufit.scheduling.api.dto.SessionSummaryView;
import vn.edufit.shared.auth.CurrentUser;

class PaymentFlowIT {
  static PostgreSQLContainer<?> container;
  static DriverManagerDataSource datasource;
  JdbcTemplate db;
  PaymentStore store;
  SessionPaymentService service;
  ProfileFacade profiles;
  ConnectionFacade connections;
  SessionFacade sessions;
  UUID studentUser, studentId, tutorUser, tutorId, parentUser, adminUser, classId, sessionId;
  Instant end = Instant.parse("2026-10-21T12:00:00Z");
  AtomicReference<Instant> now = new AtomicReference<>();

  @BeforeAll
  static void database() {
    String url = System.getenv("PAYMENT_TEST_JDBC");
    String user = System.getenv().getOrDefault("PAYMENT_TEST_USER", "payment_test");
    String pass = System.getenv().getOrDefault("PAYMENT_TEST_PASSWORD", "");
    if (url == null) {
      container = new PostgreSQLContainer<>("postgres:17");
      container.start();
      url = container.getJdbcUrl();
      user = container.getUsername();
      pass = container.getPassword();
    }
    String schema = "payment_it_" + UUID.randomUUID().toString().replace("-", "");
    datasource =
        new DriverManagerDataSource(
            url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, user, pass);
    Flyway flyway =
        Flyway.configure()
            .dataSource(datasource)
            .schemas(schema)
            .locations("classpath:db/migration")
            .load();
    assertEquals(22, flyway.migrate().migrationsExecuted);
    flyway.validate();
    assertEquals(0, flyway.migrate().migrationsExecuted);
  }

  @AfterAll
  static void close() {
    if (container != null) container.stop();
  }

  @BeforeEach
  void setup() {
    db = new JdbcTemplate(datasource);
    store = new PaymentStore(db, new DataSourceTransactionManager(datasource),
        mock(vn.edufit.shared.notification.NotificationSink.class),
        mock(org.springframework.context.ApplicationEventPublisher.class));
    db.execute(
        "truncate"
            + " payment_entry,payment_audit,payment_charge,payment_topup,payment_withdrawal,payment_wallet,payment_payout_account,payment_case_message");
    if (db.queryForObject("select count(*) from class", Long.class) == 0) {
      UUID student = db.queryForObject("select student_id from learning_goal limit 1", UUID.class);
      UUID tutor = db.queryForObject("select tutor_id from tutor_profile limit 1", UUID.class);
      UUID goal =
          db.queryForObject(
              "select goal_id from learning_goal where student_id=? limit 1", UUID.class, student);
      UUID sender =
          db.queryForObject(
              "select user_id from student_profile where student_id=?", UUID.class, student);
      UUID request = UUID.randomUUID();
      db.update(
          "insert into"
              + " connection_request(request_id,student_id,tutor_id,goal_id,sent_by_user_id,status,expires_at)"
              + " values (?,?,?,?,?,'ACCEPTED',now())",
          request,
          student,
          tutor,
          goal,
          sender);
      db.update(
          "insert into class(connection_request_id,student_id,tutor_id,goal_id) values (?,?,?,?)",
          request,
          student,
          tutor,
          goal);
    }
    var cls =
        db.queryForMap(
            "select c.*,s.user_id student_user,t.user_id tutor_user from class c join"
                + " student_profile s using(student_id) join tutor_profile t using(tutor_id) limit"
                + " 1");
    classId = (UUID) cls.get("class_id");
    studentId = (UUID) cls.get("student_id");
    tutorId = (UUID) cls.get("tutor_id");
    studentUser = (UUID) cls.get("student_user");
    tutorUser = (UUID) cls.get("tutor_user");
    parentUser =
        db.queryForObject("select user_id from account where role='PARENT' limit 1", UUID.class);
    adminUser =
        db.queryForObject("select user_id from account where role='ADMIN' limit 1", UUID.class);
    sessionId = UUID.randomUUID();
    db.update(
        "insert into"
            + " tutoring_session(session_id,class_id,tutor_id,student_id,start_at,end_at,mode,status,proposed_by,expires_at)"
            + " values (?,?,?,?,?,?,'ONLINE','COMPLETED',?,?)",
        sessionId,
        classId,
        tutorId,
        studentId,
        Timestamp.from(end.minusSeconds(5400)),
        Timestamp.from(end),
        tutorUser,
        Timestamp.from(end));
    profiles = mock(ProfileFacade.class);
    connections = mock(ConnectionFacade.class);
    sessions = mock(SessionFacade.class);
    when(profiles.findStudentByUserId(studentUser))
        .thenReturn(Optional.of(new StudentSummaryDto(studentId, studentUser, null, null, null, true)));
    when(profiles.findTutorById(tutorId))
        .thenReturn(
            Optional.of(
                new TutorSummaryDto(tutorId, tutorUser, "Tutor", null, null, "ONLINE", null, false, null, null, null, null, "VERIFIED", null, null, 0)));
    when(connections.hasConfirmedParentLink(parentUser, studentId)).thenReturn(true);
    when(sessions.findSessionById(sessionId))
        .thenReturn(
            Optional.of(
                new SessionSummaryView(
                    sessionId,
                    classId,
                    tutorId,
                    studentId,
                    end.minusSeconds(5400),
                    end,
                    "ONLINE",
                    "COMPLETED",
                    false,
                    end.minusSeconds(7200))));
    now.set(end.minusSeconds(3600));
    Clock clock =
        new Clock() {
          public ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          public Clock withZone(ZoneId z) {
            return this;
          }

          public Instant instant() {
            return now.get();
          }
        };
    service =
        new SessionPaymentService(
            store,
            new PaymentAccess(profiles, connections),
            sessions,
            profiles,
            connections,
            new PaymentTime(clock));
    store.locked(
        () -> {
          store.move(parentUser, 1000000, 0, "seed:" + sessionId, "TOPUP", sessionId);
          return null;
        });
  }

  CurrentUser actor(UUID id, String role) {
    var a = mock(CurrentUser.class);
    when(a.getUserId()).thenReturn(id);
    when(a.hasRole(anyString())).thenAnswer(i -> role.equals(i.getArgument(0)));
    return a;
  }

  void fund() {
    service.fund(actor(parentUser, "PARENT"), sessionId, 300000);
    now.set(end);
  }

  void confirmTutor() {
    service.act(actor(tutorUser, "TUTOR"), sessionId, "confirm", null, null);
  }

  void dispute() {
    service.act(actor(studentUser, "STUDENT"), sessionId, "dispute", "Thiếu giờ", null);
  }

  @Test
  void parentFundsStudentConfirmsAndConcurrentConfirmPaysTutorOnce() throws Exception {
    fund();
    confirmTutor();
    try (var pool = Executors.newFixedThreadPool(4)) {
      var jobs = new ArrayList<Future<?>>();
      for (int i = 0; i < 4; i++)
        jobs.add(
            pool.submit(
                () ->
                    service.act(actor(studentUser, "STUDENT"), sessionId, "confirm", null, null)));
      for (var f : jobs) f.get(15, TimeUnit.SECONDS);
    }
    assertEquals(300000, store.wallet(parentUser).held());
    assertEquals(0, store.wallet(tutorUser).available());
    now.set(end.plusSeconds(86400));
    service.processDeadlines();
    service.processDeadlines();
    assertEquals(700000, store.wallet(parentUser).available());
    assertEquals(0, store.wallet(parentUser).held());
    assertEquals(270000, store.wallet(tutorUser).available());
    assertEquals(
        30000,
        db.queryForObject(
            "select sum(available_delta) from payment_entry where kind='COMMISSION'", Long.class));
  }

  @Test
  void overdueDisputeWaitsForAdminAndOnlyAnExplicitRefundReleasesMoney() {
    fund();
    confirmTutor();
    dispute();
    now.set(end.plusSeconds(172800));
    service.processDeadlines();
    service.processDeadlines();
    assertEquals("ADMIN_REVIEW", store.find(sessionId).orElseThrow().state().status);
    assertEquals(300000, store.wallet(parentUser).held());
    service.resolve(actor(adminUser, "ADMIN"), sessionId, 0, "Đã xem xét hồ sơ, hoàn đủ");
    assertEquals(1000000, store.wallet(parentUser).available());
    assertEquals(0, store.wallet(parentUser).held());
    assertEquals(0, store.wallet(studentUser).available());
    assertEquals(0, store.wallet(tutorUser).available());
  }

  @Test
  void matchingReportsWaitForAdminAndPartialSettlementIsAtomic() {
    fund();
    dispute();
    service.act(actor(parentUser, "PARENT"), sessionId, "report", "Đồng ý 100k", 100000L);
    service.act(actor(tutorUser, "TUTOR"), sessionId, "report", "Đã thống nhất", 100000L);
    assertEquals(sessionId, store.adminCharges(0).getFirst().sessionId());
    assertTrue(store.adminCharges(50).isEmpty());
    now.set(end.plusSeconds(200000));
    service.processDeadlines();
    assertEquals(300000, store.wallet(parentUser).held());
    assertEquals(0, store.wallet(tutorUser).available());
    service.review(actor(adminUser, "ADMIN"), sessionId, true, "Đã kiểm tra hai báo cáo");
    assertEquals(900000, store.wallet(parentUser).available());
    assertEquals(90000, store.wallet(tutorUser).available());
    assertEquals(0, store.wallet(parentUser).held());
    assertThrows(
        RuntimeException.class,
        () -> service.review(actor(adminUser, "ADMIN"), sessionId, true, "Duyệt lại"));
  }

  @Test
  void autoSettlementAt24HoursDoesNotOverrideDispute() {
    fund();
    confirmTutor();
    dispute();
    now.set(end.plusSeconds(3600));
    service.processDeadlines();
    assertEquals("DISPUTED", store.find(sessionId).orElseThrow().state().status);
    assertEquals(0, store.wallet(tutorUser).available());
  }

  @Test
  void autoSettlementAt24HoursDoesNotNeedEitherConfirmation() {
    fund();
    now.set(end.plusSeconds(86399));
    service.processDeadlines();
    assertEquals(0, store.wallet(tutorUser).available());
    now.set(end.plusSeconds(86400));
    service.processDeadlines();
    service.processDeadlines();
    assertEquals(270000, store.wallet(tutorUser).available());
    assertTrue(store.find(sessionId).orElseThrow().state().autoAccepted);
  }

  @Test
  void revokedParentAndUnrelatedAccountCannotSpendOrConfirm() {
    when(connections.hasConfirmedParentLink(parentUser, studentId)).thenReturn(false);
    assertThrows(
        RuntimeException.class, () -> service.fund(actor(parentUser, "PARENT"), sessionId, 300000));
    assertEquals(1000000, store.wallet(parentUser).available());
  }

  @Test
  void insufficientFundsRollsBackChargeAndReservation() {
    store.locked(
        () -> {
          store.move(parentUser, -900000, 0, "remove:" + sessionId, "TEST", sessionId);
          return null;
        });
    assertThrows(RuntimeException.class, this::fund);
    assertTrue(store.find(sessionId).isEmpty());
    assertEquals(100000, store.wallet(parentUser).available());
  }

  @Test
  void duplicateDepositCannotCreditTwiceOrReuseBankReference() {
    var first = store.locked(() -> store.createTopup(parentUser, 100000, "MANUAL", end));
    UUID id = (UUID) first.get("id");
    store.locked(
        () -> {
          store.creditTopup(id, "REALBANKREF1", adminUser);
          store.creditTopup(id, "REALBANKREF1", adminUser);
          return null;
        });
    assertEquals(1100000, store.wallet(parentUser).available());
    var second = store.locked(() -> store.createTopup(parentUser, 100000, "MANUAL", end));
    assertThrows(
        RuntimeException.class,
        () ->
            store.locked(
                () -> {
                  store.creditTopup((UUID) second.get("id"), " REALBANKREF1 ", adminUser);
                  return null;
                }));
    assertEquals(1100000, store.wallet(parentUser).available());
  }

  @Test
  void adminCannotChargeACancelledSessionEvenWithMatchingReports() {
    fund();
    dispute();
    service.act(actor(parentUser, "PARENT"), sessionId, "report", "Đồng ý", 100000L);
    service.act(actor(tutorUser, "TUTOR"), sessionId, "report", "Đồng ý", 100000L);
    when(sessions.findSessionById(sessionId))
        .thenReturn(
            Optional.of(
                new SessionSummaryView(
                    sessionId,
                    classId,
                    tutorId,
                    studentId,
                    end.minusSeconds(5400),
                    end,
                    "ONLINE",
                    "CANCELLED",
                    false,
                    end.minusSeconds(7200))));
    assertThrows(
        RuntimeException.class,
        () -> service.review(actor(adminUser, "ADMIN"), sessionId, true, "Duyệt"));
    assertEquals(300000, store.wallet(parentUser).held());
    service.review(actor(adminUser, "ADMIN"), sessionId, false, "Hủy buổi, trả ví");
    assertEquals(1000000, store.wallet(parentUser).available());
  }

  @Test
  void withdrawalRejectionReturnsFundsAndCannotBeProcessedTwice() {
    store.locked(
        () -> {
          store.move(tutorUser, 270000, 0, "tutor-seed:" + sessionId, "TEST", sessionId);
          return null;
        });
    var w =
        store.locked(() -> store.createWithdrawal(tutorUser, 200000, "Bank", "12345678", "Tutor"));
    assertEquals(70000, store.wallet(tutorUser).available());
    store.locked(
        () -> {
          store.reviewWithdrawal((UUID) w.get("id"), false, null, "Sai tài khoản", adminUser);
          return null;
        });
    assertEquals(270000, store.wallet(tutorUser).available());
    assertEquals(0, store.wallet(tutorUser).held());
    assertThrows(
        RuntimeException.class,
        () ->
            store.locked(
                () -> {
                  store.reviewWithdrawal(
                      (UUID) w.get("id"), true, "REFBANK2", "Duyệt lại", adminUser);
                  return null;
                }));
  }

  @Test
  void walletAndAdminHistoriesLoadAndPaginateWithoutAmbiguousColumns() {
    assertTrue(store.adminCharges(0).isEmpty());
    assertTrue(store.adminCharges(50).isEmpty());
    assertTrue(store.topups(parentUser, 0).isEmpty());
    assertTrue(store.withdrawals(tutorUser, 0).isEmpty());
    var topup = store.locked(() -> store.createTopup(parentUser, 100000, "MANUAL", end));
    store.locked(
        () -> {
          store.creditTopup((UUID) topup.get("id"), "HISTORY_TEST", adminUser);
          store.move(tutorUser, 50000, 0, "history-seed", "TEST", sessionId);
          return null;
        });
    store.locked(() -> store.createWithdrawal(tutorUser, 20000, "Bank", "12345678", "Tutor"));
    assertEquals(1, store.topups(parentUser, 0).size());
    assertEquals(1, store.topups(null, 0).size());
    assertTrue(store.topups(studentUser, 0).isEmpty());
    assertTrue(store.topups(parentUser, 50).isEmpty());
    assertEquals(1, store.withdrawals(tutorUser, 0).size());
    assertEquals(1, store.withdrawals(null, 0).size());
    assertTrue(store.withdrawals(parentUser, 0).isEmpty());
    assertTrue(store.withdrawals(tutorUser, 50).isEmpty());
    assertEquals(2, store.entries(parentUser, 0).size());
    assertTrue(store.entries(parentUser, 50).isEmpty());
    assertNotNull(store.topups(parentUser, 0).getFirst().get("created_at"));
  }

  vn.edufit.payment.infra.PayosPayoutGateway payoutGateway;

  /** PaymentSettings bất biến: chỉ bật tài khoản nhận và/hoặc payout giả lập theo nhu cầu từng test. */
  private static vn.edufit.payment.infra.PaymentSettings paymentSettings(String bankAccount, boolean payout) {
    String key = payout ? "test" : "";
    return new vn.edufit.payment.infra.PaymentSettings(
        "", "", "", "", bankAccount, "", "http://localhost:3000", payout, key, key, key);
  }

    PaymentPayoutService payouts() {
    payoutGateway = mock(vn.edufit.payment.infra.PayosPayoutGateway.class);
    var settings = paymentSettings("", true);
    return new PaymentPayoutService(
        store,
        new PaymentAccess(profiles, connections),
        settings,
        payoutGateway,
        new PaymentTime(Clock.fixed(now.get(), ZoneOffset.UTC)));
  }

  UUID requestPayout() {
    return store.locked(
        () -> {
          store.move(tutorUser, 90000, 0, "payout-seed", "TEST", sessionId);
          return (UUID)
              store
                  .createWithdrawal(tutorUser, 50000, "Bank", "12345678", "Tutor", "970422")
                  .get("id");
        });
  }

  com.fasterxml.jackson.databind.JsonNode payoutData(UUID id, String state, long amount) {
    return new com.fasterxml.jackson.databind.ObjectMapper()
        .valueToTree(
            Map.of(
                "id",
                "payout_" + id,
                "referenceId",
                id.toString(),
                "transactions",
                List.of(
                    Map.of(
                        "referenceId",
                        id.toString(),
                        "state",
                        state,
                        "amount",
                        amount,
                        "toBin",
                        "970422",
                        "toAccountNumber",
                        "12345678"))));
  }

  @Test
  void payoutProcessingKeepsFundsAndConcurrentApprovalSubmitsOnlyOnce() throws Exception {
    var p = payouts();
    UUID id = requestPayout();
    when(payoutGateway.create(anyMap())).thenReturn(payoutData(id, "PROCESSING", 50000));
    try (var pool = Executors.newFixedThreadPool(4)) {
      var calls = new ArrayList<Callable<Object>>();
      for (int i = 0; i < 4; i++)
        calls.add(() -> p.approve(actor(adminUser, "ADMIN"), id, "Duyệt"));
      for (var f : pool.invokeAll(calls)) f.get();
    }
    verify(payoutGateway, times(1)).create(anyMap());
    assertEquals(50000, store.wallet(tutorUser).held());
    assertThrows(
        RuntimeException.class,
        () ->
            store.locked(
                () -> {
                  store.reviewWithdrawal(id, true, "MANUAL123", "Sai", adminUser);
                  return null;
                }));
    when(payoutGateway.lookup(anyMap())).thenReturn(payoutData(id, "SUCCEEDED", 50000));
    db.update("update payment_withdrawal set reconcile_after='epoch' where id=?", id);
    p.refresh(actor(adminUser, "ADMIN"), id);
    p.refresh(actor(adminUser, "ADMIN"), id);
    assertEquals("PAID", store.withdrawal(id).get("status"));
    assertEquals(0, store.wallet(tutorUser).held());
    assertEquals(40000, store.wallet(tutorUser).available());
    assertEquals(
        1,
        db.queryForObject(
            "select count(*) from payment_entry where event_key=?",
            Integer.class,
            "withdraw-result:" + id));
  }

  @Test
  void timeoutNeverRefundsOrResubmitsAndVerifiedFailureReturnsFundsOnce() {
    var p = payouts();
    UUID id = requestPayout();
    when(payoutGateway.create(anyMap())).thenThrow(new IllegalStateException("timeout"));
    p.approve(actor(adminUser, "ADMIN"), id, "Duyệt");
    p.approve(actor(adminUser, "ADMIN"), id, "Duyệt lại");
    verify(payoutGateway, times(1)).create(anyMap());
    assertEquals(50000, store.wallet(tutorUser).held());
    when(payoutGateway.lookup(anyMap())).thenReturn(payoutData(id, "FAILED", 50000));
    db.update("update payment_withdrawal set reconcile_after='epoch' where id=?", id);
    p.refresh(actor(adminUser, "ADMIN"), id);
    assertEquals("FAILED", store.withdrawal(id).get("status"));
    assertEquals(90000, store.wallet(tutorUser).available());
    assertEquals(0, store.wallet(tutorUser).held());
  }

  @Test
  void mismatchedPayoutAmountAndUnauthorizedReviewCannotReleaseFunds() {
    var p = payouts();
    UUID id = requestPayout();
    assertThrows(
        RuntimeException.class, () -> p.approve(actor(studentUser, "STUDENT"), id, "Hack"));
    verifyNoInteractions(payoutGateway);
    when(payoutGateway.create(anyMap())).thenReturn(payoutData(id, "SUCCEEDED", 60000));
    p.approve(actor(adminUser, "ADMIN"), id, "Duyệt");
    assertEquals("PROCESSING", store.withdrawal(id).get("status"));
    assertEquals(50000, store.wallet(tutorUser).held());
  }

  @Test
  void notificationAndWalletCommitTogetherAndDuplicateWebhookNotifiesOnlyOnce() {
    var sink = mock(vn.edufit.shared.notification.NotificationSink.class);
    var notified = new PaymentStore(db, new DataSourceTransactionManager(datasource), sink,
        mock(org.springframework.context.ApplicationEventPublisher.class));
    var topup = store.locked(() -> store.createTopup(parentUser, 30000, "PAYOS", end));
    UUID id = (UUID) topup.get("id");
    doThrow(new IllegalStateException("notification unavailable"))
        .when(sink)
        .notify(any(), isNull(), anyString(), anyString(), anyString(), anyString(), any());
    assertThrows(
        RuntimeException.class,
        () ->
            notified.locked(
                () -> {
                  notified.creditTopup(id, "NOTIFYREF1", null);
                  return null;
                }));
    assertEquals("PENDING", store.topup(id).get("status"));
    assertEquals(1000000, store.wallet(parentUser).available());
    reset(sink);
    for (int i = 0; i < 2; i++)
      notified.locked(
          () -> {
            notified.creditTopup(id, "NOTIFYREF1", null);
            return null;
          });
    assertEquals(1030000, store.wallet(parentUser).available());
    verify(sink, times(1))
        .notify(
            eq(parentUser),
            isNull(),
            eq("PAYMENT_TOPUP"),
            anyString(),
            anyString(),
            eq("PAYMENT_WALLET"),
            eq(id));
  }

  @Test
  void upgradesV116WithoutLosingWalletOrPendingWithdrawal() {
    String schema = "payment_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    var ds =
        new DriverManagerDataSource(
            datasource.getUrl().replaceAll("currentSchema=[^&]+", "currentSchema=" + schema),
            datasource.getUsername(),
            datasource.getPassword());
    Flyway old =
        Flyway.configure()
            .dataSource(ds)
            .schemas(schema)
            .locations("classpath:db/migration")
            .target("1.16")
            .load();
    assertEquals(16, old.migrate().migrationsExecuted);
    var jdbc = new JdbcTemplate(ds);
    UUID user =
        jdbc.queryForObject("select user_id from account where role='TUTOR' limit 1", UUID.class);
    UUID id = UUID.randomUUID();
    jdbc.update("insert into payment_wallet(user_id,available,held) values (?,40000,50000)", user);
    jdbc.update(
        "insert into"
            + " payment_withdrawal(id,user_id,amount,status,bank_name,bank_account,bank_holder)"
            + " values (?, ?,50000,'PENDING','Bank','12345678','Tutor')",
        id,
        user);
    var upgraded =
        Flyway.configure()
            .dataSource(ds)
            .schemas(schema)
            .locations("classpath:db/migration")
            .load();
    assertEquals(6, upgraded.migrate().migrationsExecuted);
    upgraded.validate();
    assertEquals(0, upgraded.migrate().migrationsExecuted);
    assertEquals(
        50000,
        jdbc.queryForObject("select held from payment_wallet where user_id=?", Long.class, user));
    assertNull(
        jdbc.queryForMap("select bank_bin from payment_withdrawal where id=?", id).get("bank_bin"));
    jdbc.update("update payment_withdrawal set status='PROCESSING' where id=?", id);
    assertThrows(
        RuntimeException.class,
        () ->
            jdbc.update(
                "insert into"
                    + " payment_withdrawal(id,user_id,amount,status,bank_name,bank_account,bank_holder)"
                    + " values (?, ?,10000,'PENDING','Bank','12345678','Tutor')",
                UUID.randomUUID(),
                user));
  }

  @Test
  void storesTheSignedVirtualAccountAndCreditsItsWebhookOnlyOnce() {
    var topup = store.locked(() -> store.createTopup(parentUser, 100000, "PAYOS", end));
    UUID id = (UUID) topup.get("id");
    long order = ((Number) topup.get("order_code")).longValue();
    store.locked(() -> {
      store.checkout(id, "https://pay.payos.vn/web/virtual-link", "virtual-link", "VQR123456789");
      return null;
    });
    assertEquals("VQR123456789", store.topup(id).get("provider_account_number"));
    assertThrows(RuntimeException.class, () -> store.locked(() -> {
      store.checkout(id, "https://pay.payos.vn/web/virtual-link", "virtual-link", "VQR_DIFFERENT");
      return null;
    }));
    var gateway = mock(vn.edufit.payment.infra.PayosGateway.class);
    var settings = paymentSettings("12345678", false);
    var wallet = new WalletService(store, new PaymentAccess(profiles, connections), settings, gateway,
        new PaymentTime(Clock.fixed(end, ZoneOffset.UTC)));
    when(gateway.verify("virtual-paid")).thenReturn(new vn.edufit.payment.infra.PayosGateway.Receipt(
        order, 100000, "VIRTUALBANKREF", "12345678", "virtual-link", "VND", "VQR123456789"));
    wallet.webhook("virtual-paid");
    wallet.webhook("virtual-paid");
    assertEquals(1100000, store.wallet(parentUser).available());
    assertEquals("PAID", store.topup(id).get("status"));
    assertEquals(1, db.queryForObject(
        "select count(*) from payment_entry where event_key=?", Integer.class, "topup:" + id));
  }

  @Test
  void upgradesV117WithoutInventingAReceivingAccountForExistingTopups() {
    String schema = "payment_va_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    var ds = new DriverManagerDataSource(
        datasource.getUrl().replaceAll("currentSchema=[^&]+", "currentSchema=" + schema),
        datasource.getUsername(), datasource.getPassword());
    Flyway old = Flyway.configure().dataSource(ds).schemas(schema)
        .locations("classpath:db/migration").target("1.17").load();
    assertEquals(17, old.migrate().migrationsExecuted);
    var jdbc = new JdbcTemplate(ds);
    UUID owner = jdbc.queryForObject("select user_id from account where role='PARENT' limit 1", UUID.class);
    UUID id = UUID.randomUUID();
    jdbc.update("insert into payment_topup(id,user_id,amount,provider,status,expires_at)"
        + " values (?,?,100000,'PAYOS','PENDING',now())", id, owner);
    Flyway upgraded = Flyway.configure().dataSource(ds).schemas(schema)
        .locations("classpath:db/migration").load();
    assertEquals(5, upgraded.migrate().migrationsExecuted);
    upgraded.validate();
    assertEquals(0, upgraded.migrate().migrationsExecuted);
    var saved = jdbc.queryForMap("select amount,status,provider_account_number from payment_topup where id=?", id);
    assertEquals(100000L, saved.get("amount"));
    assertEquals("PENDING", saved.get("status"));
    assertNull(saved.get("provider_account_number"));
  }

  @Test
  void savedRecipientIsOwnerScopedAndPendingWithdrawalKeepsItsBankSnapshot() {
    var settings = paymentSettings("", false);
    var wallet = new WalletService(store, new PaymentAccess(profiles, connections), settings,
        mock(vn.edufit.payment.infra.PayosGateway.class), new PaymentTime(Clock.fixed(end, ZoneOffset.UTC)));
    var tutor = actor(tutorUser, "TUTOR");
    var recipient = wallet.savePayoutAccount(tutor, "MBBank", "12345678", "Tutor", "970422", null);
    UUID savedId = (UUID) recipient.get("id");
    assertThrows(RuntimeException.class, () -> wallet.savePayoutAccount(
        actor(studentUser, "STUDENT"), "Bank", "12345678", "Student", "970422", null));
    assertThrows(RuntimeException.class, () -> wallet.withdrawSaved(actor(UUID.randomUUID(), "TUTOR"), 100000, savedId));
    store.locked(() -> { store.move(tutorUser, 270000, 0, "recipient-seed:" + sessionId, "TEST", sessionId); return null; });
    var withdrawal = wallet.withdrawSaved(tutor, 100000, savedId);
    var repeated = wallet.withdrawSaved(tutor, 100000, savedId);
    assertEquals(withdrawal.get("id"), repeated.get("id"));
    assertEquals(100000, store.wallet(tutorUser).held());
    var changed = wallet.savePayoutAccount(tutor, "ACB", "87654321", "Tutor", "970416", savedId);
    assertNotEquals(savedId, changed.get("id"));
    assertThrows(RuntimeException.class, () -> wallet.withdrawSaved(tutor, 100000, savedId));
    assertThrows(RuntimeException.class, () -> wallet.savePayoutAccount(tutor, "Bank", "12345678", "Tutor", "970422", savedId));
    assertEquals("12345678", store.withdrawal((UUID) withdrawal.get("id")).get("bank_account"));
    assertEquals("970422", store.withdrawal((UUID) withdrawal.get("id")).get("bank_bin"));
    assertEquals(170000, store.wallet(tutorUser).available());
    assertEquals(1, store.withdrawals(tutorUser, 0).size());
    assertFalse(wallet.overview(tutor, 0).get("payoutAccount").toString().isBlank());
    assertFalse(wallet.overview(actor(studentUser, "STUDENT"), 0).containsKey("payoutAccount"));
  }

  @Test
  void policyMigrationRemovesOnlyPendingInferredConsent() {
    fund();
    db.update("update payment_charge set learner_accepted=true,tutor_accepted=true,auto_accepted=true where session_id=?", sessionId);
    new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
        new org.springframework.core.io.ClassPathResource("db/migration/payment/V1.19__replace_pending_one_hour_auto_acceptance.sql"))
        .execute(datasource);
    var state = store.find(sessionId).orElseThrow().state();
    assertFalse(state.learnerAccepted);
    assertFalse(state.autoAccepted);
    assertTrue(state.tutorAccepted);
    assertEquals(300000, store.wallet(parentUser).held());
  }

  @Test
  void disputedMoneyStaysFrozenEvenIfSchedulingLaterRecordsAbsence() {
    fund();
    dispute();
    var original = sessions.findSessionById(sessionId).orElseThrow();
    when(sessions.findSessionById(sessionId)).thenReturn(Optional.of(new SessionSummaryView(
        original.sessionId(), original.classId(), original.tutorId(), original.studentId(),
        original.startAt(), original.endAt(), "ONLINE", "ABSENT", false, original.startAt())));
    now.set(end.plusSeconds(86400));
    service.processDeadlines();
    assertEquals("DISPUTED", store.find(sessionId).orElseThrow().state().status);
    assertEquals(300000, store.wallet(parentUser).held());
    now.set(end.plusSeconds(172800));
    service.processDeadlines();
    assertEquals("ADMIN_REVIEW", store.find(sessionId).orElseThrow().state().status);
    assertEquals(300000, store.wallet(parentUser).held());
    service.resolve(actor(adminUser, "ADMIN"), sessionId, 0, "Vắng mặt, hoàn tiền sau đối soát");
    assertEquals(0, store.wallet(parentUser).held());
    assertEquals(1000000, store.wallet(parentUser).available());
  }
  @Test
  void studentCanReportIssuesAndEvidenceButOnlyPayerCanProposeFinancialResolution() {
    fund(); dispute();
    assertThrows(RuntimeException.class, () -> service.act(actor(studentUser, "STUDENT"), sessionId, "report", "Đề nghị", 100000L));
    UUID message = UUID.randomUUID();
    service.comment(actor(studentUser, "STUDENT"), sessionId, message, "Thời gian học thực tế 20 phút");
    service.comment(actor(studentUser, "STUDENT"), sessionId, message, "Thời gian học thực tế 20 phút");
    assertEquals(1, store.caseMessages(sessionId).size());
    assertThrows(RuntimeException.class, () -> service.comment(actor(UUID.randomUUID(), "STUDENT"), sessionId, UUID.randomUUID(), "Sai quyền"));
    now.set(end.plusSeconds(172800)); service.processDeadlines();
    assertEquals(300000, store.wallet(parentUser).held());
    service.resolve(actor(adminUser, "ADMIN"), sessionId, 150000, "Đã xem bằng chứng, tính nửa buổi");
    assertEquals(850000, store.wallet(parentUser).available());
    assertEquals(135000, store.wallet(tutorUser).available());
    assertThrows(RuntimeException.class, () -> service.resolve(actor(adminUser, "ADMIN"), sessionId, 150000, "Lặp"));
  }

  @Test
  void completionRecordedLateCreatesAFullPersisted24HourWindow() {
    var original = sessions.findSessionById(sessionId).orElseThrow();
    when(sessions.findSessionById(sessionId)).thenReturn(Optional.of(new SessionSummaryView(
        original.sessionId(), original.classId(), original.tutorId(), original.studentId(),
        original.startAt(), original.endAt(), "ONLINE", "SCHEDULED", false, original.startAt())));
    service.fund(actor(parentUser, "PARENT"), sessionId, 300000);
    now.set(end.plusSeconds(90000));
    when(sessions.findSessionById(sessionId)).thenReturn(Optional.of(new SessionSummaryView(
        original.sessionId(), original.classId(), original.tutorId(), original.studentId(),
        original.startAt(), original.endAt(), "ONLINE", "COMPLETED", false, original.startAt(), now.get())));
    service.outcomeRecorded(sessionId, now.get());
    service.outcomeRecorded(sessionId, now.get().plusSeconds(10));
    assertEquals(now.get().plusSeconds(86400), store.find(sessionId).orElseThrow().state().reviewDeadline());
    service.processDeadlines();
    assertEquals(300000, store.wallet(parentUser).held());
    now.set(now.get().plusSeconds(86400)); service.processDeadlines();
    assertEquals(270000, store.wallet(tutorUser).available());
  }
  @Test
  void aNotificationFailureRollsBackOpeningTheReviewWindow() {
    fund();
    db.update("update payment_charge set status='FUNDED',review_started_at=null where session_id=?", sessionId);
    var guarded = new PaymentStore(db, new DataSourceTransactionManager(datasource),
        (user, student, type, title, text, target, id) -> {}, event -> { throw new IllegalStateException("Notification persistence failed"); });
    var app = new SessionPaymentService(guarded, new PaymentAccess(profiles, connections), sessions, profiles, connections, new PaymentTime(Clock.fixed(end, ZoneOffset.UTC)));
    assertThrows(RuntimeException.class, () -> app.outcomeRecorded(sessionId, end));
    assertNull(store.find(sessionId).orElseThrow().state().reviewStartedAt);
    assertEquals(300000,store.wallet(parentUser).held());
  }
}

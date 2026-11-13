package vn.edufit.db;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import vn.edufit.payment.api.event.TuitionTopupNeededEvent;
import vn.edufit.payment.api.event.TuitionUnpaidForTutorEvent;
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
  org.springframework.context.ApplicationEventPublisher events;
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
    assertEquals(MigrationScripts.count(MigrationScripts.MIGRATION), flyway.migrate().migrationsExecuted);
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
    events = mock(org.springframework.context.ApplicationEventPublisher.class);
    store = new PaymentStore(db, new DataSourceTransactionManager(datasource), events);
    db.execute(
        "truncate"
            + " payment_entry,payment_audit,payment_charge,payment_topup,payment_withdrawal,payment_wallet,payment_payout_account,payment_case_message");
    if (db.queryForObject("select count(*) from class", Long.class) == 0) {
      // Chỉ chạy migration thật (không seed demo): tự tạo học sinh, gia sư, nhu cầu học (V1.25) và lớp.
      UUID sender = UUID.randomUUID();
      UUID tutorAccount = UUID.randomUUID();
      UUID student = UUID.randomUUID();
      UUID tutor = UUID.randomUUID();
      UUID need = UUID.randomUUID();
      db.update(
          "insert into account(user_id,email,full_name,role) values"
              + " (?,?,'Payment Flow Student','STUDENT'),(?,?,'Payment Flow Tutor','TUTOR'),"
              + " (gen_random_uuid(),'parent@payment-flow.test','Payment Flow Parent','PARENT'),"
              + " (gen_random_uuid(),'admin@payment-flow.test','Payment Flow Admin','ADMIN')",
          sender, sender + "@payment-flow.test", tutorAccount, tutorAccount + "@payment-flow.test");
      db.update("insert into student_profile(student_id,user_id) values (?,?)", student, sender);
      db.update(
          "insert into tutor_profile(tutor_id,user_id,display_name,teaching_mode)"
              + " values (?,?,'Payment Flow Tutor','ONLINE')",
          tutor, tutorAccount);
      db.update(
          "insert into learning_need(need_id,student_id,created_by,subject_id)"
              + " values (?,?,?,(select min(subject_id) from subject))",
          need, student, sender);
      UUID request = UUID.randomUUID();
      db.update(
          "insert into"
              + " connection_request(request_id,student_id,tutor_id,need_id,sent_by_user_id,status,expires_at)"
              + " values (?,?,?,?,?,'ACCEPTED',now())",
          request,
          student,
          tutor,
          need,
          sender);
      db.update(
          "insert into class(connection_request_id,student_id,tutor_id,need_id,price_per_session)"
              + " values (?,?,?,?," + PRICE + ")",
          request,
          student,
          tutor,
          need);
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
    // Học phí mỗi buổi chốt trên lớp (SessionPaymentService.classPrice)
    when(connections.findClassById(classId))
        .thenReturn(Optional.of(new ConnectionFacade.ClassDetailView(
            classId, studentId, tutorId, null, "ACTIVE", end.minusSeconds(864000), null, 300000)));
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

  /** Schema nâng cấp chỉ có migration (không có seed) nên test tự tạo tài khoản cần dùng. */
  static UUID account(JdbcTemplate jdbc, String role) {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into account(user_id,email,password_hash,full_name,role) values (?,?,?,?,?)",
        id, id + "@test.local", "x", role, role);
    return id;
  }

  CurrentUser actor(UUID id, String role) {
    var a = mock(CurrentUser.class);
    when(a.getUserId()).thenReturn(id);
    when(a.hasRole(anyString())).thenAnswer(i -> role.equals(i.getArgument(0)));
    return a;
  }

  /** Hệ thống tự thu học phí của buổi {@link #sessionId} (job thu học phí), không còn người học bấm giữ tiền. */
  void collect() {
    var view = sessions.findSessionById(sessionId).orElseThrow();
    when(profiles.findStudentById(studentId))
        .thenReturn(Optional.of(new StudentSummaryDto(studentId, studentUser, null, null, null, true)));
    when(connections.findConfirmedParentUserIds(studentId)).thenReturn(List.of(parentUser));
    doReturn(List.of(view)).when(sessions).findScheduledSessionsStartingBetween(any(), any());
    service.collectTuition();
    assertTrue(store.find(sessionId).isPresent(), "Job phải thu được học phí của buổi này");
  }

  void fund() {
    collect();
    now.set(end);
  }

  @Test void unpaidLessonCanStartAndStartingTwiceNeverChargesTwice() {
    var source = new SessionSummaryView(sessionId,classId,tutorId,studentId,end.minusSeconds(5400),end,
        "ONLINE","SCHEDULED",false,end.minusSeconds(7200));
    when(sessions.findSessionById(sessionId)).thenReturn(Optional.of(source));
    when(sessions.lockSessionById(sessionId)).thenReturn(Optional.of(source));
    when(sessions.findMeetingLink(sessionId)).thenReturn(Optional.of("https://meet.google.com/defense-demo"));
    db.update("update tutoring_session set status='SCHEDULED' where session_id=?",sessionId);
    var lesson = new PaymentLessonService(store,service,sessions,new PaymentTime(Clock.fixed(now.get(),ZoneOffset.UTC)));
    var first = lesson.start(actor(studentUser,"STUDENT"),sessionId);
    assertFalse(first.funded(), "Chưa thu học phí vẫn bắt đầu học được");
    assertEquals("https://meet.google.com/defense-demo", first.meetingLink());
    assertTrue(store.find(sessionId).isEmpty(), "Bắt đầu học không tự lấy tiền");
    collect();
    var balance = store.wallet(parentUser);
    lesson.start(actor(tutorUser,"TUTOR"),sessionId);
    assertEquals(balance,store.wallet(parentUser));
    assertEquals(1,db.queryForObject("select count(*) from payment_audit where reference_id=? and action='LESSON_STARTED'",Integer.class,sessionId));
    when(connections.hasConfirmedParentLink(parentUser,studentId)).thenReturn(false);
    assertEquals(parentUser,service.get(actor(parentUser,"PARENT"),sessionId).payerId());
    assertEquals(List.of(classId),store.payerClassIds(parentUser));
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
  void revokedParentWalletIsNeverCollected() {
    when(connections.hasConfirmedParentLink(parentUser, studentId)).thenReturn(false);
    when(connections.findConfirmedParentUserIds(studentId)).thenReturn(List.of());
    when(profiles.findStudentById(studentId))
        .thenReturn(Optional.of(new StudentSummaryDto(studentId, studentUser, null, null, null, true)));
    doReturn(List.of(sessions.findSessionById(sessionId).orElseThrow()))
        .when(sessions).findScheduledSessionsStartingBetween(any(), any());
    service.collectTuition();
    assertEquals(1000000, store.wallet(parentUser).available());
    assertTrue(store.find(sessionId).map(c -> !c.payerId().equals(parentUser)).orElse(true));
  }

  @Test
  void insufficientFundsLeavesTheWalletUntouchedAndCreatesNoCharge() {
    store.locked(
        () -> {
          store.move(parentUser, -900000, 0, "remove:" + sessionId, "TEST", sessionId);
          return null;
        });
    when(profiles.findStudentById(studentId))
        .thenReturn(Optional.of(new StudentSummaryDto(studentId, studentUser, null, null, null, true)));
    when(connections.findConfirmedParentUserIds(studentId)).thenReturn(List.of(parentUser));
    doReturn(List.of(sessions.findSessionById(sessionId).orElseThrow()))
        .when(sessions).findScheduledSessionsStartingBetween(any(), any());
    service.collectTuition();
    assertTrue(store.find(sessionId).isEmpty(), "Ví thiếu tiền thì không tạo khoản học phí");
    assertEquals(100000, store.wallet(parentUser).available());
    assertEquals(0, store.wallet(parentUser).held());
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

  // ===== Thu học phí: hệ thống tự thu và chỉ thông báo, không bao giờ hủy buổi học =====

  /** Học phí mỗi buổi của lớp trong dữ liệu dựng ở {@code setUp}. */
  static final long PRICE = 300000;
  static final long HOUR = 3600;

  /** Buổi SCHEDULED {@link #sessionId}, bắt đầu sau {@code seconds} giây tính từ đồng hồ thanh toán. */
  SessionSummaryView scheduledIn(long seconds) {
    return stub(sessionId, now.get().plusSeconds(seconds), "SCHEDULED");
  }

  /** Buổi đã COMPLETED, kết thúc {@code secondsAgo} giây trước. */
  SessionSummaryView completedAgo(UUID id, long secondsAgo) {
    Instant ended = now.get().minusSeconds(secondsAgo);
    var view = stub(id, ended.minusSeconds(5400), "COMPLETED");
    service.outcomeRecorded(id, ended); // gia sư ghi nhận hoàn thành: đánh dấu buổi chưa thu để job thu sau
    return view;
  }

  final Map<UUID, SessionSummaryView> stubbed = new HashMap<>();

  /** Giả lập module lịch học cho các truy vấn mà job thu học phí dùng. */
  SessionSummaryView stub(UUID id, Instant start, String status) {
    var view = new SessionSummaryView(id, classId, tutorId, studentId, start, start.plusSeconds(5400),
        "ONLINE", status, false, start.minusSeconds(86400));
    stubbed.put(id, view);
    when(sessions.findSessionById(id)).thenReturn(Optional.of(view));
    // doAnswer: gọi lại helper không kích hoạt answer cũ với tham số null.
    doAnswer(i -> {
      Instant from = i.getArgument(0), to = i.getArgument(1);
      return stubbed.values().stream().filter(v -> v.status().equals("SCHEDULED")
          && !v.startAt().isBefore(from) && v.startAt().isBefore(to)).toList();
    }).when(sessions).findScheduledSessionsStartingBetween(any(), any());
    doAnswer(i -> {
      Collection<UUID> ids = i.getArgument(0);
      return stubbed.values().stream().filter(v -> ids.contains(v.sessionId())).toList();
    }).when(sessions).findSessionsByIds(any());
    when(profiles.findStudentById(studentId))
        .thenReturn(Optional.of(new StudentSummaryDto(studentId, studentUser, null, null, null, true)));
    when(connections.findConfirmedParentUserIds(studentId)).thenReturn(List.of(parentUser));
    return view;
  }

  /** Thêm một buổi học thật vào CSDL (khoản học phí tham chiếu tới buổi học). */
  UUID insertSession(Instant start) {
    UUID id = UUID.randomUUID();
    db.update("insert into tutoring_session(session_id,class_id,tutor_id,student_id,start_at,end_at,mode,status,"
            + "proposed_by,expires_at) values (?,?,?,?,?,?,'ONLINE','COMPLETED',?,?)",
        id, classId, tutorId, studentId, Timestamp.from(start), Timestamp.from(start.plusSeconds(5400)),
        tutorUser, Timestamp.from(start));
    return id;
  }

  void setAvailable(UUID user, long amount) {
    store.locked(() -> {
      long current = store.wallet(user).available();
      if (current != amount)
        store.move(user, amount - current, 0, "adjust:" + user + ":" + UUID.randomUUID(), "TEST", sessionId);
      return null;
    });
  }

  <T> List<T> published(Class<T> type) {
    var captured = org.mockito.ArgumentCaptor.forClass(Object.class);
    verify(events, atLeast(0)).publishEvent(captured.capture());
    return captured.getAllValues().stream().filter(type::isInstance).map(type::cast).toList();
  }

  @Test
  void jobCollectsDueTuitionFromTheLinkedParentWalletOnlyOnce() {
    scheduledIn(2 * HOUR);
    long before = store.wallet(parentUser).available();
    service.collectTuition();
    service.collectTuition();
    assertEquals(parentUser, store.find(sessionId).orElseThrow().payerId());
    assertEquals(before - PRICE, store.wallet(parentUser).available());
    assertEquals(PRICE, store.wallet(parentUser).held(), "Chạy job hai lần vẫn chỉ thu một lần");
    assertTrue(published(TuitionTopupNeededEvent.class).isEmpty());
  }

  @Test
  void jobFallsBackToTheStudentWalletWhenTheParentCannotPay() {
    scheduledIn(2 * HOUR);
    setAvailable(parentUser, PRICE - 1);
    setAvailable(studentUser, PRICE);
    service.collectTuition();
    assertEquals(studentUser, store.find(sessionId).orElseThrow().payerId());
    assertEquals(PRICE - 1, store.wallet(parentUser).available());
    assertEquals(PRICE, store.wallet(studentUser).held());
  }

  @Test
  void unpaidDueSessionIsNeverCancelledOnlyNotifiedOnceAndCollectedAfterTopUp() {
    scheduledIn(2 * HOUR);
    long partial = PRICE / 3;
    setAvailable(parentUser, partial);
    service.collectTuition();
    service.collectTuition();
    assertTrue(store.find(sessionId).isEmpty());
    assertEquals(1, db.queryForObject("select count(*) from tutoring_session where session_id=?"
        + " and status='COMPLETED'", Integer.class, sessionId), "Buổi học không bị hệ thống đổi trạng thái");
    var learners = published(TuitionTopupNeededEvent.class);
    assertEquals(Set.of(parentUser, studentUser),
        learners.stream().map(TuitionTopupNeededEvent::userId).collect(java.util.stream.Collectors.toSet()));
    assertEquals(2, learners.size(), "Mỗi ví nhận một thông báo dù job chạy hai lần");
    assertTrue(learners.stream().allMatch(e -> e.stage() == TuitionTopupNeededEvent.Stage.DUE));
    assertEquals(PRICE - partial, learners.getFirst().shortfall());
    var tutor = published(TuitionUnpaidForTutorEvent.class);
    assertEquals(List.of(tutorUser), tutor.stream().map(TuitionUnpaidForTutorEvent::tutorUserId).toList());

    setAvailable(parentUser, PRICE);
    service.collectTuition();
    assertEquals(PRICE, store.wallet(parentUser).held(), "Nạp đủ thì lần chạy sau tự thu");
  }

  @Test
  void completedUnpaidSessionIsCollectedAfterwardsWithAFull24HourReview() {
    completedAgo(sessionId, HOUR);
    setAvailable(parentUser, 0);
    service.collectTuition();
    assertTrue(store.find(sessionId).isEmpty());
    var notices = published(TuitionTopupNeededEvent.class);
    assertFalse(notices.isEmpty());
    assertTrue(notices.stream().allMatch(e -> e.stage() == TuitionTopupNeededEvent.Stage.OVERDUE));
    assertTrue(published(TuitionUnpaidForTutorEvent.class).isEmpty(), "Buổi đã dạy: không báo gia sư");

    now.set(now.get().plusSeconds(5 * HOUR));
    setAvailable(parentUser, PRICE);
    service.collectTuition();
    var charge = store.find(sessionId).orElseThrow();
    assertEquals(now.get().plusSeconds(vn.edufit.payment.domain.SessionCharge.CONFIRMATION_SECONDS),
        charge.state().reviewDeadline(), "Người học có đủ 24 giờ phản hồi tính từ lúc thu");
    service.processDeadlines();
    assertEquals(PRICE, store.wallet(parentUser).held(), "Chưa hết 24 giờ thì chưa chuyển cho gia sư");
  }

  @Test
  void theOldestUnpaidSessionIsCollectedFirst() {
    UUID newer = sessionId;
    UUID older = insertSession(now.get().minusSeconds(48 * HOUR));
    completedAgo(older, 46 * HOUR);
    completedAgo(newer, HOUR);
    setAvailable(parentUser, PRICE);
    service.collectTuition();
    assertTrue(store.find(older).isPresent(), "Ví chỉ đủ một buổi: thu buổi cũ trước");
    assertTrue(store.find(newer).isEmpty());
  }

  @Test
  void sessionsBeyond24HoursAreNotCollectedButShortWalletsAreRemindedOnce() {
    scheduledIn(48 * HOUR);
    setAvailable(parentUser, PRICE / 3);
    service.collectTuition();
    service.collectTuition();
    assertTrue(store.find(sessionId).isEmpty());
    var reminders = published(TuitionTopupNeededEvent.class);
    assertEquals(2, reminders.size(), "Nhắc phụ huynh và học sinh, mỗi người một lần");
    assertTrue(reminders.stream().allMatch(e -> e.stage() == TuitionTopupNeededEvent.Stage.UPCOMING));
    assertEquals(stubbed.get(sessionId).startAt().minusSeconds(SessionPaymentService.DUE_BEFORE_SECONDS),
        reminders.getFirst().dueAt());
    assertTrue(published(TuitionUnpaidForTutorEvent.class).isEmpty());
  }

  @Test
  void remindersCountEarlierSessionsThatShareTheSameWallet() {
    UUID second = insertSession(now.get().plusSeconds(60 * HOUR));
    scheduledIn(48 * HOUR);
    stub(second, now.get().plusSeconds(60 * HOUR), "SCHEDULED");
    setAvailable(parentUser, PRICE);
    setAvailable(studentUser, 0);
    service.collectTuition();
    var reminders = published(TuitionTopupNeededEvent.class);
    assertTrue(reminders.stream().allMatch(e -> e.sessionId().equals(second)),
        "Ví đủ cho buổi đầu nên chỉ nhắc buổi sau");
    assertEquals(PRICE, reminders.getFirst().shortfall(), "Thiếu đúng học phí của buổi thứ hai");
  }

  @Test
  void reschedulingKeepsTheCollectedChargeOnTheSameSession() {
    scheduledIn(2 * HOUR);
    service.collectTuition();
    scheduledIn(10 * HOUR);
    service.collectTuition();
    assertEquals(PRICE, store.wallet(parentUser).held(), "Khoản đã thu đi theo buổi, không thu thêm");
  }

  /** Giới hạn orderCode của payOS (số nguyên an toàn của JavaScript). */
  static final long PAYOS_MAX_ORDER_CODE = 9_007_199_254_740_991L;

  long orderCode(Map<String, Object> topup) {
    return ((Number) topup.get("order_code")).longValue();
  }

  @Test
  void orderCodesNeverRestartAfterSequenceResetOrBackupRestore() {
    long startedAt = Instant.now().minusSeconds(60).toEpochMilli() * 1000;
    long first = orderCode(store.locked(() -> store.createTopup(parentUser, 100000, "PAYOS", end)));
    assertTrue(first > startedAt, "Mã đơn gắn với thời gian, không bắt đầu từ 1");
    assertTrue(first < PAYOS_MAX_ORDER_CODE, "Mã đơn nằm trong giới hạn payOS");
    // CSDL mới hoặc sequence bị đặt lại vẫn không cấp lại mã nhỏ.
    db.execute("alter sequence payment_topup_order_code_seq restart with 1");
    long afterReset = orderCode(store.locked(() -> store.createTopup(parentUser, 100000, "PAYOS", end)));
    assertTrue(afterReset > first);
    // Bản sao lưu chứa mã lớn hơn đồng hồ hiện tại: mã mới vẫn lớn hơn mọi mã đã có.
    long restored = afterReset + 1_000_000_000L;
    db.update("insert into payment_topup(id,order_code,user_id,amount,provider,status,expires_at)"
        + " values (?,?,?,100000,'PAYOS','PAID',now())", UUID.randomUUID(), restored, parentUser);
    long afterRestore = orderCode(store.locked(() -> store.createTopup(parentUser, 100000, "PAYOS", end)));
    assertTrue(afterRestore > restored);
  }

  @Test
  void concurrentTopupsReceiveDistinctOrderCodes() throws Exception {
    int requests = 16;
    var pool = Executors.newFixedThreadPool(8);
    try {
      var start = new CountDownLatch(1);
      var futures = new ArrayList<Future<Long>>();
      for (int i = 0; i < requests; i++)
        futures.add(pool.submit(() -> {
          start.await();
          return orderCode(store.locked(() -> store.createTopup(parentUser, 100000, "PAYOS", end)));
        }));
      start.countDown();
      var codes = new HashSet<Long>();
      for (var f : futures) codes.add(f.get(30, TimeUnit.SECONDS));
      assertEquals(requests, codes.size());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void orderCodeIsRenewedOnlyBeforePayosIssuedALink() {
    var topup = store.locked(() -> store.createTopup(parentUser, 100000, "PAYOS", end));
    UUID id = (UUID) topup.get("id");
    long renewed = store.locked(() -> store.renewOrderCode(id));
    assertTrue(renewed > orderCode(topup));
    assertEquals(renewed, orderCode(store.topup(id)));
    store.locked(() -> {
      store.checkout(id, "https://pay.payos.vn/web/link-1", "link-1", "VA1");
      return null;
    });
    assertThrows(RuntimeException.class, () -> store.locked(() -> store.renewOrderCode(id)));
    assertEquals(renewed, orderCode(store.topup(id)));
  }

  @Test
  void cancelledTopupNeverCreditsAndCancelDoesNotTouchPaidTopups() {
    var open = store.locked(() -> store.createTopup(parentUser, 100000, "PAYOS", end));
    UUID openId = (UUID) open.get("id");
    assertTrue(store.locked(() -> store.cancelTopup(openId)));
    assertEquals("CANCELLED", store.topup(openId).get("status"));
    assertThrows(RuntimeException.class, () -> store.locked(() -> {
      store.creditTopup(openId, "LATEREF1", null);
      return null;
    }));
    var paid = store.locked(() -> store.createTopup(parentUser, 100000, "PAYOS", end));
    UUID paidId = (UUID) paid.get("id");
    store.locked(() -> {
      store.creditTopup(paidId, "PAIDREF1", null);
      return null;
    });
    assertFalse(store.locked(() -> store.cancelTopup(paidId)));
    assertEquals("PAID", store.topup(paidId).get("status"));
  }

  @Test
  void webhookAndReconciliationTogetherCreditATopupOnlyOnce() {
    var topup = store.locked(() -> store.createTopup(parentUser, 100000, "PAYOS", end));
    UUID id = (UUID) topup.get("id");
    store.locked(() -> {
      store.checkout(id, "https://pay.payos.vn/web/link-once", "link-once", "VA1");
      return null;
    });
    for (int i = 0; i < 2; i++)
      store.locked(() -> {
        store.creditTopup(id, "ONCEREF1", null);
        return null;
      });
    assertThrows(RuntimeException.class, () -> store.locked(() -> {
      store.creditTopup(id, "OTHERREF", null);
      return null;
    }));
    assertEquals(1100000, store.wallet(parentUser).available());
    assertEquals(1, db.queryForObject(
        "select count(*) from payment_entry where event_key=?", Integer.class, "topup:" + id));
  }

  @Test
  void historyListsOnlyCreditedTopupsAndSessionPaymentsInSeparateSections() {
    var paid = store.locked(() -> store.createTopup(parentUser, 200000, "PAYOS", end));
    UUID paidId = (UUID) paid.get("id");
    store.locked(() -> {
      store.checkout(paidId, "https://pay.payos.vn/web/link-paid", "link-paid", "VA1");
      store.creditTopup(paidId, "BANKREF77", null);
      return null;
    });
    store.locked(() -> store.createTopup(parentUser, 100000, "PAYOS", end));
    var cancelled = store.locked(() -> store.createTopup(parentUser, 100000, "PAYOS", end));
    store.locked(() -> store.cancelTopup((UUID) cancelled.get("id")));
    fund();

    var topups = store.topupHistory(parentUser, 0);
    assertEquals(1, topups.size(), "Đơn đang chờ hoặc đã hủy không vào lịch sử nạp");
    var row = topups.getFirst();
    assertEquals(paidId, row.get("id"));
    assertEquals(orderCode(paid), ((Number) row.get("order_code")).longValue());
    assertEquals("link-paid", row.get("payment_link_id"));
    assertEquals("BANKREF77", row.get("bank_reference"));
    assertEquals(200000L, ((Number) row.get("amount")).longValue());
    assertNotNull(row.get("paid_at"));
    assertTrue(store.topupHistory(studentUser, 0).isEmpty());
    assertTrue(store.topupHistory(parentUser, 50).isEmpty());

    assertTrue(store.sessionHistory(parentUser, 0).isEmpty(), "Giữ học phí là cơ chế nội bộ, không vào lịch sử");
    assertTrue(store.entries(parentUser, 0).stream().noneMatch(e -> "SESSION_RESERVED".equals(e.get("kind"))),
        "Hoạt động gần đây không hiện bút toán giữ học phí");
    confirmTutor();
    service.act(actor(studentUser, "STUDENT"), sessionId, "confirm", null, null);
    now.set(end.plusSeconds(86400));
    service.processDeadlines();

    var payments = store.sessionHistory(parentUser, 0);
    assertEquals(1, payments.size(), "Chỉ có khoản đã thanh toán, không lẫn nạp tiền hay giữ học phí");
    assertEquals("SESSION_SETTLED", payments.getFirst().get("kind"));
    assertEquals(sessionId, payments.getFirst().get("session_id"));
    assertEquals(0L, ((Number) payments.getFirst().get("available_delta")).longValue());
    assertEquals(-300000L, ((Number) payments.getFirst().get("held_delta")).longValue());
    assertNotNull(payments.getFirst().get("session_end_at"));
    assertTrue(store.sessionHistory(parentUser, 50).isEmpty());
    assertEquals("TUITION_INCOME", store.sessionHistory(tutorUser, 0).getFirst().get("kind"));
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
    var sink = mock(org.springframework.context.ApplicationEventPublisher.class);
    var notified = new PaymentStore(db, new DataSourceTransactionManager(datasource), sink);
    var topup = store.locked(() -> store.createTopup(parentUser, 30000, "PAYOS", end));
    UUID id = (UUID) topup.get("id");
    doThrow(new IllegalStateException("notification unavailable"))
        .when(sink)
        .publishEvent(any(Object.class));
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
    var published = org.mockito.ArgumentCaptor.forClass(Object.class);
    verify(sink, times(1)).publishEvent(published.capture());
    var event = (vn.edufit.payment.api.event.WalletBalanceChangedEvent) published.getValue();
    assertEquals(parentUser, event.userId());
    assertEquals("TOPUP", event.kind());
    assertEquals(id, event.referenceId());
    assertEquals(30000, event.availableDelta());
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
    assertEquals(MigrationScripts.countUpTo("1.16", MigrationScripts.MIGRATION),
        old.migrate().migrationsExecuted);
    var jdbc = new JdbcTemplate(ds);
    UUID user = account(jdbc, "TUTOR");
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
            .target(MigrationScripts.schemaHead())
            .load();
    assertEquals(MigrationScripts.countAfter("1.16", MigrationScripts.MIGRATION),
        upgraded.migrate().migrationsExecuted);
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
        .locations("classpath:db/migration", "classpath:db/seed").target("1.17").load();
    assertEquals(MigrationScripts.countUpTo("1.17", MigrationScripts.MIGRATION, MigrationScripts.SEED),
        old.migrate().migrationsExecuted);
    var jdbc = new JdbcTemplate(ds);
    UUID owner = account(jdbc, "PARENT");
    UUID id = UUID.randomUUID();
    jdbc.update("insert into payment_topup(id,user_id,amount,provider,status,expires_at)"
        + " values (?,?,100000,'PAYOS','PENDING',now())", id, owner);
    Flyway upgraded = Flyway.configure().dataSource(ds).schemas(schema)
        .locations("classpath:db/migration", "classpath:db/seed")
        .target(MigrationScripts.schemaHead()).load();
    assertEquals(MigrationScripts.countAfter("1.17", MigrationScripts.MIGRATION, MigrationScripts.SEED),
        upgraded.migrate().migrationsExecuted);
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
    collect();
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
        event -> { throw new IllegalStateException("Notification persistence failed"); });
    var app = new SessionPaymentService(guarded, new PaymentAccess(profiles, connections), sessions, profiles, connections, new PaymentTime(Clock.fixed(end, ZoneOffset.UTC)));
    assertThrows(RuntimeException.class, () -> app.outcomeRecorded(sessionId, end));
    assertNull(store.find(sessionId).orElseThrow().state().reviewStartedAt);
    assertEquals(300000,store.wallet(parentUser).held());
  }
}

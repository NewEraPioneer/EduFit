package vn.edufit.ai;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.edufit.ai.api.AiUnavailableException;
import vn.edufit.ai.application.AiMatchQuotaService;
import vn.edufit.ai.infra.config.AiProperties;
import vn.edufit.ai.infra.persistence.AiMatchReservationRepository;

@Testcontainers
class AiQuotaPostgresTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  static DataSource source;
  JdbcTemplate jdbc;
  DataSourceTransactionManager manager;
  MutableClock clock;
  AiMatchQuotaService quota;
  UUID actor;

  @BeforeAll
  static void database() {
    source =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
  }

  @BeforeEach
  void setup() {
    jdbc = new JdbcTemplate(source);
    jdbc.execute("drop trigger if exists reject_reservation on ai_match_reservation");
    jdbc.execute("truncate account cascade");
    manager = new DataSourceTransactionManager(source);
    clock = new MutableClock(Instant.parse("2026-10-12T12:00:00Z"));
    quota = restartedService();
    actor = account("STUDENT");
  }

  @Test
  void tenthAttemptAllowedAndEleventhDeniedWithoutRefundingFailures() {
    for (int i = 0; i < 10; i++) {
      UUID token = quota.tryReserve(actor).orElseThrow();
      assertThat(quota.finish(token, i % 2 == 0 ? "ERROR" : "TIMEOUT")).isTrue();
    }
    assertThat(quota.tryReserve(actor)).isEmpty();
    assertThat(count()).isEqualTo(10);
  }

  @Test
  void twoConcurrentRequestsAtNineAllowOnlyOneReservation() throws Exception {
    for (int i = 0; i < 9; i++) quota.tryReserve(actor).orElseThrow();
    var barrier = new CyclicBarrier(2);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Callable<Boolean> attempt =
          () -> {
            barrier.await(5, TimeUnit.SECONDS);
            return restartedService().tryReserve(actor).isPresent();
          };
      var a = executor.submit(attempt);
      var b = executor.submit(attempt);
      assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(count()).isEqualTo(10);
  }

  @Test
  void concurrentFirstReservationsDoNotLoseAttemptsOnActorCreation() throws Exception {
    var barrier = new CyclicBarrier(12);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 12; i++)
        futures.add(
            executor.submit(
                () -> {
                  barrier.await(10, TimeUnit.SECONDS);
                  return restartedService().tryReserve(actor).isPresent();
                }));
      int allowed = 0;
      for (var future : futures) if (future.get(20, TimeUnit.SECONDS)) allowed++;
      assertThat(allowed).isEqualTo(10);
    }
    assertThat(count()).isEqualTo(10);
  }

  @Test
  void rollingBoundaryExcludesExactlyTwentyFourHoursAndUsesReservationTime() {
    Instant first = clock.instant();
    for (int i = 0; i < 10; i++) quota.tryReserve(actor).orElseThrow();
    clock.now = first.plus(Duration.ofHours(24)).minusNanos(1000);
    assertThat(quota.tryReserve(actor)).isEmpty();
    clock.now = first.plus(Duration.ofHours(24));
    assertThat(quota.tryReserve(actor)).isPresent();
    assertThat(count()).isEqualTo(11);
  }

  @Test
  void studentAndParentHaveSeparateActorQuota() {
    UUID parent = account("PARENT");
    for (int i = 0; i < 10; i++) quota.tryReserve(actor).orElseThrow();
    assertThat(quota.tryReserve(actor)).isEmpty();
    assertThat(quota.tryReserve(parent)).isPresent();
  }

  @Test
  void durableAttemptSurvivesOuterRollbackAndServiceRestart() {
    assertThatThrownBy(
            () ->
                new TransactionTemplate(manager)
                    .executeWithoutResult(
                        tx -> {
                          quota.tryReserve(actor).orElseThrow();
                          throw new IllegalStateException("outer rollback");
                        }))
        .isInstanceOf(IllegalStateException.class);
    for (int i = 0; i < 9; i++) restartedService().tryReserve(actor).orElseThrow();
    assertThat(restartedService().tryReserve(actor)).isEmpty();
    assertThat(count()).isEqualTo(10);
  }

  @Test
  void staleReservationExpiresAcrossRestartButDoesNotRefundQuotaOrAcceptLateSuccess() {
    UUID token = quota.tryReserve(actor).orElseThrow();
    for (int i = 0; i < 9; i++) quota.tryReserve(actor).orElseThrow();
    clock.now = clock.instant().plusSeconds(45);
    assertThat(restartedService().tryReserve(actor)).isEmpty();
    assertThat(jdbc.queryForList("select status from ai_match_reservation", String.class))
        .containsOnly("EXPIRED");
    assertThat(quota.finish(token, "SUCCESS")).isFalse();
    assertThat(count()).isEqualTo(10);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUCCESS", "ERROR", "TIMEOUT"})
  void terminalOutcomeIsWrittenOnce(String outcome) {
    UUID token = quota.tryReserve(actor).orElseThrow();
    assertThat(quota.finish(token, outcome)).isTrue();
    assertThat(quota.finish(token, "ERROR")).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select status from ai_match_reservation where reservation_id=?",
                String.class,
                token))
        .isEqualTo(outcome);
  }

  @Test
  void expiredLeaseCannotBeCompletedEvenWithoutAnotherReservation() {
    UUID token = quota.tryReserve(actor).orElseThrow();
    clock.now = clock.instant().plusSeconds(46);
    assertThat(quota.finish(token, "SUCCESS")).isFalse();
    assertThat(jdbc.queryForObject("select status from ai_match_reservation", String.class))
        .isEqualTo("EXPIRED");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedReservationInsertOrCommitRollsBackAndNeverGrantsAdmission(boolean atCommit) {
    jdbc.execute(
        "create or replace function reject_ai_reservation() returns trigger language plpgsql as $$"
            + " begin raise exception 'injected reservation failure'; end $$");
    jdbc.execute(
        atCommit
            ? "create constraint trigger reject_reservation after insert on ai_match_reservation"
                  + " deferrable initially deferred for each row execute function"
                  + " reject_ai_reservation()"
            : "create trigger reject_reservation before insert on ai_match_reservation for each row"
                  + " execute function reject_ai_reservation()");
    assertThatThrownBy(() -> quota.tryReserve(actor))
        .isInstanceOf(AiUnavailableException.class)
        .hasNoCause();
    assertThat(count()).isZero();
    jdbc.execute("drop trigger reject_reservation on ai_match_reservation");
    assertThat(quota.tryReserve(actor)).isPresent();
  }

  @Test
  void terminalStoreFailureDoesNotRefundOrThrowAwayAProviderResult() {
    UUID token = quota.tryReserve(actor).orElseThrow();
    jdbc.execute("alter table ai_match_reservation rename to unavailable_reservation");
    try {
      assertThat(quota.finish(token, "SUCCESS")).isFalse();
    } finally {
      jdbc.execute("alter table unavailable_reservation rename to ai_match_reservation");
    }
    assertThat(count()).isEqualTo(1);
    assertThat(jdbc.queryForObject("select status from ai_match_reservation", String.class))
        .isEqualTo("RESERVED");
  }

  @Test
  void unavailableQuotaStoreIsFailClosedAndOuterTransactionRemainsUsable() {
    jdbc.execute("alter table ai_match_reservation rename to unavailable_reservation");
    try {
      new TransactionTemplate(manager)
          .executeWithoutResult(
              tx -> {
                assertThatThrownBy(() -> quota.tryReserve(actor))
                    .isInstanceOf(AiUnavailableException.class)
                    .hasNoCause();
                assertThat(tx.isRollbackOnly()).isFalse();
                jdbc.update("update account set full_name='still usable' where user_id=?", actor);
              });
    } finally {
      jdbc.execute("alter table unavailable_reservation rename to ai_match_reservation");
    }
    assertThat(quota.tryReserve(actor)).isPresent();
  }

  AiMatchQuotaService restartedService() {
    return new AiMatchQuotaService(
        new AiMatchReservationRepository(jdbc),
        manager,
        clock,
        new AiProperties("stub", Duration.ofSeconds(15), null));
  }

  int count() {
    return jdbc.queryForObject("select count(*) from ai_match_reservation", Integer.class);
  }

  UUID account(String role) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into account(user_id,email,password_hash,full_name,role)"
            + " values(?,?,'test','Test',?)",
        id,
        id + "@example.test",
        role);
    return id;
  }

  static class MutableClock extends Clock {
    volatile Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }
}

package vn.edufit.ai;

import static org.assertj.core.api.Assertions.*;

import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.edufit.ai.api.*;
import vn.edufit.ai.application.*;
import vn.edufit.ai.infra.client.LlmProvider;
import vn.edufit.ai.infra.config.AiProperties;
import vn.edufit.discovery.application.service.MatchExplanationService;
import vn.edufit.discovery.domain.model.MatchScore;
import vn.edufit.profile.api.dto.TutorDiscoveryProfileDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;

/** Real flush/commit failures; Docker absence fails rather than silently skipping. */
@Testcontainers
@SpringJUnitConfig(AiLogPostgresTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AiLogPostgresTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  @Autowired AiGatewayService gateway;
  @Autowired AiRequestLogger logger;
  @Autowired DataSource source;
  @Autowired JpaTransactionManager manager;
  @Autowired TestProvider provider;
  JdbcTemplate jdbc;
  UUID actor;

  @BeforeEach
  void setup() {
    jdbc = new JdbcTemplate(source);
    jdbc.execute("drop trigger if exists reject_log on ai_request_log");
    jdbc.execute("drop trigger if exists count_log on ai_request_log");
    jdbc.execute("drop sequence if exists log_insert_attempts");
    jdbc.execute("truncate account cascade");
    actor = UUID.randomUUID();
    jdbc.update(
        "insert into account(user_id,email,password_hash,full_name,role)"
            + " values(?,?,'test','Test','STUDENT')",
        actor,
        actor + "@example.test");
    provider.status = "SUCCESS";
    provider.calls.set(0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUCCESS", "ERROR", "TIMEOUT"})
  void eachAttemptHasExactlyOneTerminalLog(String status) {
    provider.status = status;
    if (status.equals("SUCCESS"))
      assertThat(gateway.complete(request()).text()).isEqualTo("Valid answer");
    else
      assertThatThrownBy(() -> gateway.complete(request()))
          .isInstanceOf(
              status.equals("TIMEOUT") ? AiTimeoutException.class : AiUnavailableException.class);
    assertThat(jdbc.queryForList("select status from ai_request_log", String.class))
        .containsExactly(status);
    assertThat(provider.calls).hasValue(1);
  }

  @Test
  void outerRollbackDoesNotEraseCommittedProviderAttempt() {
    assertThatThrownBy(
            () ->
                new TransactionTemplate(manager)
                    .executeWithoutResult(
                        tx -> {
                          gateway.complete(request());
                          jdbc.update(
                              "update account set full_name='Rolled back' where user_id=?", actor);
                          throw new IllegalStateException("abort outer");
                        }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "select full_name from account where user_id=?", String.class, actor))
        .isEqualTo("Test");
    assertThat(jdbc.queryForList("select status from ai_request_log", String.class))
        .containsExactly("SUCCESS");
  }

  @Test
  void commitFailureDoesNotLoseAnswerOrRetryAnErrorLogOrPoisonOuterTransaction() {
    jdbc.execute("create sequence log_insert_attempts");
    jdbc.execute(
        "create or replace function count_log_insert() returns trigger language plpgsql as $$ begin"
            + " perform nextval('log_insert_attempts'); return NEW; end $$");
    jdbc.execute(
        "create trigger count_log before insert on ai_request_log for each row execute function"
            + " count_log_insert()");
    jdbc.execute(
        "create or replace function reject_log_commit() returns trigger language plpgsql as $$"
            + " begin raise exception 'injected deferred commit failure'; end $$");
    jdbc.execute(
        "create constraint trigger reject_log after insert on ai_request_log deferrable initially"
            + " deferred for each row execute function reject_log_commit()");
    new TransactionTemplate(manager)
        .executeWithoutResult(
            tx -> {
              assertThat(gateway.complete(request()).text()).isEqualTo("Valid answer");
              assertThat(tx.isRollbackOnly()).isFalse();
              jdbc.update("update account set full_name='Committed outer' where user_id=?", actor);
            });
    assertThat(jdbc.queryForObject("select last_value from log_insert_attempts", Long.class))
        .isEqualTo(1L);
    assertThat(jdbc.queryForObject("select count(*) from ai_request_log", Integer.class)).isZero();
    assertThat(
            jdbc.queryForObject(
                "select full_name from account where user_id=?", String.class, actor))
        .isEqualTo("Committed outer");
  }

  @Test
  void flushConstraintFailureIsIsolated() {
    new TransactionTemplate(manager)
        .executeWithoutResult(
            tx -> {
              assertThatCode(() -> logger.log(actor, "INVALID_FEATURE", "SUCCESS", 1))
                  .doesNotThrowAnyException();
              assertThat(tx.isRollbackOnly()).isFalse();
              jdbc.update("update account set full_name='Survived' where user_id=?", actor);
            });
    assertThat(jdbc.queryForObject("select count(*) from ai_request_log", Integer.class)).isZero();
  }

  @Test
  void unavailableQuotaStoreDoesNotPoisonOuterTransaction() {
    jdbc.execute("alter table ai_match_reservation rename to unavailable_reservation");
    try {
      new TransactionTemplate(manager)
          .executeWithoutResult(
              tx -> {
                var explanations = new MatchExplanationService(gateway);
                var result = explanations.explainTopMatch(actor, score(), tutor());
                assertThat(result.aiGenerated()).isFalse();
                assertThat(result.text()).isEqualTo(explanations.ruleBased(score()));
                assertThat(tx.isRollbackOnly()).isFalse();
                jdbc.update("update account set full_name='Usage fallback' where user_id=?", actor);
              });
      assertThat(provider.calls).hasValue(0);
    } finally {
      jdbc.execute("alter table unavailable_reservation rename to ai_match_reservation");
    }
  }

  @Test
  void legacyAuditReadFailureStillHasAnIsolatedTransaction() {
    jdbc.execute("alter table ai_request_log rename to unavailable_ai_log");
    try {
      new TransactionTemplate(manager)
          .executeWithoutResult(
              tx -> {
                assertThatThrownBy(
                        () -> gateway.countRequestsSince(actor, AiFeature.TUTOR_BIO, Instant.EPOCH))
                    .isInstanceOf(RuntimeException.class);
                assertThat(tx.isRollbackOnly()).isFalse();
                jdbc.update("update account set full_name='Audit fallback' where user_id=?", actor);
              });
    } finally {
      jdbc.execute("alter table unavailable_ai_log rename to ai_request_log");
    }
  }

  AiRequest request() {
    return AiRequest.of(actor, AiFeature.MATCH_EXPLANATION, "System", "Prompt");
  }

  static MatchScore score() {
    return new MatchScore(
        UUID.randomUUID(),
        new BigDecimal("97.00"),
        new BigDecimal("100.00"),
        new BigDecimal("100.00"),
        new BigDecimal("100.00"),
        new BigDecimal("80.00"),
        new BigDecimal("100.00"),
        1,
        8,
        Instant.now());
  }

  static TutorDiscoveryProfileDto tutor() {
    return new TutorDiscoveryProfileDto(
        new TutorSummaryDto(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Tutor",
            "Math",
            "",
            "ONLINE",
            "Hanoi",
            200000L,
            (short) 5,
            "",
            "VERIFIED",
            Instant.now(),
            new BigDecimal("4.00"),
            8),
        List.of(),
        List.of(),
        Instant.now());
  }

  static class TestProvider implements LlmProvider {
    volatile String status = "SUCCESS";
    final AtomicInteger calls = new AtomicInteger();

    public LlmResult call(String system, String prompt, Double temperature) {
      calls.incrementAndGet();
      if (status.equals("TIMEOUT")) throw new AiTimeoutException("test timeout");
      if (status.equals("ERROR")) throw new AiUnavailableException("test unavailable");
      return new LlmResult("Valid answer", 3);
    }
  }

  @Configuration
  @EnableTransactionManagement(proxyTargetClass = true)
  @EnableJpaRepositories("vn.edufit.ai.infra.persistence")
  @Import({
    AiGatewayService.class,
    AiRequestLogger.class,
    OutputSanitizer.class,
    AiMatchQuotaService.class,
    vn.edufit.ai.infra.persistence.AiMatchReservationRepository.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      var source =
          new DriverManagerDataSource(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
      Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
      return source;
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan("vn.edufit.ai.infra.persistence");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
      return factory;
    }

    @Bean
    JpaTransactionManager transactionManager(EntityManagerFactory factory) {
      return new JpaTransactionManager(factory);
    }

    @Bean
    TestProvider provider() {
      return new TestProvider();
    }

    @Bean
    AiProperties properties() {
      return new AiProperties("stub", Duration.ofSeconds(15), null);
    }

    @Bean
    java.time.Clock aiQuotaClock() {
      return java.time.Clock.systemUTC();
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource source) {
      return new JdbcTemplate(source);
    }

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService aiProviderExecutor() {
      return Executors.newVirtualThreadPerTaskExecutor();
    }
  }
}

package vn.edufit.ai;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.edufit.ai.api.*;
import vn.edufit.ai.application.*;
import vn.edufit.ai.infra.client.GeminiLlmProvider;
import vn.edufit.ai.infra.config.*;
import vn.edufit.ai.infra.persistence.*;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.discovery.application.dto.TutorMatchResult;
import vn.edufit.discovery.application.service.*;
import vn.edufit.discovery.infra.persistence.repository.MatchingRunLogRepository;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.*;
import vn.edufit.shared.auth.CurrentUser;

/**
 * Actual scorer/ranking, Gemini HTTP adapter, audit/reservation stores and matching transaction.
 */
@Testcontainers
@SpringJUnitConfig(AiMatchingPostgresTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AiMatchingPostgresTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  @Autowired DataSource source;
  @Autowired JpaTransactionManager manager;
  @Autowired AiRequestLogger logger;
  @Autowired AiRequestLogRepository logs;
  @Autowired AiMatchQuotaService quota;
  @Autowired MatchingRunLogRepository matchingLogs;
  JdbcTemplate jdbc;
  UUID actor, goal, student;
  ProfileFacade profile;
  CurrentUser currentUser;
  HttpServer server;
  ExecutorService httpExecutor, providerExecutor;
  final AtomicInteger requests = new AtomicInteger();
  volatile String mode;
  CountDownLatch started, release;
  AiGatewayService gateway;
  MatchExplanationService explanations;
  TutorMatchingService matching;

  @BeforeEach
  void setup() throws Exception {
    jdbc = new JdbcTemplate(source);
    jdbc.execute("truncate account cascade");
    actor = UUID.randomUUID();
    student = UUID.randomUUID();
    goal = UUID.randomUUID();
    jdbc.update(
        "insert into account(user_id,email,password_hash,full_name,role)"
            + " values(?,?,'test','Student','STUDENT')",
        actor,
        actor + "@example.test");
    jdbc.update(
        "insert into student_profile(student_id,user_id,education_level_id) values(?,?,2)",
        student,
        actor);
    jdbc.update(
        "insert into learning_need(need_id,student_id,created_by,subject_id,mode,budget_max_per_session,status)"
            + " values(?,?,?,1,'ONLINE',300000,'ACTIVE')",
        goal,
        student,
        actor);
    profile = mock(ProfileFacade.class);
    var slots =
        List.of(new WeeklyAvailabilityDto((short) 1, LocalTime.of(18, 0), LocalTime.of(20, 0)));
    when(profile.findLearningNeed(goal))
        .thenReturn(Optional.of(vn.edufit.connection.ConnectionTestFixtures.need(goal, student, actor, 1, 4, "ACTIVE")));
    when(profile.findEducationLevelsForMatching())
        .thenReturn(List.of(new EducationLevelOrderDto(2, 1)));
    when(profile.findVerifiedCandidatesBySubject(any()))
        .thenReturn(List.of(tutor("4.00", slots), tutor("5.00", slots)));
    currentUser = mock(CurrentUser.class);
    when(currentUser.getUserId()).thenReturn(actor);
    when(currentUser.hasRole(anyString()))
        .thenAnswer(call -> call.getArgument(0).equals("STUDENT"));
    mode = "success";
    requests.set(0);
    started = new CountDownLatch(1);
    release = new CountDownLatch(1);
    httpExecutor = Executors.newVirtualThreadPerTaskExecutor();
    providerExecutor = Executors.newVirtualThreadPerTaskExecutor();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(httpExecutor);
    server.createContext(
        "/v1beta/models/test-model:generateContent",
        exchange -> {
          requests.incrementAndGet();
          exchange.getRequestBody().readAllBytes();
          started.countDown();
          try {
            if (mode.equals("blocked")) release.await(10, TimeUnit.SECONDS);
            if (mode.equals("delayed")) Thread.sleep(20000);
            if (mode.equals("io")) {
              exchange.close();
              return;
            }
            byte[] response =
                "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"1|Grounded first tutor answer\\n2|Distinct second tutor answer\"}]}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(mode.equals("http500") ? 500 : 200, response.length);
            try (var out = exchange.getResponseBody()) {
              if (mode.equals("trickle")) {
                for (byte value : response) {
                  out.write(value);
                  out.flush();
                  Thread.sleep(100);
                }
              } else out.write(response);
            }
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.start();
    configure(Duration.ofSeconds(15));
  }

  @AfterEach
  void close() throws InterruptedException {
    release.countDown();
    server.stop(0);
    httpExecutor.shutdownNow();
    providerExecutor.shutdownNow();
    assertThat(providerExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void tenthCallsHttpAndEleventhFallsBackWithoutChangingScoresOrRanking() {
    seedNine();
    var tenth = match();
    assertThat(tenth.getFirst().aiGenerated()).isTrue();
    assertThat(tenth.get(1).aiGenerated()).isTrue();
    var eleventh = match();
    assertFallback(eleventh);
    assertThat(eleventh.stream().map(TutorMatchResult::score).toList())
        .isEqualTo(tenth.stream().map(TutorMatchResult::score).toList());
    assertThat(requests).hasValue(1);
    assertThat(jdbc.queryForList("select status from ai_request_log", String.class))
        .containsExactly("SUCCESS");
    assertThat(jdbc.queryForObject("select count(*) from ai_match_reservation", Integer.class))
        .isEqualTo(10);
    assertThat(jdbc.queryForObject("select count(*) from matching_run_log", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void directGatewayCallsCannotBypassAdmission() {
    for (int i = 0; i < 10; i++) quota.tryReserve(actor).orElseThrow();
    assertThatThrownBy(
            () ->
                gateway.complete(
                    AiRequest.of(actor, AiFeature.MATCH_EXPLANATION, "System", "Prompt")))
        .isInstanceOf(AiUnavailableException.class);
    assertThat(requests).hasValue(0);
    assertThat(jdbc.queryForObject("select count(*) from ai_request_log", Integer.class)).isZero();
  }

  @Test
  void concurrentMatchingDoesNotHoldActorLockWhileHttpIsInFlight() throws Exception {
    seedNine();
    mode = "blocked";
    var first = providerExecutor.submit(this::match);
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    long begin = System.nanoTime();
    var second = providerExecutor.submit(this::match);
    var denied = second.get(4, TimeUnit.SECONDS);
    assertFallback(denied);
    assertThat(Duration.ofNanos(System.nanoTime() - begin)).isLessThan(Duration.ofSeconds(4));
    release.countDown();
    var allowed = first.get(5, TimeUnit.SECONDS);
    assertThat(allowed.getFirst().aiGenerated()).isTrue();
    assertThat(denied.stream().map(TutorMatchResult::score).toList())
        .isEqualTo(allowed.stream().map(TutorMatchResult::score).toList());
    assertThat(requests).hasValue(1);
  }

  @Test
  void quotaStoreFailureFallsBackWithoutHttpAndMatchingStillCommits() {
    jdbc.execute("alter table ai_match_reservation rename to unavailable_reservation");
    try {
      assertFallback(match());
      assertThat(requests).hasValue(0);
      assertThat(jdbc.queryForObject("select count(*) from matching_run_log", Integer.class))
          .isEqualTo(1);
    } finally {
      jdbc.execute("alter table unavailable_reservation rename to ai_match_reservation");
    }
  }

  @Test
  void defaultFifteenSecondDeadlineStopsActualDelayedHttpAndPersistsTimeout() {
    mode = "delayed";
    long begin = System.nanoTime();
    assertFallback(match());
    Duration elapsed = Duration.ofNanos(System.nanoTime() - begin);
    assertThat(elapsed).isBetween(Duration.ofSeconds(14), Duration.ofSeconds(19));
    assertOutcome("TIMEOUT");
  }

  @Test
  void trickleBytesCannotExtendTotalDeadline() {
    configure(Duration.ofMillis(600));
    mode = "trickle";
    long begin = System.nanoTime();
    assertFallback(match());
    assertThat(Duration.ofNanos(System.nanoTime() - begin)).isLessThan(Duration.ofSeconds(4));
    assertOutcome("TIMEOUT");
  }

  @ParameterizedTest
  @ValueSource(strings = {"io", "http500"})
  void providerFailuresPersistSingleErrorAndKeepMatchingUsable(String failure) {
    mode = failure;
    assertFallback(match());
    assertOutcome("ERROR");
  }

  void assertOutcome(String status) {
    assertThat(jdbc.queryForList("select status from ai_request_log", String.class))
        .containsExactly(status);
    assertThat(jdbc.queryForList("select status from ai_match_reservation", String.class))
        .containsExactly(status);
    assertThat(jdbc.queryForObject("select count(*) from matching_run_log", Integer.class))
        .isEqualTo(1);
  }

  void assertFallback(List<TutorMatchResult> results) {
    assertThat(results).hasSize(2);
    assertThat(results).allSatisfy(result -> {
      assertThat(result.aiGenerated()).isFalse();
      assertThat(result.explanation()).isNotBlank().hasSizeLessThanOrEqualTo(180);
    });
  }

  void seedNine() {
    for (int i = 0; i < 9; i++) quota.finish(quota.tryReserve(actor).orElseThrow(), "SUCCESS");
  }

  List<TutorMatchResult> match() {
    return new TransactionTemplate(manager).execute(tx -> matching.match(currentUser, goal, 5));
  }

  void configure(Duration timeout) {
    var properties =
        new AiProperties(
            "gemini",
            timeout,
            new AiProperties.GeminiProperties(
                "test-only-key",
                "test-model",
                "http://127.0.0.1:" + server.getAddress().getPort()));
    var provider = new GeminiLlmProvider(new AiClientConfig().aiRestClient(properties), properties);
    gateway =
        new AiGatewayService(
            provider, new OutputSanitizer(), logger, logs, providerExecutor, properties, quota);
    explanations = new MatchExplanationService(gateway);
    matching =
        new TutorMatchingService(
            profile,
            matchingLogs,
            explanations,
            new vn.edufit.discovery.application.service.NeedAccessService(profile, mock(ConnectionFacade.class)),
            event -> {},
            vn.edufit.discovery.domain.policy.MatchWeights.standard(),
            manager);
  }

  TutorDiscoveryProfileDto tutor(String rating, List<WeeklyAvailabilityDto> slots) {
    Instant registered = Instant.parse("2026-01-01T00:00:00Z");
    return new TutorDiscoveryProfileDto(
        new TutorSummaryDto(UUID.randomUUID(), UUID.randomUUID(), "Tutor", "Math", "", "ONLINE", null, false, null, "Hanoi", (short) 5, "", "VERIFIED", registered, new BigDecimal(rating), 8),
        vn.edufit.connection.ConnectionTestFixtures.offerings(1, 4),
        List.of(),
        slots,
        registered);
  }

  @Configuration
  @EnableJpaRepositories(
      basePackages = {
        "vn.edufit.ai.infra.persistence",
        "vn.edufit.discovery.infra.persistence.repository"
      })
  @Import({AiRequestLogger.class, AiMatchQuotaService.class, AiMatchReservationRepository.class})
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
      factory.setPackagesToScan(
          "vn.edufit.ai.infra.persistence", "vn.edufit.discovery.infra.persistence.entity");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
      return factory;
    }

    @Bean
    JpaTransactionManager transactionManager(EntityManagerFactory factory) {
      return new JpaTransactionManager(factory);
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource source) {
      return new JdbcTemplate(source);
    }

    @Bean
    Clock aiQuotaClock() {
      return Clock.systemUTC();
    }

    @Bean
    AiProperties properties() {
      return new AiProperties("stub", Duration.ofSeconds(15), null);
    }
  }
}

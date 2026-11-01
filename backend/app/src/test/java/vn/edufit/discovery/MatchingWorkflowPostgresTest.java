package vn.edufit.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.edufit.connection.application.service.ConnectionFacadeImpl;
import vn.edufit.discovery.api.DiscoveryFacade;
import vn.edufit.discovery.api.event.MatchingExecutedEvent;
import vn.edufit.discovery.application.service.*;
import vn.edufit.discovery.web.DiscoveryController;
import vn.edufit.discovery.web.request.TutorMatchRequest;
import vn.edufit.iam.api.IamFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.WeeklyAvailabilityDto;
import vn.edufit.shared.audit.AuditSink;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;
import vn.edufit.shared.notification.NotificationSink;

/** Real Flyway schema, Connection link reads, JPA transactions and AFTER_COMMIT delivery. */
@Testcontainers
@SpringJUnitConfig(MatchingWorkflowPostgresTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MatchingWorkflowPostgresTest {

  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
  @Autowired private DiscoveryFacade facade;
  @Autowired private TutorMatchingService matching;
  @Autowired private DiscoveryQueryService queries;
  @Autowired private ProfileFacade profile;
  @Autowired private MatchExplanationService explanations;
  @Autowired private DataSource dataSource;
  @Autowired private JpaTransactionManager transactionManager;
  @Autowired private EntityManager entityManager;
  @Autowired private CommittedEvents events;
  private JdbcTemplate jdbc;
  private UUID studentUserId, parentUserId, studentId, goalId;

  @BeforeEach
  void fixtures() {
    reset(profile, explanations);
    events.received.clear();
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("truncate account cascade");
    studentUserId = UUID.randomUUID();
    parentUserId = UUID.randomUUID();
    studentId = UUID.randomUUID();
    goalId = UUID.randomUUID();
    account(studentUserId, "STUDENT");
    account(parentUserId, "PARENT");
    jdbc.update("insert into student_profile(student_id,user_id,education_level_id) values(?,?,2)", studentId, studentUserId);
    jdbc.update("""
        insert into learning_need(need_id,student_id,created_by,subject_id,mode,budget_max_per_session,status)
        values(?,?,?,1,'ONLINE',300000,'ACTIVE')
        """, goalId, studentId, studentUserId);
    stubGoal(goalId);
  }

  @Test
  void committedEmptyRunDeliversOneEventWithPersistedPayload() {
    assertEquals(List.of(), facade.findTopMatchesForNeed(actor(studentUserId, "STUDENT"), goalId, 5));
    assertEquals(1, countLogs());
    assertEquals(1, events.received.size());
    var event = events.received.getFirst();
    var row = jdbc.queryForMap("select * from matching_run_log");
    assertEquals(row.get("user_id"), event.userId());
    assertEquals(row.get("student_id"), event.studentId());
    assertEquals(row.get("need_id"), event.needId());
    assertEquals(row.get("result_count"), event.resultCount());
    var persistedAt = jdbc.queryForObject("select created_at from matching_run_log", java.sql.Timestamp.class).toInstant();
    assertTrue(java.time.Duration.between(persistedAt, event.executedAt()).abs().toNanos() < 1000,
        "PostgreSQL timestamps preserve microseconds");
    verifyNoInteractions(explanations);
  }

  @Test
  void listenerWaitsForOuterTransactionCommit() {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      facade.findTopMatchesForNeed(actor(studentUserId, "STUDENT"), goalId, 5);
      entityManager.flush();
      assertEquals(1, countLogs());
      assertTrue(events.received.isEmpty());
    });
    assertEquals(1, events.received.size());
    assertEquals(1, countLogs());
  }

  @Test
  void rollbackLeavesNeitherLogNorCommittedEvent() {
    assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      facade.findTopMatchesForNeed(actor(studentUserId, "STUDENT"), goalId, 5);
      entityManager.flush();
      assertEquals(1, countLogs());
      assertTrue(events.received.isEmpty());
      throw new IllegalStateException("Abort enclosing business operation");
    }));
    assertEquals(0, countLogs());
    assertTrue(events.received.isEmpty());
  }

  @Test
  void deferredForeignKeyFailureDoesNotDeliverCommittedEvent() {
    UUID missingGoal = UUID.randomUUID();
    stubGoal(missingGoal);
    assertThrows(RuntimeException.class,
        () -> facade.findTopMatchesForNeed(actor(studentUserId, "STUDENT"), missingGoal, 5));
    assertEquals(0, countLogs());
    assertTrue(events.received.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"CONFIRMED", "REVOKED", "PENDING", "DECLINED", "NONE"})
  void parentAccessUsesRealConnectionStateForFacadeAndRestEntry(String state) {
    if (state.equals("CONFIRMED") || state.equals("REVOKED")) {
      jdbc.update("insert into parent_student_link(parent_user_id,student_id,status,confirmed_at) values(?,?,?,now())",
          parentUserId, studentId, state);
    } else if (!state.equals("NONE")) {
      jdbc.update("insert into link_invitation(inviter_user_id,invited_user_id,status,expires_at) values(?,?,?,now()+interval '1 day')",
          parentUserId, studentUserId, state);
    }
    CurrentUser parent = actor(parentUserId, "PARENT");
    @SuppressWarnings("unchecked") ObjectProvider<CurrentUser> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(parent);
    var controller = new DiscoveryController(queries, matching, provider);
    if (state.equals("CONFIRMED")) {
      assertEquals(List.of(), facade.findTopMatchesForNeed(parent, goalId, 5));
      assertEquals(200, controller.matchTutors(new TutorMatchRequest(goalId, null)).getStatusCode().value());
      assertEquals(2, countLogs());
      assertEquals(2, events.received.size());
      assertTrue(events.received.stream().allMatch(e -> e.userId().equals(parentUserId)));
    } else {
      assertThrows(ForbiddenOperationException.class, () -> facade.findTopMatchesForNeed(parent, goalId, 5));
      assertThrows(ForbiddenOperationException.class, () -> controller.matchTutors(new TutorMatchRequest(goalId, null)));
      assertEquals(0, countLogs());
      assertTrue(events.received.isEmpty());
      verifyNoInteractions(explanations);
    }
  }

  private void account(UUID id, String role) {
    jdbc.update("insert into account(user_id,email,password_hash,full_name,role) values(?,?,'test-only','Test',?)",
        id, id + "@example.test", role);
  }

  private void stubGoal(UUID id) {
    when(profile.findLearningNeed(id)).thenReturn(Optional.of(
        vn.edufit.connection.ConnectionTestFixtures.need(id, studentId, studentUserId, 1, 2, "ACTIVE")));
  }

  private int countLogs() {
    return jdbc.queryForObject("select count(*) from matching_run_log", Integer.class);
  }

  private CurrentUser actor(UUID id, String role) {
    CurrentUser user = mock(CurrentUser.class);
    when(user.getUserId()).thenReturn(id);
    when(user.hasRole(anyString())).thenAnswer(invocation -> role.equals(invocation.getArgument(0)));
    return user;
  }

  static class CommittedEvents {
    final List<MatchingExecutedEvent> received = new ArrayList<>();
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMatching(MatchingExecutedEvent event) { received.add(event); }
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackages = {"vn.edufit.discovery.infra.persistence.repository", "vn.edufit.connection.infra.persistence.repository"})
  @Import({TutorMatchingService.class, DiscoveryFacadeImpl.class, DiscoveryQueryService.class,
      vn.edufit.discovery.application.service.NeedAccessService.class, ConnectionFacadeImpl.class})
  static class Config {
    @Bean DataSource dataSource() {
      var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
      Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
      return source;
    }
    @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan("vn.edufit.discovery.infra.persistence.entity", "vn.edufit.connection.infra.persistence.entity");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
      return factory;
    }
    @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
    @Bean EntityManager entityManager(EntityManagerFactory factory) { return SharedEntityManagerCreator.createSharedEntityManager(factory); }
    @Bean ProfileFacade profile() { return mock(ProfileFacade.class); }
    @Bean MatchExplanationService explanations() { return mock(MatchExplanationService.class); }
    @Bean IamFacade iam() { return mock(IamFacade.class); }
    @Bean AuditSink audit() { return mock(AuditSink.class); }
    @Bean NotificationSink notifications() { return mock(NotificationSink.class); }
    @Bean CommittedEvents committedEvents() { return new CommittedEvents(); }
    @Bean vn.edufit.discovery.domain.policy.MatchWeights matchWeights() {
      return vn.edufit.discovery.domain.policy.MatchWeights.standard();
    }
  }
}

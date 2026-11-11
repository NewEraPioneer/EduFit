package vn.edufit.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.notification.application.listener.NotificationEventListener;
import vn.edufit.notification.application.service.NotificationService;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.profile.api.dto.TutorSummaryDto;
import vn.edufit.scheduling.api.event.SessionUpcomingReminderEvent;

/**
 * B-01: các job nhắc lịch phát event bên trong giao dịch {@code readOnly}. Thông báo phải thực sự được ghi
 * vào PostgreSQL, không được mất âm thầm vì Hibernate không flush trong giao dịch chỉ đọc.
 */
@Testcontainers
@SpringJUnitConfig(SessionReminderNotificationPostgresTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SessionReminderNotificationPostgresTest {

  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  @Autowired ApplicationEventPublisher events;
  @Autowired TransactionTemplate tx;
  @Autowired JdbcTemplate jdbc;
  @Autowired ProfileFacade profiles;

  private UUID tutorId;
  private UUID studentId;
  private UUID tutorUserId;
  private UUID studentUserId;

  @BeforeEach
  void seed() {
    jdbc.update("DELETE FROM notification");
    tutorId = UUID.randomUUID();
    studentId = UUID.randomUUID();
    tutorUserId = account("TUTOR");
    studentUserId = account("STUDENT");
    TutorSummaryDto tutor = mock(TutorSummaryDto.class);
    when(tutor.userId()).thenReturn(tutorUserId);
    when(profiles.findTutorById(tutorId)).thenReturn(Optional.of(tutor));
    when(profiles.findStudentById(studentId))
        .thenReturn(Optional.of(new StudentSummaryDto(studentId, studentUserId, null, null, null, true)));
  }

  private UUID account(String role) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO account(user_id,email,password_hash,full_name,role) VALUES (?,?,?,?,?)",
        id, id + "@test.local", "x", role, role);
    return id;
  }

  @Test
  @DisplayName("Nhắc lịch phát trong giao dịch readOnly (như SessionReminderJob) vẫn ghi thông báo cho gia sư và học sinh")
  void reminderPublishedInsideReadOnlyTransactionIsPersisted() {
    UUID sessionId = UUID.randomUUID();
    var readOnly = new TransactionTemplate(tx.getTransactionManager());
    readOnly.setReadOnly(true);

    readOnly.executeWithoutResult(s -> events.publishEvent(SessionUpcomingReminderEvent.of(
        sessionId, UUID.randomUUID(), tutorId, studentId, Instant.now().plusSeconds(7200), 2)));

    var rows = jdbc.queryForList("SELECT user_id,target_id,title FROM notification");
    assertThat(rows).extracting(r -> r.get("user_id")).containsExactlyInAnyOrder(tutorUserId, studentUserId);
    assertThat(rows).allSatisfy(r -> assertThat(r.get("target_id")).isEqualTo(sessionId));
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories("vn.edufit.notification.infra.persistence.repository")
  @Import({NotificationEventListener.class, NotificationService.class})
  static class Config {
    @Bean DataSource dataSource() {
      var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
      Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
      new JdbcTemplate(source).execute("ALTER TABLE notification DROP CONSTRAINT IF EXISTS fk_notification_student");
      return source;
    }

    @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan("vn.edufit.notification.infra.persistence.entity");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
      return factory;
    }

    @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }

    @Bean TransactionTemplate transactionTemplate(JpaTransactionManager manager) { return new TransactionTemplate(manager); }

    @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }

    @Bean ProfileFacade profileFacade() { return mock(ProfileFacade.class); }

    @Bean ConnectionFacade connectionFacade() { return mock(ConnectionFacade.class); }
  }
}

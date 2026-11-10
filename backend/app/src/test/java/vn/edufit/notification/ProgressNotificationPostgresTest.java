package vn.edufit.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.notification.application.listener.ProgressNotificationListener;
import vn.edufit.notification.application.service.NotificationService;
import vn.edufit.notification.infra.adapter.JpaNotificationSink;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.StudentSummaryDto;
import vn.edufit.progress.api.event.MilestoneAchievedEvent;
import vn.edufit.progress.api.event.PeriodReportSentEvent;

/**
 * Event của progress → listener → sink → bảng {@code notification} thật trên PostgreSQL 17:
 * thông báo cùng commit/rollback với giao dịch nguồn, và listener từ chối chạy ngoài giao dịch.
 */
@Testcontainers
@SpringJUnitConfig(ProgressNotificationPostgresTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProgressNotificationPostgresTest {

  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  @Autowired ApplicationEventPublisher events;
  @Autowired TransactionTemplate tx;
  @Autowired JdbcTemplate jdbc;
  @Autowired ProfileFacade profiles;
  @Autowired ConnectionFacade connections;

  private UUID studentId;
  private UUID studentUserId;
  private UUID parentUserId;

  @BeforeEach
  void seed() {
    jdbc.update("DELETE FROM notification");
    studentId = UUID.randomUUID();
    studentUserId = account("STUDENT");
    parentUserId = account("PARENT");
    when(profiles.findStudentById(studentId))
        .thenReturn(Optional.of(new StudentSummaryDto(studentId, studentUserId, null, null, null, true)));
    when(connections.findConfirmedParentUserIds(studentId)).thenReturn(List.of(parentUserId));
  }

  private UUID account(String role) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO account(user_id,email,password_hash,full_name,role) VALUES (?,?,?,?,?)",
        id, id + "@test.local", "x", role, role);
    return id;
  }

  private List<Map<String, Object>> rows() {
    return jdbc.queryForList(
        "SELECT user_id,about_student_id,type,title,short_text,target_type,target_id,is_read FROM notification");
  }

  @Test
  @DisplayName("Chốt cột mốc: commit giao dịch → học sinh và phụ huynh có thông báo chưa đọc, đúng đích")
  void milestoneAchievedPersistsForStudentAndParent() {
    UUID milestoneId = UUID.randomUUID();

    tx.executeWithoutResult(s ->
        events.publishEvent(MilestoneAchievedEvent.of(UUID.randomUUID(), studentId, milestoneId, "Hệ phương trình")));

    assertThat(rows()).hasSize(2)
        .allSatisfy(r -> {
          assertThat(r.get("type")).isEqualTo("MILESTONE_ACHIEVED");
          assertThat(r.get("target_type")).isEqualTo("MILESTONE");
          assertThat(r.get("target_id")).isEqualTo(milestoneId);
          assertThat(r.get("is_read")).isEqualTo(false);
          assertThat((String) r.get("short_text")).contains("Hệ phương trình");
        });
    assertThat(rows()).extracting(r -> r.get("user_id")).containsExactlyInAnyOrder(studentUserId, parentUserId);
  }

  @Test
  @DisplayName("Báo cáo kỳ được gửi: thông báo ghi kèm khoảng ngày của kỳ")
  void periodReportSentPersists() {
    UUID reportId = UUID.randomUUID();

    tx.executeWithoutResult(s -> events.publishEvent(PeriodReportSentEvent.of(
        reportId, UUID.randomUUID(), studentId, 1, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"))));

    assertThat(rows()).hasSize(2).allSatisfy(r -> {
      assertThat(r.get("type")).isEqualTo("PERIOD_REPORT_SENT");
      assertThat(r.get("target_type")).isEqualTo("PERIOD_REPORT");
      assertThat(r.get("target_id")).isEqualTo(reportId);
      assertThat((String) r.get("short_text")).contains("01/09–30/09");
    });
  }

  @Test
  @DisplayName("Giao dịch nguồn rollback → không để lại thông báo mồ côi")
  void rollbackLeavesNoNotification() {
    assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
      events.publishEvent(MilestoneAchievedEvent.of(UUID.randomUUID(), studentId, UUID.randomUUID(), "A"));
      throw new IllegalStateException("boom");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(rows()).isEmpty();
  }

  @Test
  @DisplayName("Phát event ngoài giao dịch bị từ chối (Propagation.MANDATORY), không ghi gì")
  void publishingOutsideTransactionIsRejected() {
    assertThatThrownBy(() ->
        events.publishEvent(MilestoneAchievedEvent.of(UUID.randomUUID(), studentId, UUID.randomUUID(), "A")))
        .isInstanceOf(IllegalTransactionStateException.class);

    assertThat(rows()).isEmpty();
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories("vn.edufit.notification.infra.persistence.repository")
  @Import({NotificationService.class, JpaNotificationSink.class, ProgressNotificationListener.class})
  static class Config {
    @Bean DataSource dataSource() {
      var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
      Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
      // Chỉ kiểm chứng đường ghi thông báo; bỏ FK sang hồ sơ học sinh vì module profile không nằm trong context này.
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

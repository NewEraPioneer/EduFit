package vn.edufit.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.edufit.notification.application.service.NotificationService;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;

/**
 * A-01: {@code /unread-summary} chạy trên PostgreSQL thật. Trước đây query HQL {@code GROUP BY CASE} chỉ được kiểm
 * chứng qua mock, nếu Hibernate render sai thì mọi trang dashboard nhận 500 và chuông im lặng ở 0.
 */
@Testcontainers
@SpringJUnitConfig(UnreadSummaryPostgresTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UnreadSummaryPostgresTest {

  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  @Autowired NotificationService notifications;
  @Autowired JdbcTemplate jdbc;

  private UUID userId;
  private UUID otherUserId;
  private CurrentUser me;

  @BeforeEach
  void seed() {
    jdbc.update("DELETE FROM notification");
    userId = account();
    otherUserId = account();
    me = mock(CurrentUser.class);
    when(me.getUserId()).thenReturn(userId);
  }

  private UUID account() {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO account(user_id,email,password_hash,full_name,role) VALUES (?,?,?,?,?)",
        id, id + "@test.local", "x", "U", "STUDENT");
    return id;
  }

  private void notify(UUID user, String type) {
    notifications.createNotification(user, null, type, "t", "s", "SESSION", UUID.randomUUID());
  }

  @Test
  @DisplayName("Gộp type thành category trên PostgreSQL; chỉ đếm chưa đọc của chính người dùng")
  void groupsUnreadByCategoryForTheCurrentUserOnly() {
    notify(userId, "SESSION_CANCELLED");
    notify(userId, "SESSION_RESCHEDULE_PROPOSED");
    notify(userId, "CONNECTION_REQUESTED");
    notify(userId, "PAYMENT_TOPUP");
    notify(userId, "MILESTONE_ACHIEVED");
    notify(userId, "SESSION_FEEDBACK_REMINDER");
    notify(userId, "VERIFICATION_APPROVED");
    notify(userId, "SOMETHING_UNKNOWN");
    notify(otherUserId, "SESSION_CANCELLED");

    var summary = notifications.getUnreadSummary(me);

    assertThat(summary.total()).isEqualTo(8);
    assertThat(summary.byCategory()).containsExactlyInAnyOrderEntriesOf(Map.of(
        "SCHEDULE", 2L, "CONNECTION", 1L, "PAYMENT", 1L, "PROGRESS", 2L, "VERIFICATION", 1L, "OTHER", 1L));
  }

  @Test
  @DisplayName("Thông báo đã đọc không còn được đếm; người dùng không có thông báo thì trả 0")
  void readNotificationsAreNotCounted() {
    assertThat(notifications.getUnreadSummary(me).total()).isZero();

    notify(userId, "SESSION_CANCELLED");
    notify(userId, "SESSION_CANCELLED");
    notifications.markAllAsRead(me);

    var summary = notifications.getUnreadSummary(me);
    assertThat(summary.total()).isZero();
    assertThat(summary.byCategory()).isEmpty();
  }

  @Test
  @DisplayName("A-06: đánh dấu đã đọc thông báo của người khác trả 404 như không tồn tại")
  void markingSomeoneElsesNotificationLooksLikeNotFound() {
    UUID foreign = notifications.createNotification(otherUserId, null, "SESSION_CANCELLED", "t", "s", "SESSION",
        UUID.randomUUID()).getNotificationId();

    assertThatThrownBy(() -> notifications.markAsRead(me, foreign)).isInstanceOf(EntityNotFoundException.class);
    assertThat(jdbc.queryForObject("SELECT is_read FROM notification WHERE notification_id=?", Boolean.class, foreign))
        .isFalse();
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories("vn.edufit.notification.infra.persistence.repository")
  @Import(NotificationService.class)
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

    @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
  }
}

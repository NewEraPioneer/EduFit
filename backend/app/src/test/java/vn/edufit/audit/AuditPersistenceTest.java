package vn.edufit.audit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
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
import vn.edufit.shared.audit.*;

@Testcontainers
@SpringJUnitConfig(AuditPersistenceTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuditPersistenceTest {
  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
  @Autowired AuditSink sink;
  @Autowired DataSource source;
  @Autowired JpaTransactionManager manager;
  @Autowired vn.edufit.connection.application.service.ParentStudentLinkService links;
  @Autowired vn.edufit.profile.api.ProfileFacade profile;
  @Autowired vn.edufit.iam.api.IamFacade iam;
  private static final UUID ACTOR = UUID.randomUUID();

  // Không dựa vào tài khoản demo của profile dev: tự tạo actor cho ràng buộc fk_audit_actor
  @org.junit.jupiter.api.BeforeEach void ensureActorAccount() {
    new JdbcTemplate(source).update("insert into account(user_id,email,password_hash,full_name,role) values (?,?,'test-only','Audit actor','ADMIN') on conflict do nothing",ACTOR,ACTOR+"@test.invalid");
  }

  @Test void structuredSnapshotRetainsAccountsReasonAndBusinessTime() {
    UUID target = UUID.randomUUID(); Instant time = Instant.parse("2026-10-11T00:00:00Z");
    var metadata = new java.util.HashMap<String,String>();
    metadata.put("parentUserId", UUID.randomUUID().toString());
    metadata.put("studentId", UUID.randomUUID().toString());
    metadata.put("studentUserId", ACTOR.toString());
    var record = new AuditRecord(ACTOR,"LINK_REVOKED","PARENT_STUDENT_LINK",target,"private reason",time,metadata);
    metadata.clear();
    new TransactionTemplate(manager).executeWithoutResult(status -> sink.record(record));
    var jdbc = new JdbcTemplate(source);
    assertEquals(time,jdbc.queryForObject("select created_at from audit_log where target_id=?",java.sql.Timestamp.class,target).toInstant());
    assertEquals("private reason",jdbc.queryForObject("select reason from audit_log where target_id=?",String.class,target));
    record.metadata().forEach((key,value) -> assertEquals(value,jdbc.queryForObject("select metadata ->> ? from audit_log where target_id=?",String.class,key,target)));
    assertThrows(UnsupportedOperationException.class, () -> record.metadata().put("changed","value"));
  }
  @Test void legacyApiStillPersistsWithEmptyMetadata() {
    UUID target = UUID.randomUUID();
    new TransactionTemplate(manager).executeWithoutResult(status -> sink.record(ACTOR,"LEGACY","ACCOUNT",target,null));
    assertEquals("{}",new JdbcTemplate(source).queryForObject("select metadata::text from audit_log where target_id=?",String.class,target));
  }
  @Test void outerRollbackDiscardsStructuredAudit() {
    UUID target = UUID.randomUUID();
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      sink.record(new AuditRecord(ACTOR,"LINK_REVOKED","PARENT_STUDENT_LINK",target,null,Instant.now(),Map.of("studentUserId",ACTOR.toString())));
      status.setRollbackOnly();
    });
    assertEquals(0,new JdbcTemplate(source).queryForObject("select count(*) from audit_log where target_id=?",Integer.class,target));
  }
  private record LinkFixture(UUID link, UUID parent, UUID student, UUID studentUser, UUID admin) {}
  private LinkFixture linkFixture() {
    var fixture = new LinkFixture(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
    var jdbc = new JdbcTemplate(source);
    for (UUID id : java.util.List.of(fixture.parent(),fixture.studentUser(),fixture.admin()))
      jdbc.update("insert into account(user_id,email,password_hash,full_name,role) values (?,?,'test-only','Audit fixture',?)",id,id+"@test.invalid",id.equals(fixture.parent())?"PARENT":id.equals(fixture.admin())?"ADMIN":"STUDENT");
    jdbc.update("insert into student_profile(student_id,user_id) values (?,?)",fixture.student(),fixture.studentUser());
    jdbc.update("insert into parent_student_link(link_id,parent_user_id,student_id,confirmed_at) values (?,?,?,now())",fixture.link(),fixture.parent(),fixture.student());
    when(profile.findStudentById(fixture.student())).thenReturn(java.util.Optional.of(new vn.edufit.profile.api.dto.StudentSummaryDto(fixture.student(), fixture.studentUser(), 4, null, null, true)));
    when(iam.findUserSummaryById(fixture.admin())).thenReturn(java.util.Optional.of(new vn.edufit.iam.api.dto.UserSummaryView(fixture.admin(),"admin@test.invalid","Admin",vn.edufit.iam.api.AccountRole.ADMIN,vn.edufit.iam.api.AccountStatus.ACTIVE)));
    return fixture;
  }
  private vn.edufit.shared.auth.CurrentUser admin(LinkFixture fixture) {
    var actor=mock(vn.edufit.shared.auth.CurrentUser.class);when(actor.getUserId()).thenReturn(fixture.admin());
    when(actor.hasRole(anyString())).thenAnswer(call -> "ADMIN".equals(call.getArgument(0)));return actor;
  }
  @Test void realRevokeAndSnapshotCommitTogetherWithoutDeletingStudentData() {
    var fixture=linkFixture();links.revokeLink(admin(fixture),fixture.link(),"private reason");
    var jdbc=new JdbcTemplate(source);
    assertEquals("REVOKED",jdbc.queryForObject("select status from parent_student_link where link_id=?",String.class,fixture.link()));
    assertEquals(fixture.studentUser().toString(),jdbc.queryForObject("select metadata ->> 'studentUserId' from audit_log where target_id=?",String.class,fixture.link()));
    assertEquals(1,jdbc.queryForObject("select count(*) from student_profile where student_id=?",Integer.class,fixture.student()));
  }
  @Test void databaseAuditFailureRollsBackRealRevocation() {
    var fixture=linkFixture();var jdbc=new JdbcTemplate(source);
    jdbc.execute("create function reject_connection_audit() returns trigger language plpgsql as $$ begin raise exception 'audit rejected' using errcode='23514'; end; $$");
    jdbc.execute("create trigger reject_connection_audit before insert on audit_log for each row execute function reject_connection_audit()");
    try {
      assertThrows(RuntimeException.class,()->links.revokeLink(admin(fixture),fixture.link(),"reason"));
      assertEquals("CONFIRMED",jdbc.queryForObject("select status from parent_student_link where link_id=?",String.class,fixture.link()));
      assertEquals(0,jdbc.queryForObject("select count(*) from audit_log where target_id=?",Integer.class,fixture.link()));
    } finally { jdbc.execute("drop trigger reject_connection_audit on audit_log");jdbc.execute("drop function reject_connection_audit()"); }
  }
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings={"ADMIN","STUDENT"})
  void revokeNotificationsHaveCorrectRecipientsAndNoPrivateReason(String role) {
    var fixture=linkFixture();UUID user=role.equals("ADMIN")?fixture.admin():fixture.studentUser();
    var actor=mock(vn.edufit.shared.auth.CurrentUser.class);when(actor.getUserId()).thenReturn(user);
    when(actor.hasRole(anyString())).thenAnswer(call -> role.equals(call.getArgument(0)));
    when(iam.findUserSummaryById(user)).thenReturn(java.util.Optional.of(new vn.edufit.iam.api.dto.UserSummaryView(user,"actor@test.invalid","Actor",vn.edufit.iam.api.AccountRole.valueOf(role),vn.edufit.iam.api.AccountStatus.ACTIVE)));
    links.revokeLink(actor,fixture.link(),"private reason never disclose");
    var jdbc=new JdbcTemplate(source);
    assertEquals(role.equals("ADMIN")?2:1,jdbc.queryForObject("select count(*) from notification where target_id=? and type='LINK_REVOKED'",Integer.class,fixture.link()));
    assertEquals(1,jdbc.queryForObject("select count(*) from notification where target_id=? and user_id=? and about_student_id is null",Integer.class,fixture.link(),fixture.parent()));
    if(role.equals("ADMIN")) assertEquals(1,jdbc.queryForObject("select count(*) from notification where target_id=? and user_id=? and about_student_id=?",Integer.class,fixture.link(),fixture.studentUser(),fixture.student()));
    assertEquals(0,jdbc.queryForObject("select count(*) from notification where target_id=? and (title like '%private reason%' or short_text like '%private reason%')",Integer.class,fixture.link()));
    assertThrows(vn.edufit.shared.exception.ResourceConflictException.class,()->links.revokeLink(actor,fixture.link(),"private reason never disclose"));
    assertEquals(role.equals("ADMIN")?2:1,jdbc.queryForObject("select count(*) from notification where target_id=?",Integer.class,fixture.link()));
  }
  @Test void notificationFailureRollsBackRevocationAndRealAuditTogether() {
    var fixture=linkFixture();var jdbc=new JdbcTemplate(source);
    jdbc.execute("create function reject_revoke_notification() returns trigger language plpgsql as $$ begin if new.type='LINK_REVOKED' and new.about_student_id is not null then raise exception 'notification rejected' using errcode='23514'; end if; return new; end; $$");
    jdbc.execute("create trigger reject_revoke_notification before insert on notification for each row execute function reject_revoke_notification()");
    try {
      assertThrows(RuntimeException.class,()->links.revokeLink(admin(fixture),fixture.link(),"private reason"));
      assertEquals("CONFIRMED",jdbc.queryForObject("select status from parent_student_link where link_id=?",String.class,fixture.link()));
      assertEquals(0,jdbc.queryForObject("select count(*) from audit_log where target_id=?",Integer.class,fixture.link()));
      assertEquals(0,jdbc.queryForObject("select count(*) from notification where target_id=?",Integer.class,fixture.link()));
    } finally { jdbc.execute("drop trigger reject_revoke_notification on notification");jdbc.execute("drop function reject_revoke_notification()"); }
  }
  @Configuration @EnableTransactionManagement
  @EnableJpaRepositories(basePackages = {"vn.edufit.audit","vn.edufit.connection.infra.persistence.repository","vn.edufit.notification"})
  @Import({JpaAuditSink.class,vn.edufit.connection.application.service.ParentStudentLinkService.class,vn.edufit.connection.infra.locking.PostgresConnectionLock.class,vn.edufit.notification.ConnectionNotificationTestConfig.class})
  static class Config {
    @Bean DataSource dataSource() {
      var source = new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
      Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();return source;
    }
    @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
      var factory = new LocalContainerEntityManagerFactoryBean();factory.setDataSource(source);
      factory.setPackagesToScan("vn.edufit.audit","vn.edufit.connection.infra.persistence.entity","vn.edufit.notification");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate"));return factory;
    }
    @Bean java.time.Clock clock() { return java.time.Clock.systemUTC(); }
    @Bean @Primary jakarta.persistence.EntityManager entityManager(EntityManagerFactory factory) { return SharedEntityManagerCreator.createSharedEntityManager(factory); }
    @Bean vn.edufit.iam.api.IamFacade iam() { return mock(vn.edufit.iam.api.IamFacade.class); }
    @Bean vn.edufit.profile.api.ProfileFacade profile() { return mock(vn.edufit.profile.api.ProfileFacade.class); }
    @Bean vn.edufit.connection.api.ConnectionFacade connectionFacade() { return mock(vn.edufit.connection.api.ConnectionFacade.class); }
    @Bean vn.edufit.shared.notification.NotificationSink notifications() { return mock(vn.edufit.shared.notification.NotificationSink.class); }
    @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
  }
}

package vn.edufit.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DefenseDemoSeedIT {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
      .withDatabaseName("defense_test").withUsername("edufit").withPassword("test-only");

  @BeforeAll static void migrate() {
    Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
        .locations("classpath:db/migration","classpath:db/seed").load().migrate();
  }
  private long count(String sql) throws Exception {
    try (var c=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
         var s=c.createStatement(); var r=s.executeQuery(sql)) { r.next(); return r.getLong(1); }
  }
  @Test void workshopAccountsAndRelationshipsAreComplete() throws Exception {
    assertEquals(7,count("select count(*) from account where email like '%@demo.edufit.test'"));
    assertEquals(6,count("select count(*) from learning_need n join student_profile s using(student_id) join account a using(user_id) where a.email like '%@demo.edufit.test'"));
    assertEquals(6,count("select count(*) from class"));
    assertEquals(2,count("select count(*) from parent_student_link l join account a on a.user_id=l.parent_user_id where a.email='parent.live@demo.edufit.test' and l.status='CONFIRMED'"));
    assertEquals(5,count("select count(*) from connection_request r join student_profile s using(student_id) join account a using(user_id) where a.email='student.conflict@demo.edufit.test' and r.status='PENDING'"));
  }
  @Test void walletsMatchLedgerAndNeverBecomeNegative() throws Exception {
    assertEquals(0,count("select count(*) from payment_wallet w where (available,held)<>(select sum(available_delta),sum(held_delta) from payment_entry e where e.user_id=w.user_id)"));
    assertEquals(0,count("select count(*) from (select sum(available_delta) over(partition by user_id order by created_at,event_key) a,sum(held_delta) over(partition by user_id order by created_at,event_key) h from payment_entry where user_id is not null) x where a<0 or h<0"));
    assertEquals(1000000,count("select available from payment_wallet join account using(user_id) where email='parent.live@demo.edufit.test'"));
    assertEquals(900000,count("select available from payment_wallet join account using(user_id) where email='tutor.live@demo.edufit.test'"));
  }
  @Test void agreedCaseIsActionableByAdmin() throws Exception {
    assertEquals(1,count("select count(*) from payment_charge p join account a on a.user_id=p.payer_user_id where a.email='student.conflict@demo.edufit.test' and p.status='AWAITING_ADMIN' and p.learner_amount=p.tutor_amount and p.learner_amount=125000"));
    assertEquals(0,count("select count(*) from payment_charge p join tutoring_session s using(session_id) where p.end_at<>s.end_at"));
    assertEquals(0,count("select count(*) from payment_charge p where status='SETTLED' and settled_amount<>(select sum(available_delta) from payment_entry e where e.reference_id=p.session_id and kind in ('TUITION_INCOME','COMMISSION'))"));
  }
  @Test void sessionsAndReportsHaveValidChronology() throws Exception {
    assertEquals(0,count("select count(*) from tutoring_session s join class c using(class_id) where s.start_at<c.started_at or (c.ended_at is not null and s.end_at>c.ended_at)"));
    assertEquals(0,count("select count(*) from tutoring_session where status in ('COMPLETED','ABSENT') and (outcome_recorded_at<end_at or outcome_recorded_at>now())"));
    assertEquals(0,count("select count(*) from tutoring_session a join tutoring_session b on a.session_id<b.session_id and a.status='SCHEDULED' and b.status='SCHEDULED' and (a.student_id=b.student_id or a.tutor_id=b.tutor_id) where a.start_at<b.end_at and b.start_at<a.end_at"));
    assertEquals(0,count("select count(*) from connection_request where status='PENDING' and expires_at<=now()"));
  }
  @Test void liveGroupHasTasksToPerform() throws Exception {
    assertEquals(0,count("select count(*) from session_feedback f where not exists(select 1 from milestone_assessment a where a.session_id=f.session_id)"));
    assertEquals(0,count("select count(*) from class c join student_profile s using(student_id) join account a using(user_id) join tutor_profile t using(tutor_id) join account ta on ta.user_id=t.user_id where a.email='student.live@demo.edufit.test' and ta.email='tutor.live@demo.edufit.test' and c.status='ACTIVE'"));
    assertEquals(0,count("select count(*) from review r join student_profile s using(student_id) join account a using(user_id) where a.email='student.live@demo.edufit.test'"));
    assertEquals(0,count("select count(*) from payment_withdrawal w join account a using(user_id) where a.email='tutor.live@demo.edufit.test' and w.status='PENDING'"));
    assertEquals(3,count("select count(*) from credential_file where storage_key='defense-demo.pdf'"));
  }
}

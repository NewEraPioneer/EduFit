package vn.edufit.db;
import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import vn.edufit.EduFitApplication;
@SpringBootTest(classes=EduFitApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentApplicationIT {
 static PostgreSQLContainer<?> container;
 @DynamicPropertySource static void database(DynamicPropertyRegistry props) {
  String url=System.getenv("PAYMENT_TEST_JDBC");String user=System.getenv().getOrDefault("PAYMENT_TEST_USER","payment_test");String password=System.getenv().getOrDefault("PAYMENT_TEST_PASSWORD","");
  if(url==null) {container=new PostgreSQLContainer<>("postgres:17");container.start();url=container.getJdbcUrl();user=container.getUsername();password=container.getPassword();}
  String schema="payment_app_"+UUID.randomUUID().toString().replace("-","");String jdbc=url+(url.contains("?")?"&":"?")+"currentSchema="+schema;String u=user,p=password;
  props.add("spring.datasource.url",()->jdbc);props.add("spring.datasource.username",()->u);props.add("spring.datasource.password",()->p);props.add("spring.flyway.schemas",()->schema);props.add("edufit.ai.provider",()->"stub");props.add("spring.mail.test-connection",()->false);
 }
 @Autowired ServletWebServerApplicationContext context;
 @Autowired org.springframework.jdbc.core.JdbcTemplate db;
 @Autowired vn.edufit.payment.infra.PaymentStore store;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
 @Autowired org.springframework.context.ApplicationEventPublisher events;

 @Test void schedulingOutcomeAtomicallyOpensOneNotified24HourWindowEvenWhenRecordedLate() {
  var row=db.queryForMap("select g.goal_id,g.student_id,s.user_id student_user,t.tutor_id,t.user_id tutor_user"
    + " from learning_goal g join student_profile s using(student_id) cross join tutor_profile t limit 1");
  UUID pupil=(UUID)row.get("student_user"), tutorUser=(UUID)row.get("tutor_user"), student=(UUID)row.get("student_id"), tutor=(UUID)row.get("tutor_id");
  UUID request=UUID.randomUUID(), cls=UUID.randomUUID(), session=UUID.randomUUID();
  var recorded=java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
  var end=recorded.minusSeconds(90000);
  db.update("insert into connection_request(request_id,student_id,tutor_id,goal_id,sent_by_user_id,status,expires_at) values (?,?,?,?,?,'ACCEPTED',now())",request,student,tutor,row.get("goal_id"),pupil);
  db.update("insert into class(class_id,connection_request_id,student_id,tutor_id,goal_id) values (?,?,?,?,?)",cls,request,student,tutor,row.get("goal_id"));
  db.update("insert into tutoring_session(session_id,class_id,tutor_id,student_id,start_at,end_at,mode,status,proposed_by,expires_at) values (?,?,?,?,?,?,'ONLINE','SCHEDULED',?,now())",session,cls,tutor,student,java.sql.Timestamp.from(end.minusSeconds(3600)),java.sql.Timestamp.from(end),tutorUser);
  store.locked(()-> {store.insert(new vn.edufit.payment.application.PaymentViews.Charge(session,cls,student,pupil,tutorUser,1000,new vn.edufit.payment.domain.SessionCharge(100000,end)));store.move(pupil,0,100000,"review-fixture:"+session,"TEST",session);return null;});
  var event=vn.edufit.scheduling.api.event.SessionOutcomeRecordedEvent.of(session,cls,tutor,student,"COMPLETED",null,recorded);
  new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
   db.update("update tutoring_session set status='COMPLETED',outcome_recorded_at=?,outcome_recorded_by=? where session_id=?",java.sql.Timestamp.from(recorded),tutorUser,session);
   events.publishEvent(event);return null;
  });
  assertEquals(recorded.plusSeconds(86400),store.find(session).orElseThrow().state().reviewDeadline());
  assertEquals(2,db.queryForObject("select count(*) from notification where target_id=? and target_type='PAYMENT_SESSION'",Integer.class,session));
  new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{events.publishEvent(event);return null;});
  assertEquals(2,db.queryForObject("select count(*) from notification where target_id=? and target_type='PAYMENT_SESSION'",Integer.class,session));
 }
 @Test void paymentDoesNotBreakApplicationClockWiringOrExposeUnauthenticatedWallet() throws Exception {
  int port=context.getWebServer().getPort();
  try(var http=HttpClient.newHttpClient()) {
   var health=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/actuator/health")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,health.statusCode());
   var wallet=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/payment/wallet")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(401,wallet.statusCode());
  }
 }
 @AfterAll static void stopContainer(){if(container!=null)container.stop();}
}

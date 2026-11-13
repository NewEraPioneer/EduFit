package vn.edufit.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;
import vn.edufit.ai.api.AiGateway;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.discovery.application.matching.MatchWeights;
import vn.edufit.discovery.application.service.DiscoveryQueryService;
import vn.edufit.discovery.application.service.MatchExplanationService;
import vn.edufit.discovery.application.service.NeedAccessService;
import vn.edufit.discovery.application.service.TutorMatchingService;
import vn.edufit.discovery.web.DiscoveryController;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.port.ParentStudentLinkPort;
import vn.edufit.profile.application.service.LearningCatalogLoader;
import vn.edufit.profile.application.service.LearningNeedService;
import vn.edufit.profile.application.service.ProfileFacadeImpl;
import vn.edufit.profile.application.service.TutorProfileAssembler;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.web.advice.GlobalExceptionHandler;

/** Discovery trên schema thật sau migration: lọc cứng theo gói dạy, phân trang ổn định, xếp hạng theo nhu cầu. */
@Testcontainers
@SpringJUnitConfig(DiscoveryReadPostgresTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DiscoveryReadPostgresTest {
  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
  private static final UUID STUDENT_USER = new UUID(1, 1), STUDENT = new UUID(1, 2), NEED = new UUID(1, 3);

  @Autowired private ProfileFacade profile;
  @Autowired private DiscoveryQueryService queries;
  @Autowired private TutorMatchingService matching;
  @Autowired private DataSource dataSource;
  @Autowired private EntityManagerFactory emf;
  @Autowired private SqlCapture sql;
  @Autowired private AiGateway ai;
  @Autowired private CurrentUser actor;
  private JdbcTemplate jdbc;
  private MockMvc mvc;
  private int math, level8, level9, grade10, foundation, kntt, hanoi, cauGiay, dongDa;

  @BeforeEach
  void fixtures() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("truncate account cascade");
    math = id("select subject_id from subject where name='Toán'");
    level8 = id("select level_id from education_level where name='Lớp 8'");
    level9 = id("select level_id from education_level where name='Lớp 9'");
    grade10 = id("select goal_tag_id from goal_tag where code='GRADE10_EXAM'");
    foundation = id("select goal_tag_id from goal_tag where code='FOUNDATION'");
    kntt = id("select series_id from textbook_series where code='KNTT'");
    hanoi = id("select province_id from province where code='HA_NOI'");
    cauGiay = id("select district_id from district where name='Cầu Giấy'");
    dongDa = id("select district_id from district where name='Đống Đa'");
    jdbc.update("insert into account(user_id,email,password_hash,full_name,role) values(?,'student@example.test','test-only','Student','STUDENT')", STUDENT_USER);
    jdbc.update("insert into student_profile(student_id,user_id,education_level_id) values(?,?,?)", STUDENT, STUDENT_USER, level9);
    jdbc.update("""
        insert into learning_need(need_id,student_id,created_by,education_level_id,subject_id,goal_tag_id,exam_province_id,
            mode,province_id,district_id,budget_max_per_session,status)
        values(?,?,?,?,?,?,?,'OFFLINE',?,?,300000,'ACTIVE')
        """, NEED, STUDENT, STUDENT_USER, level9, math, grade10, hanoi, hanoi, cauGiay);
    jdbc.update("insert into learning_need_slot(need_id,day_of_week,start_time,end_time) values(?,2,'19:00','21:00')", NEED);
    @SuppressWarnings("unchecked") ObjectProvider<CurrentUser> provider = mock(ObjectProvider.class);
    lenient().when(provider.getIfAvailable()).thenReturn(actor);
    mvc = MockMvcBuilders.standaloneSetup(new DiscoveryController(queries, matching, provider, org.mockito.Mockito.mock(vn.edufit.scheduling.api.SessionFacade.class)))
        .setControllerAdvice(new GlobalExceptionHandler()).build();
    reset(ai);
    when(ai.complete(any())).thenReturn(new vn.edufit.ai.api.AiResponse("Offline fixture", 0, null, false));
    clearSql();
  }

  private int id(String query) {
    return jdbc.queryForObject(query, Integer.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {20, 240})
  void candidatesAreBoundedAndHydratedInBatchesOutsideTransactions(int count) {
    seedTutors(count);
    clearSql();
    var candidates = profile.findVerifiedCandidatesBySubject(math);
    assertEquals(Math.min(count, 200), candidates.size());
    assertTrue(sql.statements.getFirst().toLowerCase().contains("fetch first"));
    assertTrue(queryCount() < 40, "Offerings, levels, goals, districts and slots are batch-loaded: " + queryCount());
    assertDetached();
    assertEquals(2, candidates.getFirst().offerings().getFirst().levels().size());

    clearSql();
    var results = matching.match(actor, NEED, 5);
    assertEquals(5, results.size());
    // Phần cố định (nhu cầu, danh mục, nhật ký) + mỗi lô 50 ứng viên nạp 5 bảng con; ứng viên tối đa 200
    long hydrationBatches = (Math.min(count, 200) + 49) / 50;
    assertTrue(queryCount() <= 30 + 5 * hydrationBatches, "Matching stays bounded: " + queryCount());
    assertDetached();
  }

  @ParameterizedTest
  @CsvSource({"relevance,desc", "rating,asc", "price,asc", "price,desc", "experience,desc"})
  void httpPaginationIsStableAndUnique(String sort, String direction) throws Exception {
    seedTutors(23);
    var found = new ArrayList<String>();
    for (int page = 0; page < 5; page++) {
      String body = mvc.perform(get("/api/v1/discovery/tutors").param("page", "" + page).param("size", "5")
              .param("sortBy", sort).param("sortDirection", direction))
          .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
      JsonMapper.builder().build().readTree(body).get("data").get("content")
          .forEach(node -> found.add(node.get("tutorId").asText()));
    }
    assertEquals(23, found.size());
    assertEquals(23, new HashSet<>(found).size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"level", "goal", "textbook", "maxPrice", "district", "tutorType", "mode", "keyword"})
  void userChosenFiltersAreHard(String filter) throws Exception {
    seedTutors(3);
    UUID excluded = tutorId(2);
    MockHttpServletRequestBuilder request = get("/api/v1/discovery/tutors").param("subjectId", "" + math);
    switch (filter) {
      case "level" -> {
        jdbc.update("delete from tutor_offering_level where offering_id in (select offering_id from tutor_offering where tutor_id=?) and level_id=?", excluded, level9);
        request.param("levelId", "" + level9);
      }
      case "goal" -> {
        jdbc.update("delete from tutor_offering_goal where offering_id in (select offering_id from tutor_offering where tutor_id=?)", excluded);
        request.param("goalTagId", "" + grade10);
      }
      case "textbook" -> {
        jdbc.update("delete from tutor_offering_textbook where offering_id in (select offering_id from tutor_offering where tutor_id=?)", excluded);
        request.param("textbookSeriesId", "" + kntt);
      }
      case "maxPrice" -> {
        jdbc.update("update tutor_offering set price_per_session=900000 where tutor_id=?", excluded);
        request.param("maxPrice", "300000");
      }
      case "district" -> {
        jdbc.update("update tutor_district set district_id=? where tutor_id=?", dongDa, excluded);
        request.param("provinceId", "" + hanoi).param("districtId", "" + cauGiay);
      }
      case "tutorType" -> {
        jdbc.update("update tutor_profile set tutor_type_verified=false where tutor_id=?", excluded);
        request.param("tutorType", "GRADUATE");
      }
      case "mode" -> {
        jdbc.update("update tutor_profile set teaching_mode='OFFLINE' where tutor_id=?", excluded);
        request.param("mode", "ONLINE");
      }
      default -> {
        jdbc.update("update tutor_profile set display_name='Gia sư khác' where tutor_id=?", excluded);
        request.param("keyword", "Tutor");
      }
    }
    mvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2));
  }

  @Test
  void needRanksSoftlyWithoutExcludingAnyone() throws Exception {
    seedTutors(3);
    UUID best = tutorId(3);
    jdbc.update("update tutor_profile set teaching_mode='BOTH' where tutor_id=?", best);
    jdbc.update("insert into tutor_offering_exam_province(offering_id,province_id) select offering_id, ? from tutor_offering where tutor_id=?", hanoi, best);
    mvc.perform(get("/api/v1/discovery/tutors").param("needId", NEED.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.content[0].tutorId").value(best.toString()))
        .andExpect(jsonPath("$.data.content[0].fit.checks.length()").value(5));
  }

  private UUID tutorId(int index) {
    return UUID.fromString(String.format("00000000-0000-0000-0000-%012x", index));
  }

  private void seedTutors(int count) {
    jdbc.update("""
        insert into account(user_id,email,password_hash,full_name,role)
        select md5('user-'||i)::uuid,'tutor-'||i||'@example.test','test-only','Tutor','TUTOR' from generate_series(1,?) i
        """, count);
    jdbc.update("""
        insert into tutor_profile(tutor_id,user_id,display_name,headline,bio,teaching_mode,tutor_type,tutor_type_verified,
            province_id,status,rating_avg,review_count,experience_years,created_at,updated_at)
        select lpad(to_hex(i),32,'0')::uuid,md5('user-'||i)::uuid,'Tutor '||i,'Toán','Public biography','ONLINE','GRADUATE',true,
            ?,'VERIFIED',4.5,10,2,'2025-01-01'::timestamptz,'2025-01-01'::timestamptz from generate_series(1,?) i
        """, hanoi, count);
    jdbc.update("insert into tutor_district(tutor_id,district_id) select tutor_id,? from tutor_profile", cauGiay);
    jdbc.update("""
        insert into tutor_offering(tutor_id,subject_id,price_per_session)
        select tutor_id,?,200000 from tutor_profile
        """, math);
    jdbc.update("insert into tutor_offering_level(offering_id,level_id) select offering_id,l from tutor_offering cross join (values(?),(?)) levels(l)", level8, level9);
    jdbc.update("insert into tutor_offering_goal(offering_id,goal_tag_id,evidence_verified) select offering_id,g,false from tutor_offering cross join (values(?),(?)) goals(g)", grade10, foundation);
    jdbc.update("insert into tutor_offering_textbook(offering_id,series_id) select offering_id,? from tutor_offering", kntt);
    jdbc.update("insert into tutor_availability_slot(tutor_id,day_of_week,start_time,end_time) select tutor_id,2,'18:00'::time,'22:00'::time from tutor_profile");
  }

  private void assertDetached() {
    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    assertFalse(TransactionSynchronizationManager.hasResource(emf), "No request-scoped EntityManager/OSIV");
  }

  private long queryCount() {
    return emf.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount();
  }

  private void clearSql() {
    emf.unwrap(SessionFactory.class).getStatistics().clear();
    sql.statements.clear();
  }

  static class SqlCapture implements StatementInspector {
    final List<String> statements = new ArrayList<>();
    @Override public String inspect(String statement) { statements.add(statement); return statement; }
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackages = {"vn.edufit.profile.infra.persistence.repository", "vn.edufit.discovery.infra.persistence.repository"})
  @Import({ProfileFacadeImpl.class, LearningNeedService.class, LearningCatalogLoader.class, TutorProfileAssembler.class,
      DiscoveryQueryService.class, TutorMatchingService.class, MatchExplanationService.class, NeedAccessService.class})
  static class Config {
    @Bean(destroyMethod = "close") HikariDataSource dataSource() {
      var source = new HikariDataSource();
      source.setJdbcUrl(postgres.getJdbcUrl()); source.setUsername(postgres.getUsername()); source.setPassword(postgres.getPassword());
      source.setMaximumPoolSize(5);
      Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
      return source;
    }
    @Bean SqlCapture sqlCapture() { return new SqlCapture(); }
    @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source, SqlCapture capture) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan("vn.edufit.profile.infra.persistence.entity", "vn.edufit.discovery.infra.persistence.entity");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.generate_statistics", "true",
          "hibernate.session_factory.statement_inspector", capture));
      return factory;
    }
    @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
    @Bean ConnectionFacade connection() { return mock(ConnectionFacade.class); }
    @Bean ParentStudentLinkPort parentLinks() { return (parent, student) -> false; }
    @Bean MatchWeights matchWeights() { return MatchWeights.standard(); }
    @Bean AiGateway ai() { return mock(AiGateway.class); }
    @Bean CurrentUser actor() {
      var user = mock(CurrentUser.class);
      when(user.getUserId()).thenReturn(STUDENT_USER);
      when(user.hasRole(anyString())).thenAnswer(i -> "STUDENT".equals(i.getArgument(0)));
      return user;
    }
  }
}

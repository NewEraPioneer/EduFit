package vn.edufit.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.edufit.ai.api.AiGateway;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.discovery.application.service.*;
import vn.edufit.discovery.domain.model.*;
import vn.edufit.discovery.domain.policy.MatchScorer;
import vn.edufit.discovery.web.DiscoveryController;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.*;
import vn.edufit.profile.application.service.ProfileFacadeImpl;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.web.advice.GlobalExceptionHandler;

/** Detached read models and bounded workflows on the real migrated PostgreSQL schema. */
@Testcontainers
@SpringJUnitConfig(DiscoveryReadPostgresTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DiscoveryReadPostgresTest {
  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
  private static final UUID STUDENT_USER = new UUID(1, 1), STUDENT = new UUID(1, 2), GOAL = new UUID(1, 3);
  @Autowired private ProfileFacade profile;
  @Autowired private DiscoveryQueryService queries;
  @Autowired private TutorMatchingService matching;
  @Autowired private MatchExplanationService explanations;
  @Autowired private DataSource dataSource;
  @Autowired private EntityManagerFactory emf;
  @Autowired private SqlCapture sql;
  @Autowired private AiGateway ai;
  @Autowired private CurrentUser actor;
  private JdbcTemplate jdbc;
  private MockMvc mvc;

  @BeforeEach
  void fixtures() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("truncate account, subject, education_level cascade");
    jdbc.update("insert into subject(subject_id,name) values(1,'Math'),(2,'Physics')");
    jdbc.update("insert into education_level(level_id,name,sort_order) values(80,'Primary',10),(3,'Middle',20),(2,'High',30)");
    jdbc.update("insert into account(user_id,email,password_hash,full_name,role) values(?,'student@example.test','test-only','Student','STUDENT')", STUDENT_USER);
    jdbc.update("insert into student_profile(student_id,user_id,education_level_id,area) values(?,?,2,'Hanoi')", STUDENT, STUDENT_USER);
    jdbc.update("""
        insert into learning_goal(goal_id,student_id,subject_id,current_level,desired_level,goal_type,deadline,mode,area,budget_max)
        values(?,?,1,1,2,'OTHER','2027-01-01','ONLINE','Hanoi',300000)
        """, GOAL, STUDENT);
    jdbc.update("insert into goal_availability_slot(goal_id,day_of_week,start_time,end_time) values(?,1,'18:00','20:00')", GOAL);
    @SuppressWarnings("unchecked") ObjectProvider<CurrentUser> provider = mock(ObjectProvider.class);
    lenient().when(provider.getIfAvailable()).thenReturn(actor);
    mvc = MockMvcBuilders.standaloneSetup(new DiscoveryController(queries, matching, provider))
        .setControllerAdvice(new GlobalExceptionHandler()).build();
    reset(ai);
    when(ai.complete(any())).thenReturn(new vn.edufit.ai.api.AiResponse("Offline fixture", 0, null, false));
    clearSql();
  }

  @ParameterizedTest
  @ValueSource(ints = {20, 200, 240})
  void fixedQueryCountsAndDetachedHydration(int count) {
    seedTutors(count);
    clearSql();
    var page = queries.searchTutors(null, PageRequest.of(0, 10, Sort.by("pricePerSession")));
    assertEquals(count, page.getTotalElements());
    assertEquals(5, queryCount(), "Search includes page, count, detail rows, subjects and slots");
    assertDetached();
    assertEquals(2, page.getContent().getFirst().subjects().size());
    assertEquals(1, page.getContent().getFirst().availabilitySlots().size());
    assertTrue(sql.statements.stream().anyMatch(s -> s.toLowerCase().contains("count(")));
    clearSql();
    var detail = queries.getTutorDetail(id(1));
    assertEquals(3, queryCount());
    assertDetached();
    assertEquals("Math", detail.subjects().getFirst().subjectName());
    assertEquals("18:00", detail.availabilitySlots().getFirst().startTime().toString());
    clearSql();
    var candidates = profile.findVerifiedCandidatesBySubject(new TutorCandidateCriteria(1, "ONLINE", "Hanoi"));
    assertEquals(Math.min(count, 200), candidates.size());
    assertEquals(3, queryCount());
    assertTrue(sql.statements.getFirst().contains("fetch first"));
    clearSql();
    assertEquals(5, matching.match(actor, GOAL, 5).size());
    assertEquals(8, queryCount(), "Goal/student/slots, catalog, bounded candidates/subjects/slots, insert log");
    assertDetached();
    verify(ai).complete(any()); // Gateway owns admission; this fixture performs no model call.
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
      "rating,asc", "rating,desc", "relevance,asc", "relevance,desc",
      "price,asc", "price,desc", "experience,asc", "experience,desc"})
  void httpPaginationUsesUniqueTutorTieBreak(String sort, String direction) throws Exception {
    seedTutors(23);
    jdbc.update("update tutor_profile set price_per_session=200000, rating_avg=4.5, review_count=10, experience_years=2");
    var found = new ArrayList<UUID>();
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    for (int page = 0; page < 3; page++) {
      var response = mvc.perform(get("/api/v1/discovery/tutors").param("page",String.valueOf(page))
          .param("size","10").param("sortBy",sort).param("sortDirection",direction)
          .param("area","  ").param("keyword","  "))
          .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(23))
          .andExpect(jsonPath("$.data.content.length()").value(page == 2 ? 3 : 10))
          .andReturn().getResponse().getContentAsString();
      for (var tutor : mapper.readTree(response).path("data").path("content")) {
        found.add(UUID.fromString(tutor.path("tutorId").asText()));
      }
    }
    assertEquals(java.util.stream.IntStream.rangeClosed(1,23).mapToObj(this::id).toList(), found,
        "All equal sort keys must use ascending unique tutorId across every HTTP page");
    assertEquals(23,found.stream().distinct().count());
    mvc.perform(get("/api/v1/discovery/tutors").param("page","3"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty());
  }

  @Test
  void paginationHasNoDuplicatesOrMissingRowsDespiteMultipleSubjects() throws Exception {
    seedTutors(23);
    var found = new ArrayList<UUID>();
    for (int page = 0; page < 3; page++) {
      var result = queries.searchTutors(null, PageRequest.of(page, 10, Sort.by("pricePerSession")));
      assertEquals(23, result.getTotalElements());
      found.addAll(result.stream().map(p -> p.tutor().tutorId()).toList());
    }
    assertEquals(java.util.stream.IntStream.rangeClosed(1, 23).mapToObj(this::id).toList(), found);
    assertEquals(23, found.stream().distinct().count());
    mvc.perform(get("/api/v1/discovery/tutors").param("page", "2").param("sortBy", "price").param("sortDirection", "asc"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(23))
        .andExpect(jsonPath("$.data.content.length()").value(3))
        .andExpect(jsonPath("$.data.content[0].subjects.length()").value(2));
    assertDetached();
  }

  @Test
  void combinedFiltersEmptyAndNotFoundRespectVerifiedContract() throws Exception {
    seedTutors(8);
    jdbc.update("update tutor_profile set status='UNVERIFIED' where tutor_id=?", id(2));
    jdbc.update("update tutor_profile set teaching_mode='OFFLINE' where tutor_id=?", id(3));
    jdbc.update("update tutor_profile set area='Hue' where tutor_id=?", id(4));
    jdbc.update("update tutor_profile set price_per_session=900000 where tutor_id=?", id(5));
    jdbc.update("update tutor_profile set rating_avg=1 where tutor_id=?", id(6));
    jdbc.update("update tutor_availability_slot set day_of_week=2 where tutor_id=?", id(7));
    jdbc.update("update tutor_subject set subject_id=2 where tutor_id=?", id(8));
    mvc.perform(get("/api/v1/discovery/tutors").param("subjectId", "1").param("levelId", "2")
            .param("area", "hAnOi").param("mode", "ONLINE").param("minPrice", "100000").param("maxPrice", "300000")
            .param("minRating", "4").param("keyword", "Math").param("dayOfWeek", "1")
            .param("availableFrom", "19:00").param("availableTo", "21:00"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].tutorId").value(id(1).toString()))
        .andExpect(jsonPath("$.data.content[0].subjects.length()").value(2));
    mvc.perform(get("/api/v1/discovery/tutors/{id}", id(1)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.availabilitySlots.length()").value(1));
    for (UUID missing : List.of(id(2), id(999))) {
      assertThrows(EntityNotFoundException.class, () -> queries.getTutorDetail(missing));
      mvc.perform(get("/api/v1/discovery/tutors/{id}", missing)).andExpect(status().isNotFound());
    }
    clearSql();
    var empty = queries.searchTutors(new TutorSearchCriteria(999, null, null, null, null, null, null), PageRequest.of(0, 10));
    assertTrue(empty.isEmpty());
    assertEquals(1, queryCount(), "Empty first page skips count and hydration");
    assertDetached();
  }

  private UUID id(int index) { return new UUID(0, index); }

  @ParameterizedTest
  @ValueSource(strings = {"subject", "level", "area", "mode", "minPrice", "maxPrice", "rating", "name", "headline", "bio", "subjectName", "day", "touching"})
  void eachSearchFilterIsAppliedByPostgres(String filter) throws Exception {
    seedTutors(2);
    var request = get("/api/v1/discovery/tutors");
    switch (filter) {
      case "subject", "subjectName" -> {
        jdbc.update("update tutor_subject set subject_id=2 where tutor_id=?", id(2));
        jdbc.update("update tutor_profile set headline='Skills'");
        request.param(filter.equals("subject") ? "subjectId" : "keyword", filter.equals("subject") ? "1" : "Math");
      }
      case "level" -> {
        jdbc.update("delete from tutor_subject where tutor_id=? and education_level_id=2", id(2));
        request.param("levelId", "2");
      }
      case "area" -> {
        jdbc.update("update tutor_profile set area='Hue' where tutor_id=?", id(2));
        request.param("area", "hAnOi");
      }
      case "mode" -> {
        jdbc.update("update tutor_profile set teaching_mode='OFFLINE' where tutor_id=?", id(2));
        request.param("mode", "ONLINE");
      }
      case "minPrice", "maxPrice" -> {
        jdbc.update("update tutor_profile set price_per_session=? where tutor_id=?", filter.equals("minPrice") ? 100000 : 900000, id(2));
        request.param(filter, filter.equals("minPrice") ? "150000" : "300000");
      }
      case "rating" -> {
        jdbc.update("update tutor_profile set rating_avg=1 where tutor_id=?", id(2));
        request.param("minRating", "4");
      }
      case "name", "headline", "bio" -> {
        String column = filter.equals("name") ? "display_name" : filter;
        // Column is selected from the closed test-case set, never request input.
        jdbc.update("update tutor_profile set " + column + "='Needle' where tutor_id=?", id(1));
        request.param("keyword", "needle");
      }
      case "day", "touching" -> {
        if (filter.equals("day")) jdbc.update("update tutor_availability_slot set day_of_week=2 where tutor_id=?", id(2));
        else jdbc.update("update tutor_availability_slot set start_time='20:00',end_time='22:00' where tutor_id=?", id(2));
        request.param("dayOfWeek", "1").param("availableFrom", "18:00").param("availableTo", "20:00");
      }
      default -> throw new IllegalArgumentException(filter);
    }
    mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].tutorId").value(id(1).toString()));
    assertDetached();
  }

  @ParameterizedTest
  @ValueSource(ints = {20, 200, 240})
  void recordLocalLatencyAndRepresentativeQueryPlans(int count) throws Exception {
    seedTutors(count);
    jdbc.execute("analyze tutor_profile");
    jdbc.execute("analyze tutor_subject");
    measure("search-http", count, 5, () -> mvc.perform(get("/api/v1/discovery/tutors").param("sortBy", "price"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(10)));
    measure("detail-http", count, 3, () -> mvc.perform(get("/api/v1/discovery/tutors/{id}", id(1)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.subjects.length()").value(2)));
    measure("match-http-no-ai", count, 8, () -> mvc.perform(post("/api/v1/discovery/match")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"goalId\":\"" + GOAL + "\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(5)));
    measure("candidate-db-hydration", count, 3,
        () -> profile.findVerifiedCandidatesBySubject(new TutorCandidateCriteria(1, "ONLINE", "Hanoi")));
    var candidates = profile.findVerifiedCandidatesBySubject(new TutorCandidateCriteria(1, "ONLINE", "Hanoi"));
    measure("scorer-only", count, 0, () -> rank(candidates));
    verify(ai, times(35)).complete(any()); // Five warm-ups plus thirty matching samples.
    if (count == 240) {
      explain("search", "select t.* from tutor_profile t where t.status='VERIFIED' order by t.price_per_session desc limit 10");
      explain("candidate", """
          select t.* from tutor_profile t where t.status='VERIFIED'
            and exists(select 1 from tutor_subject ts where ts.tutor_id=t.tutor_id and ts.subject_id=1)
            and t.teaching_mode in ('ONLINE','BOTH')
          order by coalesce(t.rating_avg,0) desc,t.review_count desc,t.created_at,t.tutor_id limit 200
          """);
    }
  }

  @Test
  void candidateCapCanMissGlobalWinnerAndReportsTopFiveRecall() {
    seedTutors(240);
    jdbc.update("delete from tutor_subject where education_level_id=2");
    jdbc.update("update tutor_subject set education_level_id=2 where tutor_id=?", id(240));
    jdbc.update("update tutor_profile set rating_avg=4.0 where tutor_id=?", id(240));
    var bounded = rank(profile.findVerifiedCandidatesBySubject(new TutorCandidateCriteria(1, "ONLINE", "Hanoi")));
    var fullIds = jdbc.query("select tutor_id from tutor_profile where status='VERIFIED'", (rs, n) -> rs.getObject(1, UUID.class));
    // Uncapped oracle is test-only: hydrate all fixture IDs, never call the legacy matching API.
    var full = rank(profile.findVerifiedTutorDetails(fullIds));
    assertEquals(200, bounded.size());
    assertEquals(240, full.size());
    assertEquals(id(240), full.getFirst().tutorId());
    assertEquals(new java.math.BigDecimal("95.00"), full.getFirst().total());
    assertEquals(new java.math.BigDecimal("77.50"), bounded.getFirst().total());
    assertFalse(bounded.stream().anyMatch(s -> s.tutorId().equals(id(240))));
    var fullTop = full.stream().limit(5).map(MatchScore::tutorId).toList();
    long shared = bounded.stream().limit(5).map(MatchScore::tutorId).filter(fullTop::contains).count();
    assertEquals(4, shared);
    System.out.println("CAP_REPORT candidates=200/240 globalTop1=95.00 boundedTop1=77.50 recallAt5=" + shared + "/5");
  }

  private List<MatchScore> rank(List<TutorDiscoveryProfileDto> candidates) {
    var scorer = new MatchScorer(new EducationLevelOrder(List.of(80, 3, 2)));
    var slots = List.of(new AvailabilitySlot((short) 1, java.time.LocalTime.of(18, 0), java.time.LocalTime.of(20, 0)));
    var criteria = new MatchCriteria(1, 2, "ONLINE", "Hanoi", 0L, 300000L, slots);
    return candidates.stream().map(p -> {
      var tutor = p.tutor();
      var subjects = p.subjects().stream().map(s -> new SubjectLevel(s.subjectId(), s.educationLevelId()))
          .collect(java.util.stream.Collectors.toSet());
      var offered = p.availabilitySlots().stream().map(s -> new AvailabilitySlot(s.dayOfWeek(), s.startTime(), s.endTime())).toList();
      return new TutorCandidate(tutor.tutorId(), subjects, tutor.teachingMode(), tutor.area(), tutor.pricePerSession(),
          tutor.ratingAvg(), tutor.reviewCount(), p.registeredAt(), offered);
    }).map(t -> scorer.score(criteria, t).orElseThrow()).sorted(MatchScore.rankingOrder()).toList();
  }

  @FunctionalInterface private interface Operation { void run() throws Exception; }

  private void measure(String operation, int count, long expectedQueries, Operation work) throws Exception {
    for (int i = 0; i < 5; i++) work.run();
    var elapsed = new long[30];
    for (int i = 0; i < elapsed.length; i++) {
      clearSql();
      long start = System.nanoTime();
      work.run();
      elapsed[i] = System.nanoTime() - start;
      assertEquals(expectedQueries, queryCount());
      assertDetached();
    }
    java.util.Arrays.sort(elapsed);
    System.out.printf(java.util.Locale.ROOT, "PERF operation=%s tutors=%d samples=30 queries=%d p50Ms=%.3f p95Ms=%.3f maxMs=%.3f%n",
        operation, count, expectedQueries, elapsed[14] / 1e6, elapsed[28] / 1e6, elapsed[29] / 1e6);
  }

  private void explain(String name, String statement) {
    var plan = jdbc.query("explain (analyze, buffers) " + statement, (rs, n) -> rs.getString(1));
    assertTrue(plan.stream().anyMatch(line -> line.contains("Limit")));
    System.out.println("QUERY_PLAN " + name + "\n" + String.join("\n", plan));
  }

  private void seedTutors(int count) {
    jdbc.update("""
        insert into account(user_id,email,password_hash,full_name,role)
        select md5('user-'||i)::uuid,'tutor-'||i||'@example.test','test-only','Tutor','TUTOR' from generate_series(1,?) i
        """, count);
    jdbc.update("""
        insert into tutor_profile(tutor_id,user_id,display_name,headline,bio,teaching_mode,area,price_per_session,status,
            rating_avg,review_count,created_at,updated_at)
        select lpad(to_hex(i),32,'0')::uuid,md5('user-'||i)::uuid,'Tutor '||i,'Math','Public biography','ONLINE','Hanoi',
            200000+i,'VERIFIED',4.5,10,'2025-01-01'::timestamptz,'2025-01-01'::timestamptz from generate_series(1,?) i
        """, count);
    jdbc.update("insert into tutor_subject(tutor_id,subject_id,education_level_id) select tutor_id,1,l from tutor_profile cross join (values(2),(80)) levels(l)");
    jdbc.update("insert into tutor_availability_slot(tutor_id,day_of_week,start_time,end_time) select tutor_id,1,'18:00'::time,'20:00'::time from tutor_profile");
  }

  private void assertDetached() {
    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    assertFalse(TransactionSynchronizationManager.hasResource(emf), "No request-scoped EntityManager/OSIV");
  }
  private long queryCount() { return emf.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount(); }
  private void clearSql() { emf.unwrap(SessionFactory.class).getStatistics().clear(); sql.statements.clear(); }

  static class SqlCapture implements StatementInspector {
    final List<String> statements = new ArrayList<>();
    @Override public String inspect(String statement) { statements.add(statement); return statement; }
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackages = {"vn.edufit.profile.infra.persistence.repository", "vn.edufit.discovery.infra.persistence.repository"})
  @Import({ProfileFacadeImpl.class, DiscoveryQueryService.class, TutorMatchingService.class, MatchExplanationService.class})
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
    @Bean AiGateway ai() { return mock(AiGateway.class); }
    @Bean CurrentUser actor() {
      var user = mock(CurrentUser.class);
      when(user.getUserId()).thenReturn(STUDENT_USER);
      when(user.hasRole(anyString())).thenAnswer(i -> "STUDENT".equals(i.getArgument(0)));
      return user;
    }
  }
}

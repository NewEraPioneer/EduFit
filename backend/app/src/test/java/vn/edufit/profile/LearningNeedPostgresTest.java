package vn.edufit.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;
import vn.edufit.profile.web.CatalogController;
import vn.edufit.profile.web.LearningNeedController;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.web.advice.GlobalExceptionHandler;

/**
 * Nhu cầu học trên schema thật sau toàn bộ migration (Hibernate validate): danh mục, luật Lớp/Môn/Mục tiêu,
 * tạo nguyên tử kèm khung giờ, lỗi theo từng trường, quyền của học sinh và phụ huynh đã liên kết.
 */
@Testcontainers
@SpringJUnitConfig(LearningNeedPostgresTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LearningNeedPostgresTest {
  static final String ROOT = "/api/v1/learning-needs";

  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  @Autowired ApplicationContext context;
  @Autowired DataSource source;
  @Autowired CurrentUser user;
  JdbcTemplate jdbc;
  MockMvc mvc;
  UUID accountId, studentId;
  int math, level6, level9, grade10, foundation, kntt, hanoi, cauGiay;

  @BeforeEach
  void setup() {
    jdbc = new JdbcTemplate(source);
    jdbc.execute("truncate account cascade");
    math = id("select subject_id from subject where name='Toán'");
    level6 = id("select level_id from education_level where name='Lớp 6'");
    level9 = id("select level_id from education_level where name='Lớp 9'");
    grade10 = id("select goal_tag_id from goal_tag where code='GRADE10_EXAM'");
    foundation = id("select goal_tag_id from goal_tag where code='FOUNDATION'");
    kntt = id("select series_id from textbook_series where code='KNTT'");
    hanoi = id("select province_id from province where code='HA_NOI'");
    cauGiay = id("select district_id from district where name='Cầu Giấy'");
    accountId = UUID.randomUUID();
    studentId = UUID.randomUUID();
    account(accountId, "STUDENT");
    jdbc.update("insert into student_profile(student_id,user_id) values(?,?)", studentId, accountId);
    reset(user);
    actor(accountId, "STUDENT");
    mvc = MockMvcBuilders.standaloneSetup(
            context.getBean(LearningNeedController.class), context.getBean(CatalogController.class))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  int id(String sql) {
    return jdbc.queryForObject(sql, Integer.class);
  }

  @Test
  void learningCatalogComesFromMigrations() throws Exception {
    mvc.perform(get("/api/v1/catalogs/learning"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.subjects.length()").value(4))
        .andExpect(jsonPath("$.data.goalTagRules.length()").value(80))
        .andExpect(jsonPath("$.data.provinces.length()").value(34));
    mvc.perform(get("/api/v1/catalogs/goal-tags").param("levelId", "" + level6).param("subjectId", "" + math))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(3));
  }

  @Test
  void onboardingNeedIsIncompleteAndCompletedNeedIsActive() throws Exception {
    UUID id = create(classification(null));
    mvc.perform(get(ROOT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].needId").value(id.toString()))
        .andExpect(jsonPath("$.data[0].status").value("INCOMPLETE"))
        .andExpect(jsonPath("$.data[0].examProvinceName").value("Hà Nội"))
        .andExpect(jsonPath("$.data[0].missingFields[0]").value("mode"));
    assertEquals(accountId, jdbc.queryForObject("select created_by from learning_need where need_id=?", UUID.class, id));

    mvc.perform(put(ROOT + "/" + id).contentType("application/json").content(complete(null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.districtName").value("Cầu Giấy"))
        .andExpect(jsonPath("$.data.availabilitySlots.length()").value(1));
    mvc.perform(post(ROOT + "/" + id + "/archive"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    mvc.perform(put(ROOT + "/" + id).contentType("application/json").content(complete(null)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void invalidCombinationReturnsFieldErrors() throws Exception {
    String grade6Exam = """
        {"educationLevelId":%d,"subjectId":%d,"goalTagId":%d,"examProvinceId":%d}
        """.formatted(level6, math, grade10, hanoi);
    mvc.perform(post(ROOT).contentType("application/json").content(grade6Exam))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0].field").value("goalTagId"));
    String missingTextbook = """
        {"educationLevelId":%d,"subjectId":%d,"goalTagId":%d}
        """.formatted(level9, math, foundation);
    mvc.perform(post(ROOT).contentType("application/json").content(missingTextbook))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0].field").value("textbookSeriesId"));
    String withTextbook = """
        {"educationLevelId":%d,"subjectId":%d,"goalTagId":%d,"textbookSeriesId":%d}
        """.formatted(level9, math, foundation, kntt);
    mvc.perform(post(ROOT).contentType("application/json").content(withTextbook)).andExpect(status().isCreated());
    assertEquals(1, id("select count(*) from learning_need"));
  }

  @Test
  void linkedParentManagesChildNeedsButUnlinkedParentCannot() throws Exception {
    UUID parent = UUID.randomUUID();
    account(parent, "PARENT");
    actor(parent, "PARENT");
    mvc.perform(post(ROOT).contentType("application/json").content(complete(studentId)))
        .andExpect(status().isForbidden());

    jdbc.update("insert into parent_student_link(link_id,parent_user_id,student_id,confirmed_at,status) values (?,?,?,now(),'CONFIRMED')",
        UUID.randomUUID(), parent, studentId);
    UUID id = create(complete(studentId));
    assertEquals(parent, jdbc.queryForObject("select created_by from learning_need where need_id=?", UUID.class, id));
    mvc.perform(get(ROOT).param("studentId", studentId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));

    actor(accountId, "STUDENT");
    mvc.perform(get(ROOT)).andExpect(jsonPath("$.data[0].needId").value(id.toString()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"TUTOR", "ADMIN", "GUEST"})
  void otherRolesCannotManageNeeds(String role) throws Exception {
    actor(accountId, role);
    mvc.perform(get(ROOT)).andExpect(status().isForbidden());
    mvc.perform(post(ROOT).contentType("application/json").content(classification(null)))
        .andExpect(status().isForbidden());
  }

  @Test
  void otherStudentCannotSeeOrEditNeed() throws Exception {
    UUID id = create(classification(null));
    UUID other = UUID.randomUUID();
    account(other, "STUDENT");
    jdbc.update("insert into student_profile(student_id,user_id) values(?,?)", UUID.randomUUID(), other);
    actor(other, "STUDENT");
    mvc.perform(get(ROOT)).andExpect(jsonPath("$.data.length()").value(0));
    mvc.perform(put(ROOT + "/" + id).contentType("application/json").content(complete(null)))
        .andExpect(status().isForbidden());
  }

  UUID create(String body) throws Exception {
    String response = mvc.perform(post(ROOT).contentType("application/json").content(body))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return UUID.fromString(JsonMapper.builder().build().readTree(response).get("data").get("needId").asText());
  }

  String classification(UUID forStudent) {
    return """
        {%s"educationLevelId":%d,"subjectId":%d,"goalTagId":%d,"examProvinceId":%d}
        """.formatted(forStudent == null ? "" : "\"studentId\":\"" + forStudent + "\",", level9, math, grade10, hanoi);
  }

  String complete(UUID forStudent) {
    return """
        {%s"educationLevelId":%d,"subjectId":%d,"goalTagId":%d,"examProvinceId":%d,
         "mode":"OFFLINE","provinceId":%d,"districtId":%d,"budgetMaxPerSession":300000,
         "availabilitySlots":[{"dayOfWeek":2,"startTime":"19:00","endTime":"21:00"}]}
        """.formatted(forStudent == null ? "" : "\"studentId\":\"" + forStudent + "\",", level9, math, grade10, hanoi,
        hanoi, cauGiay);
  }

  void account(UUID id, String role) {
    jdbc.update("insert into account(user_id,email,password_hash,full_name,role) values(?,?,'test-only','Test',?)",
        id, id + "@example.test", role);
  }

  void actor(UUID id, String role) {
    when(user.getUserId()).thenReturn(role.equals("GUEST") ? null : id);
    when(user.hasRole(anyString())).thenAnswer(call -> role.equals(call.getArgument(0)));
  }

  @Configuration
  @EnableTransactionManagement(proxyTargetClass = true)
  @EnableJpaRepositories({
    "vn.edufit.profile.infra.persistence.repository",
    "vn.edufit.connection.infra.persistence.repository"
  })
  @Import({
    LearningNeedController.class,
    CatalogController.class,
    vn.edufit.profile.application.service.LearningNeedService.class,
    vn.edufit.profile.application.service.LearningCatalogLoader.class,
    vn.edufit.profile.application.service.CatalogQueryService.class,
    vn.edufit.connection.infra.adapter.ParentStudentLinkAdapter.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
      Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
      return source;
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan("vn.edufit.profile.infra.persistence.entity", "vn.edufit.connection.infra.persistence");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
      return factory;
    }

    @Bean
    JpaTransactionManager transactionManager(EntityManagerFactory factory) {
      return new JpaTransactionManager(factory);
    }

    @Bean
    CurrentUser user() {
      return mock(CurrentUser.class);
    }

    @Bean
    jakarta.persistence.EntityManager entityManager(EntityManagerFactory factory) {
      return SharedEntityManagerCreator.createSharedEntityManager(factory);
    }

    @Bean
    org.springframework.validation.beanvalidation.LocalValidatorFactoryBean validator() {
      return new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();
    }
  }
}

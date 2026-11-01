package vn.edufit.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.profile.api.dto.TutorSearchCriteria;
import vn.edufit.profile.application.service.LearningCatalogLoader;
import vn.edufit.profile.application.service.LearningNeedService;
import vn.edufit.profile.application.service.ProfileFacadeImpl;
import vn.edufit.profile.application.service.TutorProfileAssembler;
import vn.edufit.profile.infra.persistence.repository.*;

/** Chạy JPQL thật (member of, exists trên gói dạy) và nạp theo lô trên PostgreSQL cô lập. */
@Testcontainers
@SpringJUnitConfig(MatchingCandidatesPostgresTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MatchingCandidatesPostgresTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  private static final int MATH = 1;
  private static final int SCIENCE = 2;
  private static final int LEVEL_8 = 3;
  private static final int LEVEL_9 = 4;
  private static final int GIFTED = 3;
  private static final int GRADE10 = 4;
  private static final int KNTT = 1;
  private static final int HA_NOI = 1;
  private static final int CAU_GIAY = 5;

  @Autowired private ProfileFacade facade;
  @Autowired private DataSource dataSource;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private SqlCapture sqlCapture;
  private JdbcTemplate jdbc;

  @BeforeEach
  void resetFixtures() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("""
        truncate tutor_offering_level, tutor_offering_goal, tutor_offering_textbook, tutor_offering_exam_province,
          tutor_offering, tutor_district, tutor_availability_slot, tutor_profile, subject, education_level,
          goal_tag, textbook_series, province, district cascade
        """);
    jdbc.update("insert into subject (subject_id,name,is_active) values (1,'Toán',true),(2,'Khoa học tự nhiên',true)");
    jdbc.update("insert into education_level (level_id,name,sort_order,is_active) values (3,'Lớp 8',3,true),(4,'Lớp 9',4,true)");
    jdbc.update("""
        insert into goal_tag (goal_tag_id,code,name,requires_textbook,requires_exam_province,is_premium,sort_order,is_active)
        values (3,'GIFTED','Bồi dưỡng HSG & Thi chuyên',false,false,true,3,true),
               (4,'GRADE10_EXAM','Luyện thi vào 10',false,true,false,4,true)
        """);
    jdbc.update("insert into textbook_series (series_id,code,name,sort_order,is_active) values (1,'KNTT','Kết nối tri thức',1,true)");
    jdbc.update("insert into province (province_id,code,name,sort_order,is_active) values (1,'HA_NOI','Hà Nội',1,true)");
    jdbc.update("insert into district (district_id,province_id,name,sort_order,is_active) values (5,1,'Cầu Giấy',5,true),(6,1,'Đống Đa',6,true)");
  }

  @Test
  void candidatesRequireVerifiedTutorWithOfferingInSubject() {
    offering(tutor(1, "math-verified", "ONLINE", "VERIFIED"), MATH, 200000, List.of(LEVEL_9), List.of());
    offering(tutor(2, "math-unverified", "ONLINE", "UNVERIFIED"), MATH, 200000, List.of(LEVEL_9), List.of());
    offering(tutor(3, "science-only", "ONLINE", "VERIFIED"), SCIENCE, 200000, List.of(LEVEL_9), List.of());
    tutor(4, "no-offering", "ONLINE", "VERIFIED");

    var results = facade.findVerifiedCandidatesBySubject(MATH);

    assertEquals(Set.of("math-verified"), names(results));
  }

  @Test
  void capsCandidatesInSqlAndHydratesOfferingsInBatches() {
    for (int index = 1; index <= 205; index++) {
      UUID tutorId = tutor(index, "Tutor " + index, "ONLINE", "VERIFIED");
      offering(tutorId, MATH, 200000, List.of(LEVEL_8, LEVEL_9), List.of(GRADE10));
      jdbc.update("insert into tutor_availability_slot (slot_id,tutor_id,day_of_week,start_time,end_time) values (?,?,1,'18:00','20:00')",
          UUID.randomUUID(), tutorId);
    }
    clearQueryStatistics();

    var results = facade.findVerifiedCandidatesBySubject(MATH);

    assertEquals(200, results.size());
    assertEquals(new UUID(0, 1), results.getFirst().tutor().tutorId());
    assertTrue(results.stream().allMatch(profile -> profile.offerings().size() == 1
        && profile.offerings().getFirst().levels().size() == 2
        && profile.offerings().getFirst().goals().size() == 1
        && profile.availabilitySlots().size() == 1));
    assertTrue(sqlCapture.statements.getFirst().toLowerCase().contains("fetch first"),
        "Candidate cap must be applied in SQL before association hydration.");
    assertTrue(statistics().getPrepareStatementCount() < 40,
        "Offerings and their collections must be loaded in batches, not per tutor.");
  }

  @Test
  void searchRequiresOfferingCriteriaOnTheSameOffering() {
    UUID both = tutor(1, "same-offering", "BOTH", "VERIFIED");
    offering(both, MATH, 300000, List.of(LEVEL_9), List.of(GIFTED));
    UUID split = tutor(2, "split-offerings", "BOTH", "VERIFIED");
    offering(split, MATH, 300000, List.of(LEVEL_8), List.of(GIFTED));
    offering(split, SCIENCE, 300000, List.of(LEVEL_9), List.of());

    var criteria = new TutorSearchCriteria(LEVEL_9, MATH, null, GIFTED, null, null, null, null, null, null, null,
        null, null, null, null, null);

    assertEquals(Set.of("same-offering"), names(facade.searchVerifiedTutors(criteria, 50)));
  }

  @Test
  void searchFiltersPriceDistrictAndConfirmedTutorType() {
    UUID cheapLocal = tutor(1, "cheap-local", "BOTH", "VERIFIED");
    offering(cheapLocal, MATH, 180000, List.of(LEVEL_9), List.of(GRADE10));
    jdbc.update("update tutor_profile set province_id=1, tutor_type='GRADUATE', tutor_type_verified=true where tutor_id=?", cheapLocal);
    jdbc.update("insert into tutor_district (tutor_id,district_id) values (?,5)", cheapLocal);
    UUID expensive = tutor(2, "expensive-local", "BOTH", "VERIFIED");
    offering(expensive, MATH, 500000, List.of(LEVEL_9), List.of(GRADE10));
    jdbc.update("update tutor_profile set province_id=1, tutor_type='GRADUATE', tutor_type_verified=true where tutor_id=?", expensive);
    jdbc.update("insert into tutor_district (tutor_id,district_id) values (?,5)", expensive);
    UUID unconfirmed = tutor(3, "unconfirmed-type", "BOTH", "VERIFIED");
    offering(unconfirmed, MATH, 180000, List.of(LEVEL_9), List.of(GRADE10));
    jdbc.update("update tutor_profile set province_id=1, tutor_type='GRADUATE', tutor_type_verified=false where tutor_id=?", unconfirmed);
    jdbc.update("insert into tutor_district (tutor_id,district_id) values (?,5)", unconfirmed);
    UUID otherDistrict = tutor(4, "other-district", "BOTH", "VERIFIED");
    offering(otherDistrict, MATH, 180000, List.of(LEVEL_9), List.of(GRADE10));
    jdbc.update("update tutor_profile set province_id=1, tutor_type='GRADUATE', tutor_type_verified=true where tutor_id=?", otherDistrict);
    jdbc.update("insert into tutor_district (tutor_id,district_id) values (?,6)", otherDistrict);

    var criteria = new TutorSearchCriteria(null, MATH, null, null, null, "GRADUATE", "OFFLINE", HA_NOI, CAU_GIAY,
        null, 250000L, null, null, null, null, null);
    var results = facade.searchVerifiedTutors(criteria, 50);

    assertEquals(Set.of("cheap-local"), names(results));
    var hydrated = results.getFirst();
    assertEquals("Hà Nội", hydrated.tutor().provinceName());
    assertEquals(List.of("Cầu Giấy"), hydrated.districts().stream().map(d -> d.name()).toList());
    assertEquals(180000L, hydrated.offerings().getFirst().pricePerSession());
  }

  @Test
  void searchByTextbookAndNoCriteriaReturnsAllVerified() {
    UUID withBook = tutor(1, "with-book", "ONLINE", "VERIFIED");
    UUID offeringId = offering(withBook, MATH, 200000, List.of(LEVEL_9), List.of());
    jdbc.update("insert into tutor_offering_textbook (offering_id,series_id) values (?,?)", offeringId, KNTT);
    offering(tutor(2, "without-book", "ONLINE", "VERIFIED"), MATH, 200000, List.of(LEVEL_9), List.of());

    var byBook = new TutorSearchCriteria(null, null, null, null, KNTT, null, null, null, null, null, null,
        new BigDecimal("0"), null, null, null, null);
    assertEquals(Set.of("with-book"), names(facade.searchVerifiedTutors(byBook, 50)));
    assertEquals(Set.of("with-book", "without-book"), names(facade.searchVerifiedTutors(TutorSearchCriteria.none(), 50)));
  }

  private static Set<String> names(List<vn.edufit.profile.api.dto.TutorDiscoveryProfileDto> results) {
    return results.stream().map(profile -> profile.tutor().displayName()).collect(Collectors.toSet());
  }

  private UUID tutor(long index, String name, String mode, String status) {
    UUID tutorId = new UUID(0, index);
    var createdAt = Timestamp.from(Instant.parse("2025-01-01T00:00:00Z"));
    jdbc.update("""
        insert into tutor_profile (tutor_id,user_id,display_name,teaching_mode,tutor_type_verified,
            status,rating_avg,review_count,version,created_at,updated_at)
        values (?,?,?,?,false,?,4.5,10,0,?,?)
        """, tutorId, UUID.randomUUID(), name, mode, status, createdAt, createdAt);
    return tutorId;
  }

  private UUID offering(UUID tutorId, int subjectId, long price, List<Integer> levels, List<Integer> goals) {
    UUID offeringId = UUID.randomUUID();
    var now = Timestamp.from(Instant.parse("2025-01-01T00:00:00Z"));
    jdbc.update("""
        insert into tutor_offering (offering_id,tutor_id,subject_id,track_id,price_per_session,created_at,updated_at)
        values (?,?,?,null,?,?,?)
        """, offeringId, tutorId, subjectId, price, now, now);
    levels.forEach(level -> jdbc.update("insert into tutor_offering_level (offering_id,level_id) values (?,?)", offeringId, level));
    goals.forEach(goal -> jdbc.update(
        "insert into tutor_offering_goal (offering_id,goal_tag_id,evidence_verified) values (?,?,false)", offeringId, goal));
    return offeringId;
  }

  private org.hibernate.stat.Statistics statistics() {
    return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
  }

  private void clearQueryStatistics() {
    statistics().clear();
    sqlCapture.statements.clear();
  }

  static class SqlCapture implements StatementInspector {
    final List<String> statements = new ArrayList<>();

    @Override
    public String inspect(String sql) {
      statements.add(sql);
      return sql;
    }
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackageClasses = TutorProfileRepository.class)
  static class Config {
    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @Bean
    SqlCapture sqlCapture() {
      return new SqlCapture();
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, SqlCapture capture) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(dataSource);
      factory.setPackagesToScan("vn.edufit.profile.infra.persistence.entity");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(Map.of(
          "hibernate.hbm2ddl.auto", "create-drop",
          "hibernate.generate_statistics", "true",
          "hibernate.session_factory.statement_inspector", capture
      ));
      return factory;
    }

    @Bean
    JpaTransactionManager transactionManager(EntityManagerFactory factory) {
      return new JpaTransactionManager(factory);
    }

    @Bean
    LearningCatalogLoader catalogLoader(EducationLevelRepository levels, SubjectRepository subjects,
        SubjectTrackRepository tracks, GoalTagRepository goalTags, GoalTagRuleRepository rules,
        TextbookSeriesRepository textbooks, ProvinceRepository provinces, DistrictRepository districts,
        SpecializedSchoolRepository schools) {
      return new LearningCatalogLoader(levels, subjects, tracks, goalTags, rules, textbooks, provinces, districts,
          schools);
    }

    @Bean
    ProfileFacade facade(TutorProfileRepository tutors, StudentProfileRepository students,
        TutorOfferingRepository offerings, TutorAvailabilitySlotRepository slots, LearningNeedRepository needs,
        LearningNeedSlotRepository needSlots, EducationLevelRepository levels, LearningCatalogLoader catalogLoader) {
      var needService = new LearningNeedService(needs, needSlots, students, catalogLoader, (parent, student) -> false);
      var assembler = new TutorProfileAssembler(offerings, slots, catalogLoader);
      return new ProfileFacadeImpl(tutors, students, offerings, needs, needSlots, levels, needService, catalogLoader,
          assembler, 200);
    }
  }
}

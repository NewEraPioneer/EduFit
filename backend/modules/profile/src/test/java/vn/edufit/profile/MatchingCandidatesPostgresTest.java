package vn.edufit.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManagerFactory;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import vn.edufit.profile.api.dto.TutorCandidateCriteria;
import vn.edufit.profile.application.service.ProfileFacadeImpl;
import vn.edufit.profile.infra.persistence.repository.*;

/** Exercises real Hibernate JPQL and bounded hydration on an isolated PostgreSQL database. */
@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(MatchingCandidatesPostgresTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MatchingCandidatesPostgresTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private ProfileFacade facade;
  @Autowired private DataSource dataSource;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private SqlCapture sqlCapture;
  private JdbcTemplate jdbc;

  @BeforeEach
  void resetFixtures() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("truncate tutor_subject, tutor_availability_slot, tutor_profile, subject, education_level cascade");
    jdbc.update("insert into subject (subject_id,name,is_active) values (1,'Math',true),(2,'Physics',true)");
    jdbc.update("insert into education_level (level_id,name,sort_order,is_active) values (80,'First',10,true),(3,'Second',30,true),(41,'Third',60,true)");
  }

  @ParameterizedTest
  @CsvSource({
      "ONLINE,Other,'online,both-local,both-remote'",
      "OFFLINE, hAnOi ,'both-local,offline-local'",
      "BOTH,Hanoi,'online,both-local,both-remote,offline-local'",
      "BOTH,,'online,both-local,both-remote'"
  })
  void filtersVerifiedSubjectModeAndArea(String mode, String area, String expectedNames) {
    tutor(1, "online", "ONLINE", "Other", "VERIFIED", 1);
    tutor(2, "both-local", "BOTH", " Hanoi ", "VERIFIED", 1);
    tutor(3, "both-remote", "BOTH", "Other", "VERIFIED", 1);
    tutor(4, "offline-local", "OFFLINE", "HANOI", "VERIFIED", 1);
    tutor(5, "offline-remote", "OFFLINE", "Other", "VERIFIED", 1);
    tutor(6, "unverified", "ONLINE", "Hanoi", "UNVERIFIED", 1);
    tutor(7, "wrong-subject", "ONLINE", "Hanoi", "VERIFIED", 2);

    var results = facade.findVerifiedCandidatesBySubject(new TutorCandidateCriteria(1, mode, area));

    assertEquals(Set.of(expectedNames.split(",")), results.stream()
        .map(profile -> profile.tutor().displayName()).collect(Collectors.toSet()));
  }

  @Test
  void appliesDatabaseCapWithoutDuplicateTutorsAndHydratesInThreeQueries() {
    for (int index = 1; index <= 205; index++) {
      UUID tutorId = tutor(index, "Tutor " + index, "ONLINE", "Hanoi", "VERIFIED", 1);
      jdbc.update("insert into tutor_subject (tutor_subject_id,tutor_id,subject_id,education_level_id) values (?,?,1,3)",
          UUID.randomUUID(), tutorId);
      jdbc.update("insert into tutor_availability_slot (slot_id,tutor_id,day_of_week,start_time,end_time) values (?,?,1,'18:00','20:00')",
          UUID.randomUUID(), tutorId);
    }
    clearQueryStatistics();
    var criteria = new TutorCandidateCriteria(1, "ONLINE", null);

    var results = facade.findVerifiedCandidatesBySubject(criteria);

    assertEquals(200, results.size());
    assertEquals(200, results.stream().map(profile -> profile.tutor().tutorId()).distinct().count());
    assertEquals(new UUID(0, 1), results.getFirst().tutor().tutorId());
    assertEquals(new UUID(0, 200), results.getLast().tutor().tutorId());
    assertTrue(results.stream().allMatch(profile -> profile.subjects().size() == 2
        && profile.availabilitySlots().size() == 1
        && profile.subjects().getFirst().subjectName().equals("Math")));
    assertEquals(3, statistics().getPrepareStatementCount());
    assertTrue(sqlCapture.statements.getFirst().toLowerCase().contains("fetch first"),
        "Candidate cap must be applied in SQL before association hydration.");
    var firstIds = results.stream().map(profile -> profile.tutor().tutorId()).toList();
    assertEquals(firstIds, facade.findVerifiedCandidatesBySubject(criteria).stream()
        .map(profile -> profile.tutor().tutorId()).toList());
  }

  @Test
  void softFactorsDoNotExcludeCandidatesAndEmptyResultsSkipHydration() {
    UUID id = tutor(1, "New tutor", "ONLINE", "Other", "VERIFIED", 1);
    jdbc.update("update tutor_profile set price_per_session=900000,rating_avg=null,review_count=0 where tutor_id=?", id);
    jdbc.update("update tutor_subject set education_level_id=41 where tutor_id=?", id);
    clearQueryStatistics();

    var results = facade.findVerifiedCandidatesBySubject(new TutorCandidateCriteria(1, "ONLINE", null));

    assertEquals(1, results.size());
    assertEquals(900000L, results.getFirst().tutor().pricePerSession());
    assertEquals(41, results.getFirst().subjects().getFirst().educationLevelId());
    assertTrue(results.getFirst().availabilitySlots().isEmpty());
    assertEquals(3, statistics().getPrepareStatementCount());
    clearQueryStatistics();
    assertEquals(List.of(), facade.findVerifiedCandidatesBySubject(new TutorCandidateCriteria(2, "ONLINE", null)));
    assertEquals(1, statistics().getPrepareStatementCount());
  }

  private UUID tutor(long index, String name, String mode, String area, String status, int subjectId) {
    UUID tutorId = new UUID(0, index);
    var createdAt = Timestamp.from(Instant.parse("2025-01-01T00:00:00Z"));
    jdbc.update("""
        insert into tutor_profile (tutor_id,user_id,display_name,teaching_mode,area,price_per_session,
            status,rating_avg,review_count,version,created_at,updated_at)
        values (?,?,?,?,?,200000,?,4.5,10,0,?,?)
        """, tutorId, UUID.randomUUID(), name, mode, area, status, createdAt, createdAt);
    jdbc.update("insert into tutor_subject (tutor_subject_id,tutor_id,subject_id,education_level_id) values (?,?,?,80)",
        UUID.randomUUID(), tutorId, subjectId);
    return tutorId;
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
    ProfileFacade facade(TutorProfileRepository tutors, StudentProfileRepository students,
        TutorSubjectRepository subjects, TutorAvailabilitySlotRepository slots,
        LearningGoalRepository goals, GoalAvailabilitySlotRepository goalSlots, EducationLevelRepository levels,
        vn.edufit.profile.infra.persistence.repository.SubjectRepository subjectCatalog) {
      return new ProfileFacadeImpl(tutors, students, subjects, slots, goals, goalSlots, levels, subjectCatalog, 200);
    }
  }
}

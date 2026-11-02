package vn.edufit.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.edufit.progress.domain.model.AttitudeChip;
import vn.edufit.progress.domain.model.HomeworkStatus;
import vn.edufit.progress.domain.model.IndependenceLevel;
import vn.edufit.progress.domain.model.LearningPlan;
import vn.edufit.progress.domain.model.Milestone;
import vn.edufit.progress.domain.model.MilestoneAssessment;
import vn.edufit.progress.domain.model.MilestoneCriterion;
import vn.edufit.progress.domain.model.PlanTemplate;
import vn.edufit.progress.domain.model.SelfRating;
import vn.edufit.progress.domain.model.SessionFeedback;
import vn.edufit.progress.domain.model.SessionMilestoneLink;
import vn.edufit.progress.domain.model.SessionSelfRating;
import vn.edufit.progress.domain.policy.FeedbackTimingPolicy.ReminderStage;
import vn.edufit.progress.domain.repository.FeedbackReminderDomainRepository;
import vn.edufit.progress.domain.repository.LearningPlanDomainRepository;
import vn.edufit.progress.domain.repository.PlanTemplateDomainRepository;
import vn.edufit.progress.domain.repository.SessionFeedbackDomainRepository;
import vn.edufit.progress.domain.repository.SessionMilestoneLinkDomainRepository;
import vn.edufit.progress.domain.repository.SessionSelfRatingDomainRepository;
import vn.edufit.progress.infra.persistence.adapter.FeedbackReminderRepositoryAdapter;
import vn.edufit.progress.infra.persistence.adapter.LearningPlanRepositoryAdapter;
import vn.edufit.progress.infra.persistence.adapter.PlanTemplateRepositoryAdapter;
import vn.edufit.progress.infra.persistence.adapter.SessionFeedbackRepositoryAdapter;
import vn.edufit.progress.infra.persistence.adapter.SessionMilestoneLinkRepositoryAdapter;
import vn.edufit.progress.infra.persistence.adapter.SessionSelfRatingRepositoryAdapter;
import vn.edufit.progress.infra.persistence.mapper.ProgressMapper;

/**
 * Kiểm chứng persistence thật của Progress trên PostgreSQL 17 (Flyway + Hibernate validate):
 * tiêu chí lồng trong cột mốc (merge/orphan), phản hồi buổi học (lời nhắn riêng, bổ sung muộn), điểm chấm ghi nối tiếp
 * kèm câu bằng chứng, liên kết ACTUAL, cảm nhận 1 chạm của học sinh và nhật ký nhắc phản hồi.
 */
@Testcontainers
@SpringJUnitConfig(ProgressPersistencePostgresTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProgressPersistencePostgresTest {

  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  @Autowired LearningPlanDomainRepository plans;
  @Autowired SessionFeedbackDomainRepository feedbacks;
  @Autowired SessionSelfRatingDomainRepository selfRatings;
  @Autowired FeedbackReminderDomainRepository reminders;
  @Autowired SessionMilestoneLinkDomainRepository links;
  @Autowired PlanTemplateDomainRepository templates;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate tx;

  private final Instant now = Instant.parse("2026-11-02T10:00:00Z");
  private final LocalDate today = LocalDate.parse("2026-11-02");
  private UUID userId;
  private UUID classId;
  private UUID sessionId;

  /** Dựng hàng cha tối thiểu (tài khoản, lớp, buổi học) mà bỏ qua chuỗi khóa ngoại của các module khác. */
  @BeforeEach
  void seedParents() {
    userId = UUID.randomUUID();
    classId = UUID.randomUUID();
    sessionId = UUID.randomUUID();
    jdbc.execute("SET session_replication_role = replica;"
        + "INSERT INTO account(user_id,email,password_hash,full_name,role) VALUES ('" + userId + "','" + userId
        + "@test.local','x','Tutor','TUTOR');"
        + "INSERT INTO class(class_id,connection_request_id,student_id,tutor_id,need_id,price_per_session) VALUES ('"
        + classId + "','" + UUID.randomUUID() + "','" + UUID.randomUUID() + "','" + UUID.randomUUID() + "','"
        + UUID.randomUUID() + "',200000);"
        + "INSERT INTO tutoring_session(session_id,class_id,tutor_id,student_id,start_at,end_at,mode,status,proposed_by,expires_at)"
        + " VALUES ('" + sessionId + "','" + classId + "','" + UUID.randomUUID() + "','" + UUID.randomUUID()
        + "',now() - interval '2 hour',now() - interval '1 hour','ONLINE','COMPLETED','" + userId + "',now());"
        + "SET session_replication_role = DEFAULT;");
  }

  /** Lộ trình tối thiểu hợp lệ (3 cột mốc, cột mốc cuối có hạn); cột mốc đầu mang tiêu chí truyền vào. */
  private LearningPlan savePlan(List<String> criteria) {
    Milestone first = Milestone.createNew(null, "Hệ phương trình", null, today.plusDays(14), 2, 0, criteria, today, now);
    Milestone second = Milestone.createNew(null, "Hàm số bậc hai", null, null, 1, 1, List.of("Vẽ đồ thị"), today, now);
    Milestone last = Milestone.createNew(null, "Đề thi thử", null, today.plusDays(42), 1, 2, List.of("Đạt 8 điểm"), today, now);
    return tx.execute(s -> plans.save(LearningPlan.createNew(classId, "Ôn thi vào 10", List.of(first, second, last), now)));
  }

  @Test
  @DisplayName("Tiêu chí lưu cùng cột mốc; tick, sửa, thêm, xóa tiêu chí được merge đúng (orphan removal)")
  void criteriaRoundTrip() {
    LearningPlan saved = savePlan(List.of("Phương pháp thế", "Phương pháp cộng"));
    Milestone m = saved.orderedActiveMilestones().getFirst();
    assertThat(m.getCriteria()).extracting(MilestoneCriterion::getContent).containsExactly("Phương pháp thế", "Phương pháp cộng");

    UUID keep = m.getCriteria().get(0).getCriterionId();
    tx.executeWithoutResult(s -> {
      LearningPlan plan = plans.findByClassId(classId).orElseThrow();
      Milestone pm = plan.orderedActiveMilestones().getFirst();
      pm.applyCriteriaChecks(List.of(keep), now);
      pm.updateDetails(pm.getTitle(), null, pm.getTargetDate(), 2, 0,
          List.of(new Milestone.CriterionDraft(keep, "Phương pháp thế"), new Milestone.CriterionDraft(null, "Bài toán lời văn")),
          today, now);
      plans.save(plan);
    });

    Milestone reloaded = tx.execute(s -> plans.findByClassId(classId).orElseThrow()).orderedActiveMilestones().getFirst();
    assertThat(reloaded.getCriteria()).extracting(MilestoneCriterion::getContent)
        .containsExactly("Phương pháp thế", "Bài toán lời văn");
    assertThat(reloaded.getCriteria()).extracting(MilestoneCriterion::isChecked).containsExactly(true, false);
    assertThat(reloaded.getCriteria().getFirst().getCheckedAt()).isEqualTo(now);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM milestone_criterion WHERE milestone_id = ?", Integer.class, m.getMilestoneId()))
        .as("Tiêu chí bị bỏ khỏi bản nháp phải bị xóa (orphan removal)")
        .isEqualTo(2);
  }

  @Test
  @DisplayName("Phản hồi buổi học lưu điểm chấm + liên kết ACTUAL; mỗi buổi chỉ một phản hồi")
  void feedbackRoundTrip() {
    UUID milestoneId = savePlan(List.of("Phương pháp thế")).orderedActiveMilestones().getFirst().getMilestoneId();
    SessionFeedback feedback = new SessionFeedback(UUID.randomUUID(), sessionId, classId, HomeworkStatus.ASSIGNED,
        List.of(AttitudeChip.FOCUSED, AttitudeChip.NEEDS_MORE_PRACTICE), "Con cần ngủ sớm hơn", userId, now, true,
        List.of(new MilestoneAssessment(UUID.randomUUID(), milestoneId, sessionId, IndependenceLevel.WITH_HINTS,
            7, 10, "Tự giải 7 bài phương pháp thế", userId, now)));
    tx.executeWithoutResult(s -> feedbacks.save(feedback));

    SessionFeedback loaded = tx.execute(s -> feedbacks.findBySessionIdIn(List.of(sessionId))).getFirst();
    assertThat(loaded.getHomeworkStatus()).isEqualTo(HomeworkStatus.ASSIGNED);
    assertThat(loaded.getAttitudeChips()).containsExactlyInAnyOrder(AttitudeChip.FOCUSED, AttitudeChip.NEEDS_MORE_PRACTICE);
    assertThat(loaded.getParentMessage()).isEqualTo("Con cần ngủ sớm hơn");
    assertThat(loaded.isSubmittedLate()).isTrue();
    assertThat(loaded.getAssessments()).singleElement().satisfies(a -> {
      assertThat(a.getLevel()).isEqualTo(IndependenceLevel.WITH_HINTS);
      assertThat(a.getCorrectCount()).isEqualTo(7);
      assertThat(a.getTotalCount()).isEqualTo(10);
      assertThat(a.getEvidenceNote()).isEqualTo("Tự giải 7 bài phương pháp thế");
    });
    assertThat(feedbacks.findSessionIdsWithFeedback(List.of(sessionId, UUID.randomUUID()))).containsExactly(sessionId);
    assertThat(feedbacks.findAssessmentsByMilestoneIdIn(List.of(milestoneId))).hasSize(1);
    assertThat(jdbc.queryForObject(
        "SELECT link_type FROM session_milestone_link WHERE session_id = ?", String.class, sessionId)).isEqualTo("ACTUAL");
    assertThat(feedbacks.existsBySessionId(sessionId)).isTrue();

    SessionFeedback duplicate = new SessionFeedback(UUID.randomUUID(), sessionId, classId, HomeworkStatus.NONE,
        List.of(), null, userId, now, false,
        List.of(new MilestoneAssessment(UUID.randomUUID(), milestoneId, sessionId, IndependenceLevel.NOT_YET,
            null, null, null, userId, now)));
    assertThatThrownBy(() -> tx.executeWithoutResult(s -> feedbacks.save(duplicate)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("Cảm nhận 1 chạm: lưu, chọn lại thì ghi đè cùng một dòng; giá trị ngoài 1–3 bị CSDL chặn")
  void selfRatingRoundTrip() {
    UUID studentId = jdbc.queryForObject("SELECT student_id FROM class WHERE class_id = ?", UUID.class, classId);
    jdbc.execute("SET session_replication_role = replica;"
        + "INSERT INTO student_profile(student_id,user_id) VALUES ('" + studentId + "','" + userId + "');"
        + "SET session_replication_role = DEFAULT;");
    SessionSelfRating first = tx.execute(s -> selfRatings.save(new SessionSelfRating(
        UUID.randomUUID(), sessionId, classId, studentId, SelfRating.STRUGGLING, userId, now, now)));

    tx.executeWithoutResult(s -> {
      SessionSelfRating existing = selfRatings.findBySessionId(sessionId).orElseThrow();
      existing.change(SelfRating.CONFIDENT, now.plusSeconds(60));
      selfRatings.save(existing);
    });

    assertThat(selfRatings.findBySessionIdIn(List.of(sessionId))).singleElement().satisfies(r -> {
      assertThat(r.getRatingId()).isEqualTo(first.getRatingId());
      assertThat(r.getRating()).isEqualTo(SelfRating.CONFIDENT);
      assertThat(r.getUpdatedAt()).isEqualTo(now.plusSeconds(60));
    });
    assertThatThrownBy(() -> jdbc.update("UPDATE session_self_rating SET rating = 4 WHERE session_id = ?", sessionId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("Nhật ký nhắc phản hồi: đọc lại theo buổi; mỗi mốc chỉ ghi được một lần")
  void reminderLogRoundTrip() {
    tx.executeWithoutResult(s -> reminders.recordSent(sessionId, ReminderStage.H12, now));

    assertThat(reminders.findSentStagesBySessionIdIn(List.of(sessionId)))
        .containsEntry(sessionId, java.util.EnumSet.of(ReminderStage.H12));
    assertThatThrownBy(() -> tx.executeWithoutResult(s -> reminders.recordSent(sessionId, ReminderStage.H12, now)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("Liên kết dự kiến: thay toàn bộ PLANNED, không đụng ACTUAL; đọc lại cả hai loại")
  void plannedLinksReplaceWithoutTouchingActual() {
    LearningPlan plan = savePlan(List.of("Phương pháp thế"));
    UUID first = plan.orderedActiveMilestones().get(0).getMilestoneId();
    UUID second = plan.orderedActiveMilestones().get(1).getMilestoneId();
    tx.executeWithoutResult(s -> feedbacks.save(new SessionFeedback(UUID.randomUUID(), sessionId, classId,
        HomeworkStatus.NONE, List.of(), null, userId, now, false,
        List.of(new MilestoneAssessment(UUID.randomUUID(), first, sessionId, IndependenceLevel.WITH_HINTS,
            null, null, null, userId, now)))));

    tx.executeWithoutResult(s -> links.replacePlanned(sessionId, List.of(first, second), userId, now));
    tx.executeWithoutResult(s -> links.replacePlanned(sessionId, List.of(second), userId, now));

    assertThat(links.findBySessionIdIn(List.of(sessionId))).containsExactlyInAnyOrder(
        new SessionMilestoneLink(sessionId, first, SessionMilestoneLink.Type.ACTUAL),
        new SessionMilestoneLink(sessionId, second, SessionMilestoneLink.Type.PLANNED));
    tx.executeWithoutResult(s -> links.replacePlanned(sessionId, List.of(), userId, now));
    assertThat(links.findBySessionIdIn(List.of(sessionId)))
        .containsExactly(new SessionMilestoneLink(sessionId, first, SessionMilestoneLink.Type.ACTUAL));
  }

  @Test
  @DisplayName("Mẫu lộ trình: lưu cột mốc + tiêu chí có thứ tự; sửa thay cả danh sách; xóa dọn bảng con")
  void planTemplateRoundTrip() {
    Integer subjectId = jdbc.queryForObject("SELECT min(subject_id) FROM subject", Integer.class);
    Integer levelId = jdbc.queryForObject("SELECT min(level_id) FROM education_level", Integer.class);
    List<PlanTemplate.TemplateMilestone> items = List.of(
        new PlanTemplate.TemplateMilestone("Hệ phương trình", null, 2, 4, List.of("Phương pháp thế", "Phương pháp cộng")),
        new PlanTemplate.TemplateMilestone("Hàm số bậc hai", null, 1, 3, List.of("Vẽ đồ thị")),
        new PlanTemplate.TemplateMilestone("Đề thi thử", "Tổng ôn", 1, 2, List.of("Đạt 8 điểm")));
    PlanTemplate saved = tx.execute(s -> templates.save(new PlanTemplate(UUID.randomUUID(), null, subjectId, levelId, null,
        "Toán 9 ôn thi vào 10", null, items, userId, 0, now, now)));

    PlanTemplate loaded = tx.execute(s -> templates.findById(saved.getTemplateId()).orElseThrow());
    assertThat(loaded.isSystem()).isTrue();
    assertThat(loaded.getMilestones()).extracting(PlanTemplate.TemplateMilestone::title)
        .containsExactly("Hệ phương trình", "Hàm số bậc hai", "Đề thi thử");
    assertThat(loaded.getMilestones().getFirst().criteria()).containsExactly("Phương pháp thế", "Phương pháp cộng");
    List<PlanTemplate> visible = tx.execute(s -> templates.findVisibleTo(null));
    assertThat(visible).extracting(PlanTemplate::getTemplateId).contains(saved.getTemplateId());

    tx.executeWithoutResult(s -> {
      PlanTemplate edit = templates.findById(saved.getTemplateId()).orElseThrow();
      edit.revise(subjectId, levelId, null, "Toán 9 (sửa)", null,
          List.of(items.get(2), items.get(1), items.get(0)), now.plusSeconds(60));
      templates.save(edit);
    });
    PlanTemplate revised = tx.execute(s -> templates.findById(saved.getTemplateId()).orElseThrow());
    assertThat(revised.getTitle()).isEqualTo("Toán 9 (sửa)");
    assertThat(revised.getVersion()).isEqualTo(1);
    assertThat(revised.getMilestones()).extracting(PlanTemplate.TemplateMilestone::title)
        .containsExactly("Đề thi thử", "Hàm số bậc hai", "Hệ phương trình");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM plan_template_milestone WHERE template_id = ?",
        Integer.class, saved.getTemplateId())).isEqualTo(3);

    tx.executeWithoutResult(s -> templates.delete(saved.getTemplateId()));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM plan_template_criterion", Integer.class)).isZero();
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories("vn.edufit.progress.infra.persistence.repository")
  @Import({ProgressMapper.class, LearningPlanRepositoryAdapter.class, SessionFeedbackRepositoryAdapter.class,
      SessionSelfRatingRepositoryAdapter.class, FeedbackReminderRepositoryAdapter.class,
      SessionMilestoneLinkRepositoryAdapter.class, PlanTemplateRepositoryAdapter.class})
  static class Config {
    @Bean DataSource dataSource() {
      var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
      Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
      return source;
    }

    @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan("vn.edufit.progress.infra.persistence.entity");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
      return factory;
    }

    @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }

    @Bean TransactionTemplate transactionTemplate(JpaTransactionManager manager) { return new TransactionTemplate(manager); }

    @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
  }
}

package vn.edufit.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Dữ liệu demo đầy đủ của profile dev (db/migration + db/seed, gồm V1.34): kiểm chứng đúng luật nghiệp vụ ngay sau khi nạp.
 */
@Testcontainers
@DisplayName("Dữ liệu demo: nhất quán về nghiệp vụ ngay sau khi nạp")
class DemoJourneySeedIT {

  private static final String SCHEMA = "demo_seed";
  private static final String STUDENT = "11111111-1111-1111-1111-000000000001";
  private static final String TUTOR_DEMO = "22222222-2222-2222-2222-000000000001";

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:17")
          .withDatabaseName("edufit_seed")
          .withUsername("edufit")
          .withPassword("edufit-local-only");

  @BeforeAll
  static void seedFreshSchema() {
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration", "classpath:db/seed")
        .schemas(SCHEMA)
        .load()
        .migrate();
  }

  private static long count(String sql) throws Exception {
    try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement s = c.createStatement()) {
      s.execute("SET search_path TO " + SCHEMA);
      try (ResultSet rs = s.executeQuery(sql)) {
        assertTrue(rs.next());
        return rs.getLong(1);
      }
    }
  }

  private static Set<String> column(String sql) throws Exception {
    Set<String> values = new HashSet<>();
    try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement s = c.createStatement()) {
      s.execute("SET search_path TO " + SCHEMA);
      try (ResultSet rs = s.executeQuery(sql)) {
        while (rs.next()) {
          values.add(rs.getString(1));
        }
      }
    }
    return values;
  }

  @Test
  @DisplayName("Tài khoản và gia sư đa dạng đúng như mô tả")
  void accountsAndTutorsAreDiverse() throws Exception {
    assertEquals(
        Set.of("student.demo@edufit.vn", "parent.demo@edufit.vn", "tutor.demo@edufit.vn", "admin.demo@edufit.vn"),
        column("SELECT email FROM account WHERE email LIKE '%.demo@edufit.vn'"));
    assertEquals(
        Set.of("VERIFIED", "PENDING_VERIFICATION", "DRAFT", "UNVERIFIED"), column("SELECT DISTINCT status FROM tutor_profile"));
    assertEquals(13, count("SELECT count(*) FROM tutor_profile"));
    assertEquals(0, count("SELECT count(*) FROM tutor_availability_slot WHERE tutor_id = '22222222-2222-2222-2222-000000000010'"),
        "tutor.kien cố ý không có khung giờ rảnh (kín lịch)");
    assertEquals(0, count("SELECT count(*) FROM tutor_profile t WHERE t.status = 'VERIFIED' AND NOT EXISTS ("
        + "SELECT 1 FROM verification_request v WHERE v.tutor_id = t.tutor_id AND v.status = 'APPROVED')"),
        "Mọi gia sư đã xác minh phải có hồ sơ APPROVED");
    assertEquals(Set.of("PENDING", "REJECTED", "APPROVED"), column("SELECT DISTINCT status FROM verification_request"));
    assertEquals(0, count("SELECT count(*) FROM tutor_profile t WHERE t.status = 'VERIFIED'"
        + " AND NOT EXISTS (SELECT 1 FROM tutor_offering o WHERE o.tutor_id = t.tutor_id)"), "Gia sư đã xác minh phải có gói dạy");
    assertEquals(0, count("SELECT count(*) FROM tutor_profile t WHERE t.review_count <> (SELECT count(*) FROM review r"
        + " WHERE r.tutor_id = t.tutor_id) AND EXISTS (SELECT 1 FROM review r WHERE r.tutor_id = t.tutor_id)"),
        "Điểm gia sư có đánh giá thật phải khớp bảng review");
  }

  @Test
  @DisplayName("Buổi học: đủ trạng thái, lịch tuần khớp khung giờ rảnh, không có gì quá hạn ngay sau khi nạp")
  void sessionsCoverAllStatesWithoutOverdue() throws Exception {
    assertEquals(
        Set.of("PROPOSED", "SCHEDULED", "COMPLETED", "ABSENT", "CANCELLED", "REJECTED", "WITHDRAWN", "EXPIRED"),
        column("SELECT DISTINCT status FROM tutoring_session"));
    assertEquals(3, count("SELECT count(*) FROM class"));
    assertEquals(2, count("SELECT count(*) FROM class WHERE status = 'ACTIVE'"));
    assertEquals(1, count("SELECT count(*) FROM tutoring_session WHERE status = 'CANCELLED' AND is_late_cancel"));
    assertEquals(0, count("SELECT count(*) FROM tutoring_session WHERE status = 'CANCELLED'"
        + " AND cancel_reason <> 'OTHER' AND is_late_cancel <> (start_at - cancelled_at < interval '12 hours')"));
    assertEquals(0, count("SELECT count(*) FROM tutoring_session WHERE status IN ('COMPLETED', 'ABSENT')"
        + " AND (outcome_recorded_at IS NULL OR outcome_recorded_at < end_at OR outcome_recorded_at > now())"));
    assertEquals(0, count("SELECT count(*) FROM tutoring_session WHERE status = 'SCHEDULED' AND end_at <= now()"),
        "Không có buổi đã qua giờ mà vẫn chờ ghi nhận kết quả");
    assertEquals(0, count("SELECT count(*) FROM tutoring_session WHERE status = 'PROPOSED' AND expires_at <= now()"));
    assertEquals(0, count("SELECT count(*) FROM tutoring_session WHERE responded_at > now() OR created_at > now()"));
    assertEquals(0, count("SELECT count(*) FROM tutoring_session s JOIN class c USING (class_id)"
        + " WHERE s.start_at < c.started_at OR (c.ended_at IS NOT NULL AND s.end_at > c.ended_at)"),
        "Mọi buổi học nằm trong thời gian tồn tại của lớp");
    // Lịch tuần lớp 1 (Thứ 3 / Thứ 6 19:00) khớp khung giờ rảnh của tutor.demo và nhu cầu học của student.demo
    assertEquals(0, count("SELECT count(*) FROM tutoring_session s WHERE s.class_id = '77777777-7777-7777-7777-000000000001'"
        + " AND s.repeat_note LIKE 'Lịch cố định%' AND s.status IN ('SCHEDULED', 'COMPLETED')"
        + " AND NOT EXISTS (SELECT 1 FROM tutor_availability_slot a WHERE a.tutor_id = s.tutor_id"
        + " AND a.day_of_week = extract(isodow FROM s.start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')"
        + " AND a.start_time <= (s.start_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time"
        + " AND a.end_time >= (s.end_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::time)"));
    assertEquals(Set.of("PENDING", "ACCEPTED", "REJECTED", "EXPIRED"),
        column("SELECT DISTINCT status FROM session_reschedule_proposal"));
    assertEquals(2, count("SELECT count(DISTINCT proposed_by) FROM session_reschedule_proposal WHERE status = 'PENDING'"),
        "Đề xuất đổi lịch đang chờ theo cả hai chiều");
    assertEquals(0, count("SELECT count(*) FROM session_reschedule_proposal WHERE status = 'PENDING' AND expires_at <= now()"));
    assertEquals(Set.of("PENDING", "ACCEPTED", "REJECTED", "CANCELLED", "EXPIRED"),
        column("SELECT DISTINCT status FROM connection_request"));
    assertEquals(0, count("SELECT count(*) FROM connection_request WHERE status = 'PENDING' AND expires_at <= now()"));
    assertEquals(1, count("SELECT count(*) FROM parent_student_link WHERE status = 'CONFIRMED'"));
    assertEquals(1, count("SELECT count(*) FROM link_invitation WHERE status = 'PENDING' AND expires_at > now()"));
  }

  @Test
  @DisplayName("Thanh toán: sổ cái = ví, không âm tại mọi thời điểm, học phí khớp trạng thái buổi học")
  void paymentsAreConsistent() throws Exception {
    assertEquals(Set.of("FUNDED", "AWAITING_CONFIRMATION", "DISPUTED", "ADMIN_REVIEW", "SETTLED", "REFUNDED"),
        column("SELECT DISTINCT status FROM payment_charge"));
    assertEquals(0, count("SELECT count(*) FROM payment_wallet w WHERE (w.available, w.held) <>"
        + " (SELECT sum(available_delta), sum(held_delta) FROM payment_entry e WHERE e.user_id = w.user_id)"));
    assertEquals(0, count("SELECT count(*) FROM (SELECT sum(available_delta) OVER (PARTITION BY user_id ORDER BY created_at, event_key)"
        + " AS available, sum(held_delta) OVER (PARTITION BY user_id ORDER BY created_at, event_key) AS held"
        + " FROM payment_entry WHERE user_id IS NOT NULL) x WHERE available < 0 OR held < 0"),
        "Ví không được âm tại bất kỳ thời điểm nào trong sổ cái");
    assertEquals(0, count("SELECT count(*) FROM payment_charge p JOIN tutoring_session s USING (session_id) WHERE p.end_at <> s.end_at"
        + " OR (s.status IN ('CANCELLED', 'ABSENT') AND p.status <> 'REFUNDED')"
        + " OR (s.status = 'SCHEDULED' AND p.status <> 'FUNDED')"
        + " OR (s.status = 'COMPLETED' AND p.status IN ('FUNDED', 'REFUNDED'))"));
    // Tiền đang giữ = tổng học phí các khoản chưa kết thúc (trừ tiền giữ cho yêu cầu rút)
    assertEquals(0, count("SELECT count(*) FROM payment_wallet w WHERE w.held <> (SELECT coalesce(sum(amount), 0) FROM payment_charge c"
        + " WHERE c.payer_user_id = w.user_id AND c.status IN ('FUNDED', 'AWAITING_CONFIRMATION', 'DISPUTED', 'AWAITING_ADMIN', 'ADMIN_REVIEW'))"
        + " + (SELECT coalesce(sum(amount), 0) FROM payment_withdrawal x WHERE x.user_id = w.user_id AND x.status IN ('PENDING', 'PROCESSING'))"));
    // Tất toán: tiền gia sư + phí nền tảng = học phí đã tính
    assertEquals(0, count("SELECT count(*) FROM payment_charge c WHERE c.status = 'SETTLED' AND (SELECT coalesce(sum(available_delta), 0)"
        + " FROM payment_entry e WHERE e.reference_id = c.session_id AND e.kind IN ('TUITION_INCOME', 'COMMISSION')) <> c.settled_amount"));
    // Chưa có khoản nào quá hạn xác nhận hay thương lượng khiếu nại
    assertEquals(0, count("SELECT count(*) FROM payment_charge WHERE status = 'AWAITING_CONFIRMATION' AND review_started_at + interval '24 hours' <= now()"));
    assertEquals(0, count("SELECT count(*) FROM payment_charge WHERE status = 'DISPUTED' AND dispute_opened_at + interval '48 hours' <= now()"));
    assertEquals(1, count("SELECT count(*) FROM payment_charge WHERE status = 'ADMIN_REVIEW' AND dispute_opened_at + interval '48 hours' <= now()"),
        "Đúng một khiếu nại đã chuyển admin phân xử");
    assertEquals(Set.of("PENDING", "PAID", "REJECTED"), column("SELECT DISTINCT status FROM payment_withdrawal"));
  }

  @Test
  @DisplayName("Tiến độ: tiêu chí, mức đánh giá, cột mốc và báo cáo kỳ đúng luật")
  void progressIsConsistent() throws Exception {
    assertEquals(2, count("SELECT count(*) FROM learning_plan"));
    assertEquals(0, count("SELECT count(*) FROM milestone m WHERE m.status = 'ACHIEVED' AND (m.achieved_date IS NULL OR EXISTS ("
        + "SELECT 1 FROM milestone_criterion c WHERE c.milestone_id = m.milestone_id AND NOT c.is_checked))"),
        "Cột mốc đã đạt phải tick đủ tiêu chí");
    assertEquals(0, count("SELECT count(*) FROM milestone m WHERE m.status = 'NOT_STARTED' AND EXISTS ("
        + "SELECT 1 FROM milestone_assessment a WHERE a.milestone_id = m.milestone_id)"));
    assertEquals(0, count("SELECT count(*) FROM milestone_assessment a JOIN milestone m USING (milestone_id)"
        + " WHERE a.level = 4 AND EXISTS (SELECT 1 FROM milestone_criterion c WHERE c.milestone_id = m.milestone_id AND NOT c.is_checked)"));
    assertEquals(0, count("SELECT count(*) FROM milestone_criterion c JOIN milestone m USING (milestone_id)"
        + " WHERE c.is_checked AND c.checked_at < m.created_at"));
    assertEquals(0, count("SELECT count(*) FROM session_feedback f JOIN tutoring_session s USING (session_id) WHERE s.status <> 'COMPLETED'"),
        "Chỉ buổi đã hoàn thành mới có phản hồi");
    assertEquals(0, count("SELECT count(*) FROM milestone_assessment a JOIN tutoring_session s USING (session_id) WHERE s.status <> 'COMPLETED'"));
    assertEquals(0, count("SELECT count(*) FROM milestone_assessment a JOIN milestone m USING (milestone_id)"
        + " JOIN learning_plan p USING (plan_id) JOIN tutoring_session s ON s.session_id = a.session_id WHERE s.class_id <> p.class_id"),
        "Đánh giá cột mốc phải thuộc lộ trình của đúng lớp");
    assertEquals(3, count("SELECT count(*) FROM tutoring_session s JOIN class c USING (class_id) WHERE s.status = 'COMPLETED'"
        + " AND c.status = 'ACTIVE' AND NOT EXISTS (SELECT 1 FROM session_feedback f WHERE f.session_id = s.session_id)"),
        "Ba buổi gần nhất chờ gia sư viết phản hồi (việc cần làm của tutor.demo và tutor.linh)");
    assertTrue(count("SELECT count(*) FROM period_report WHERE status = 'SENT' AND sent_at <= now()") >= 3);
    assertEquals(0, count("SELECT count(*) FROM period_report r JOIN class c USING (class_id)"
        + " WHERE r.period_end >= (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date"
        + " OR r.period_start < (c.started_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date"), "Báo cáo chỉ cho các kỳ đã trọn vẹn");
    assertEquals(0, count("SELECT count(*) FROM period_report WHERE counted_count < attended_count"));
  }

  @Test
  @DisplayName("Học sinh demo đúng hồ sơ: lớp, nhu cầu, liên kết phụ huynh")
  void demoStudentJourneyIsWired() throws Exception {
    assertEquals(3, count("SELECT count(*) FROM class WHERE student_id = '" + STUDENT + "'"));
    assertEquals(1, count("SELECT count(*) FROM class WHERE tutor_id = '" + TUTOR_DEMO + "' AND status = 'ACTIVE'"));
    assertEquals(Set.of("ACTIVE", "ARCHIVED"), column("SELECT DISTINCT status FROM learning_need WHERE student_id = '" + STUDENT + "'"));
    assertEquals(0, count("SELECT count(*) FROM class c JOIN connection_request r ON r.request_id = c.connection_request_id"
        + " WHERE r.status <> 'ACCEPTED' OR r.student_id <> c.student_id OR r.tutor_id <> c.tutor_id OR r.need_id <> c.need_id"));
    assertEquals(0, count("SELECT count(*) FROM class c WHERE c.price_per_session <> (SELECT o.price_per_session FROM tutor_offering o"
        + " JOIN learning_need n ON n.need_id = c.need_id WHERE o.tutor_id = c.tutor_id AND o.subject_id = n.subject_id"
        + " AND o.track_id IS NOT DISTINCT FROM n.track_id)"), "Đơn giá lớp khớp gói dạy của gia sư");
  }
}

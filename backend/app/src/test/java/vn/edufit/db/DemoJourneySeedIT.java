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
  /** Dữ liệu số lượng lớn V1.39: gia sư tutor001..100, học sinh student001..080, phụ huynh parent001..040. */
  private static final int BULK_TUTORS = 100;
  private static final int BULK_STUDENTS = 80;
  private static final int BULK_PARENTS = 40;
  private static final String BULK_TUTOR = "t.tutor_id::text LIKE '22222222-2222-2222-2222-2%'";

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
    assertEquals(13 + BULK_TUTORS, count("SELECT count(*) FROM tutor_profile"));
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
    assertEquals(1, count("SELECT count(*) FROM parent_student_link WHERE status = 'CONFIRMED'"
        + " AND parent_user_id = '33333333-3333-3333-3333-333333333333'"));
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

  @Test
  @DisplayName("Dữ liệu số lượng lớn: tài khoản đúng ID, gia sư đa vùng, ưu tiên Ngũ Hành Sơn")
  void bulkAccountsAndTutorsAreDiverse() throws Exception {
    assertEquals(BULK_TUTORS, count("SELECT count(*) FROM account WHERE role = 'TUTOR' AND email ~ '^tutor[0-9]{3}@edufit\\.vn$'"
        + " AND user_id::text = '22222222-2222-2222-2222-1' || lpad(substring(email from 6 for 3), 11, '0')"),
        "Email tutorNNN khớp số thứ tự trong user_id");
    assertEquals(BULK_STUDENTS, count("SELECT count(*) FROM account a JOIN student_profile p USING (user_id)"
        + " WHERE a.role = 'STUDENT' AND a.email ~ '^student[0-9]{3}@edufit\\.vn$'"
        + " AND p.student_id::text = '11111111-1111-1111-1111-2' || lpad(substring(a.email from 8 for 3), 11, '0')"));
    assertEquals(BULK_PARENTS, count("SELECT count(*) FROM account WHERE role = 'PARENT' AND email ~ '^parent[0-9]{3}@edufit\\.vn$'"));
    assertEquals(BULK_TUTORS, count("SELECT count(*) FROM tutor_profile t JOIN account a USING (user_id) WHERE " + BULK_TUTOR
        + " AND t.tutor_id::text = '22222222-2222-2222-2222-2' || lpad(substring(a.email from 6 for 3), 11, '0')"));

    assertTrue(count("SELECT count(*) FROM tutor_profile t JOIN province p USING (province_id) WHERE " + BULK_TUTOR
        + " AND p.code = 'DA_NANG'") >= 50, "Ưu tiên Đà Nẵng");
    assertTrue(count("SELECT count(DISTINCT t.tutor_id) FROM tutor_profile t JOIN tutor_district td USING (tutor_id)"
        + " JOIN district d USING (district_id) WHERE " + BULK_TUTOR + " AND t.status = 'VERIFIED'"
        + " AND t.teaching_mode <> 'ONLINE' AND d.name = 'Ngũ Hành Sơn'") >= 25, "Nhiều gia sư dạy trực tiếp Ngũ Hành Sơn");
    assertTrue(count("SELECT count(DISTINCT province_id) FROM tutor_profile t WHERE " + BULK_TUTOR) >= 9, "Nhiều tỉnh/thành");
    assertEquals(Set.of("ONLINE", "OFFLINE", "BOTH"), column("SELECT DISTINCT teaching_mode FROM tutor_profile t WHERE " + BULK_TUTOR));
    assertEquals(Set.of("UNIVERSITY_STUDENT", "GRADUATE", "IN_SERVICE_TEACHER", "RETIRED_TEACHER"),
        column("SELECT DISTINCT tutor_type FROM tutor_profile t WHERE " + BULK_TUTOR));
    assertEquals(Set.of("Toán", "Ngữ Văn", "Tiếng Anh", "Khoa học tự nhiên"), column("SELECT DISTINCT s.name FROM tutor_offering o"
        + " JOIN subject s USING (subject_id) JOIN tutor_profile t USING (tutor_id) WHERE " + BULK_TUTOR));
    assertEquals(7, count("SELECT count(DISTINCT day_of_week) FROM tutor_availability_slot a JOIN tutor_profile t USING (tutor_id)"
        + " WHERE " + BULK_TUTOR), "Khung giờ rải đủ 7 ngày trong tuần");
    assertTrue(count("SELECT count(DISTINCT start_time) FROM tutor_availability_slot a JOIN tutor_profile t USING (tutor_id)"
        + " WHERE " + BULK_TUTOR) >= 8, "Giờ bắt đầu đa dạng (sáng, chiều, tối)");
  }

  @Test
  @DisplayName("Dữ liệu số lượng lớn: gói dạy đúng luật danh mục, hồ sơ đã xác minh đủ điều kiện hiển thị")
  void bulkOfferingsFollowCatalogRules() throws Exception {
    assertEquals(0, count("SELECT count(*) FROM tutor_offering_goal og JOIN tutor_offering o USING (offering_id)"
        + " JOIN tutor_profile t USING (tutor_id) WHERE " + BULK_TUTOR + " AND NOT EXISTS (SELECT 1 FROM goal_tag_rule r"
        + " JOIN tutor_offering_level l ON l.level_id = r.level_id AND l.offering_id = o.offering_id"
        + " WHERE r.goal_tag_id = og.goal_tag_id AND r.subject_id = o.subject_id AND r.track_id IS NOT DISTINCT FROM o.track_id)"),
        "Mục tiêu phải hợp lệ với môn, nhánh và ít nhất một khối lớp của gói");
    assertEquals(0, count("SELECT count(*) FROM tutor_offering_level l JOIN tutor_offering o USING (offering_id)"
        + " JOIN tutor_profile t USING (tutor_id) WHERE " + BULK_TUTOR + " AND o.track_id IS NOT NULL"
        + " AND NOT EXISTS (SELECT 1 FROM subject_track_level s WHERE s.track_id = o.track_id AND s.level_id = l.level_id)"),
        "Khối lớp phải thuộc nhánh môn");
    assertEquals(0, count("SELECT count(*) FROM tutor_offering_textbook b JOIN tutor_offering o USING (offering_id)"
        + " JOIN tutor_profile t USING (tutor_id) WHERE " + BULK_TUTOR + " AND NOT EXISTS (SELECT 1 FROM textbook_series_subject s"
        + " WHERE s.series_id = b.series_id AND s.subject_id = o.subject_id)"), "Bộ sách đúng môn");
    assertEquals(0, count("SELECT count(*) FROM tutor_offering o JOIN tutor_profile t USING (tutor_id) WHERE " + BULK_TUTOR
        + " AND EXISTS (SELECT 1 FROM tutor_offering_goal og JOIN goal_tag g USING (goal_tag_id) WHERE og.offering_id = o.offering_id"
        + " AND g.requires_textbook) AND NOT EXISTS (SELECT 1 FROM tutor_offering_textbook b WHERE b.offering_id = o.offering_id)"),
        "Mục tiêu cần bộ sách thì gói phải khai báo bộ sách");
    assertEquals(0, count("SELECT count(*) FROM tutor_offering o JOIN tutor_profile t USING (tutor_id) WHERE " + BULK_TUTOR
        + " AND EXISTS (SELECT 1 FROM tutor_offering_goal og JOIN goal_tag g USING (goal_tag_id) WHERE og.offering_id = o.offering_id"
        + " AND g.requires_exam_province) AND NOT EXISTS (SELECT 1 FROM tutor_offering_exam_province e WHERE e.offering_id = o.offering_id)"),
        "Luyện thi vào 10 phải có tỉnh luyện đề");
    assertEquals(0, count("SELECT count(*) FROM tutor_offering_goal og JOIN tutor_offering o USING (offering_id)"
        + " JOIN tutor_profile t USING (tutor_id) WHERE " + BULK_TUTOR + " AND og.evidence_verified AND NOT EXISTS ("
        + "SELECT 1 FROM credential c WHERE c.offering_id = o.offering_id)"), "Mục tiêu premium đã xác minh phải có bằng chứng");
    assertEquals(0, count("SELECT count(*) FROM tutor_profile t WHERE " + BULK_TUTOR + " AND t.teaching_mode <> 'ONLINE'"
        + " AND t.province_id IS NULL"), "Dạy trực tiếp phải có tỉnh");
    assertEquals(0, count("SELECT count(*) FROM tutor_district td JOIN district d USING (district_id)"
        + " JOIN tutor_profile t USING (tutor_id) WHERE " + BULK_TUTOR + " AND d.province_id <> t.province_id"), "Khu vực thuộc tỉnh của gia sư");
    assertEquals(0, count("SELECT count(*) FROM tutor_availability_slot a JOIN tutor_availability_slot b"
        + " ON a.tutor_id = b.tutor_id AND a.day_of_week = b.day_of_week AND a.slot_id < b.slot_id"
        + " AND a.start_time < b.end_time AND b.start_time < a.end_time"), "Khung giờ rảnh không chồng nhau");
    assertEquals(Set.of("VERIFIED", "PENDING_VERIFICATION", "UNVERIFIED"),
        column("SELECT DISTINCT status FROM tutor_profile t WHERE " + BULK_TUTOR));
  }

  @Test
  @DisplayName("Dữ liệu số lượng lớn: đánh giá dàn trải cao/thấp, nhiều/ít, người viết hợp lệ, điểm khớp bảng review")
  void bulkReviewsAreSpreadAndConsistent() throws Exception {
    String stats = "SELECT t.tutor_id, t.status, count(r.review_id) AS n, avg(r.rating) AS a FROM tutor_profile t"
        + " LEFT JOIN review r USING (tutor_id) WHERE " + BULK_TUTOR + " GROUP BY t.tutor_id, t.status";
    assertTrue(count("SELECT count(*) FROM (" + stats + ") x WHERE n >= 25 AND a >= 4.6") >= 5, "Gia sư nổi bật: nhiều, điểm cao");
    assertTrue(count("SELECT count(*) FROM (" + stats + ") x WHERE n >= 3 AND a < 3") >= 4, "Gia sư điểm thấp");
    assertTrue(count("SELECT count(*) FROM (" + stats + ") x WHERE n BETWEEN 1 AND 3") >= 10, "Gia sư mới, ít đánh giá");
    assertTrue(count("SELECT count(*) FROM (" + stats + ") x WHERE n = 0 AND status = 'VERIFIED'") >= 5, "Gia sư chưa có đánh giá");
    assertEquals(Set.of("1", "2", "3", "4", "5"), column("SELECT DISTINCT r.rating::text FROM review r JOIN tutor_profile t USING (tutor_id)"
        + " WHERE " + BULK_TUTOR));
    assertEquals(Set.of("STUDENT", "PARENT"), column("SELECT DISTINCT reviewer_type FROM review r JOIN tutor_profile t USING (tutor_id)"
        + " WHERE " + BULK_TUTOR));
    assertTrue(count("SELECT count(*) FROM (" + stats + ") x JOIN tutor_district td USING (tutor_id) JOIN district d USING (district_id)"
        + " WHERE d.name = 'Ngũ Hành Sơn' AND x.n >= 25") >= 2, "Ngũ Hành Sơn có gia sư nổi bật để demo");
    assertEquals(0, count("SELECT count(*) FROM review r JOIN tutor_profile t USING (tutor_id) WHERE " + BULK_TUTOR
        + " AND t.status <> 'VERIFIED'"), "Chỉ gia sư đã xác minh mới có đánh giá");
    assertEquals(0, count("SELECT count(*) FROM review r WHERE r.reviewer_type = 'STUDENT' AND NOT EXISTS ("
        + "SELECT 1 FROM student_profile s WHERE s.student_id = r.student_id AND s.user_id = r.reviewer_user_id)"),
        "Học sinh chỉ đánh giá thay chính mình");
    assertEquals(0, count("SELECT count(*) FROM review r WHERE r.reviewer_type = 'PARENT' AND NOT EXISTS ("
        + "SELECT 1 FROM parent_student_link l WHERE l.student_id = r.student_id AND l.parent_user_id = r.reviewer_user_id"
        + " AND l.status = 'CONFIRMED' AND l.confirmed_at < r.created_at)"), "Phụ huynh chỉ đánh giá cho con đã liên kết");
    assertEquals(0, count("SELECT count(*) FROM review r JOIN tutor_profile t USING (tutor_id) JOIN account a ON a.user_id = r.reviewer_user_id"
        + " WHERE r.created_at <= t.verified_at OR r.created_at <= a.created_at OR r.created_at > now() OR r.updated_at < r.created_at"),
        "Mốc thời gian đánh giá hợp lệ");
    assertEquals(0, count("SELECT count(*) FROM tutor_profile t WHERE " + BULK_TUTOR + " AND (t.review_count,"
        + " coalesce(t.rating_avg, 0)) <> (SELECT count(*), coalesce(round(avg(r.rating)::numeric, 2), 0) FROM review r"
        + " WHERE r.tutor_id = t.tutor_id)"), "rating_avg và review_count khớp bảng review");
    assertTrue(count("SELECT count(*) FROM parent_student_link WHERE parent_user_id::text LIKE '33333333-3333-3333-3333-1%'") >= BULK_PARENTS);
  }
}

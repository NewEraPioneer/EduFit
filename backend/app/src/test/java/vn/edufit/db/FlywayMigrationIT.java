package vn.edufit.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Kiểm thử tích hợp (Integration Test) xác minh toàn bộ kịch bản migration của Flyway trên CSDL
 * thật.
 *
 * <p>Mục đích thiết kế và tuân thủ quyết định kiến trúc:
 *
 * <ul>
 *   <li><b>ADR-002 Decision 3b (Mục 6):</b> <i>"CI bắt buộc chạy flyway migrate trên Testcontainers
 *       Postgres sạch mỗi PR - bắt lỗi thứ tự version sớm, trước khi merge."</i>
 *   <li><b>Docker bắt buộc:</b> Test phải fail khi thiếu Docker, không bỏ qua bằng skip. Máy phát
 *       triển local và CI cần có Docker để kiểm chứng migration PostgreSQL thật.
 *   <li><b>Kiểm tra thực tế:</b> Xác nhận các câu lệnh SQL trong {@code db/migration/**} có cú pháp
 *       hợp lệ với engine PostgreSQL 17; schema nền được mô tả trong {@code
 *       docs/edufit-schema.dbml}.
 * </ul>
 */
@Testcontainers
@DisplayName("Kiểm thử tích hợp Flyway Migration trên PostgreSQL thật (Testcontainers)")
class FlywayMigrationIT {

  /** Chuỗi migration triển khai thật: chỉ schema + danh mục tham chiếu, không có tài khoản demo. */
  private static final String[] PROD_LOCATIONS = {"classpath:db/migration"};

  /** Chuỗi migration của profile dev: thêm seed demo (V1.09, V1.10, V1.13) từ db/seed. */
  private static final String[] DEV_LOCATIONS = {"classpath:db/migration", "classpath:db/seed"};

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:17")
          .withDatabaseName("edufit_test")
          .withUsername("edufit")
          .withPassword("edufit-local-only");

  @Test
  @DisplayName(
      "Nâng cấp DB main đã seed V1.09: giữ history/dữ liệu, chạy V1.10 đến V1.14 và rerun no-op")
  void shouldUpgradeMainSeedHistoryWithoutRepair() throws Exception {
    String schema = "main_seed_upgrade";
    Flyway previous =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations(
                "classpath:db/migration/iam",
                "classpath:db/migration/profile",
                "classpath:db/migration/verification",
                "classpath:db/migration/scheduling",
                "classpath:db/migration/progress",
                "classpath:db/migration/review",
                "classpath:db/legacy-main")
            .schemas(schema)
            .target(MigrationVersion.fromVersion("1.09"))
            .load();
    assertEquals(9, previous.migrate().migrationsExecuted);
    try (Connection connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute("SET search_path TO " + schema);
      statement.executeUpdate(
          "UPDATE account SET full_name = 'User edited name' WHERE user_id ="
              + " '11111111-1111-1111-1111-111111111111'");
      statement.executeUpdate(
          "INSERT INTO parent_student_link(link_id,parent_user_id,student_id,confirmed_at) VALUES"
              + " ('99999999-1111-1111-1111-111111111111','33333333-3333-3333-3333-333333333333','11111111-1111-1111-1111-000000000001',now())");
    }
    Flyway upgraded =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations(DEV_LOCATIONS)
            .schemas(schema)
            .outOfOrder(false)
            .validateOnMigrate(true)
            .baselineOnMigrate(false)
            .load();
    assertEquals(18, upgraded.migrate().migrationsExecuted);
    upgraded.validate();
    assertEquals(0, upgraded.migrate().migrationsExecuted, "Rerun không thực thi lại migration");
    try (Connection connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute("SET search_path TO " + schema);
      try (ResultSet rs =
          statement.executeQuery(
              "SELECT checksum FROM flyway_schema_history WHERE version = '1.09'")) {
        assertTrue(rs.next());
        assertEquals(-1594466614, rs.getInt(1));
      }
      try (ResultSet rs =
          statement.executeQuery(
              "SELECT full_name FROM account WHERE user_id ="
                  + " '11111111-1111-1111-1111-111111111111'")) {
        assertTrue(rs.next());
        assertEquals("User edited name", rs.getString(1));
      }
      try (ResultSet rs = statement.executeQuery("SELECT count(*) FROM account")) {
        assertTrue(rs.next());
        assertEquals(9, rs.getInt(1));
      }
      try (ResultSet rs =
          statement.executeQuery(
              "SELECT status,version FROM parent_student_link WHERE link_id ="
                  + " '99999999-1111-1111-1111-111111111111'")) {
        assertTrue(rs.next());
        assertEquals("CONFIRMED", rs.getString(1));
        assertEquals(0, rs.getLong(2));
      }
      statement
          .executeQuery("SELECT user_id,window_started_at,attempts FROM connection_invite_quota")
          .close();
    }
  }

  @Test
  @DisplayName("CSDL triển khai thật (không profile dev) có danh mục tham chiếu nhưng không có tài khoản demo")
  void productionLocationsMustNotSeedDemoAccounts() throws Exception {
    String schema = "production_clean";
    Flyway flyway =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations(PROD_LOCATIONS)
            .schemas(schema)
            .outOfOrder(false)
            .validateOnMigrate(true)
            .load();

    assertEquals(23, flyway.migrate().migrationsExecuted, "22 migration cũ - 3 seed demo + V1.23 danh mục + V1.24–V1.26 nhu cầu học");

    try (Connection connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute("SET search_path TO " + schema);
      try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM account")) {
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1), "Production không được có tài khoản demo dùng mật khẩu công khai");
      }
      try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM tutor_profile")) {
        assertTrue(rs.next());
        assertEquals(0, rs.getInt(1), "Production không được có hồ sơ gia sư với rating giả");
      }
      try (ResultSet rs = statement.executeQuery(
          "SELECT (SELECT COUNT(*) FROM education_level), (SELECT COUNT(*) FROM subject)")) {
        assertTrue(rs.next());
        assertEquals(4, rs.getInt(1));
        assertEquals(7, rs.getInt(2), "6 môn cũ + KHTN (V1.24); Lý/Hóa/Sinh giữ lại ở trạng thái ngừng dùng");
      }
      // Identity sequence phải vượt các ID chèn tường minh để Admin thêm danh mục mới không trùng khóa
      statement.executeUpdate("INSERT INTO subject (name) VALUES ('Lịch Sử')");
      try (ResultSet rs = statement.executeQuery("SELECT subject_id FROM subject WHERE name = 'Lịch Sử'")) {
        assertTrue(rs.next());
        assertEquals(8, rs.getInt(1), "KHTN (V1.24) đã nhận id 7 từ sequence");
      }
    }
  }

  @Test
  @DisplayName(
      "Thực thi Flyway migrate trên CSDL sạch và xác minh bảng account, email_token và các bảng"
          + " SRS")
  void shouldMigrateCleanDatabaseSuccessfully() throws Exception {
    assertTrue(postgres.isRunning(), "Container PostgreSQL phải đang chạy để thực hiện kiểm thử");

    // 1. Cấu hình Flyway trỏ trực tiếp vào Testcontainers PostgreSQL
    Flyway flyway =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations(DEV_LOCATIONS)
            .outOfOrder(false)
            .validateOnMigrate(true)
            .baselineOnMigrate(false)
            .load();

    // 2. Kích hoạt toàn bộ các file migration từ đầu đến cuối
    MigrateResult result = flyway.migrate();

    // 3. Kiểm chứng kết quả thực thi của Flyway
    assertTrue(result.success, "Quá trình migrate của Flyway phải thành công 100%");
    assertEquals(
        27, result.migrationsExecuted, "Phải thực thi đúng 27 file migration V1.01 -> V1.27 (kể cả seed dev)");

    // 4. Kiểm tra trực tiếp các bảng và ràng buộc đã được tạo trong PostgreSQL
    try (Connection connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement statement = connection.createStatement()) {

      // Kiểm tra bảng account tồn tại và có thể truy vấn
      try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM account")) {
        assertTrue(rs.next(), "Bảng account phải tồn tại và cho phép truy vấn");
        assertEquals(9, rs.getInt(1), "Bảng account có 9 bản ghi demo sau V1.13");
      }

      // Kiểm tra bảng email_token tồn tại và có thể truy vấn
      try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM email_token")) {
        assertTrue(rs.next(), "Bảng email_token phải tồn tại và cho phép truy vấn");
        assertEquals(0, rs.getInt(1), "Bảng email_token mới khởi tạo phải có 0 bản ghi");
      }

      // Kiểm tra bảng class tồn tại
      try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM class")) {
        assertTrue(rs.next(), "Bảng class phải tồn tại và cho phép truy vấn");
      }

      // Kiểm tra bảng tutoring_session tồn tại
      try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM tutoring_session")) {
        assertTrue(rs.next(), "Bảng tutoring_session phải tồn tại và cho phép truy vấn");
      }

      // Kiểm tra bảng flyway_schema_history ghi nhận toàn bộ version ở trạng thái thành công
      try (ResultSet rs =
          statement.executeQuery(
              "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true")) {
        assertTrue(rs.next());
        assertEquals(
            27, rs.getInt(1), "Lịch sử migration phải ghi nhận đúng 27 bản ghi thành công");
      }
    }
  }

  @Test
  void paymentV121UpgradeKeepsWalletAndAppliesConnectionV122() throws Exception {
    String schema = "payment_connection_upgrade";
    Flyway existing =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations(DEV_LOCATIONS)
            .schemas(schema)
            .target("1.21")
            .load();
    assertEquals(21, existing.migrate().migrationsExecuted);

    try (Connection connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute("SET search_path TO " + schema);
      statement.executeUpdate(
          "INSERT INTO payment_wallet(user_id, available, held)"
              + " SELECT user_id, 45000, 5000 FROM account WHERE role='TUTOR' LIMIT 1");
    }

    Flyway upgraded =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations(DEV_LOCATIONS)
            .schemas(schema)
            .outOfOrder(false)
            .validateOnMigrate(true)
            .load();
    assertEquals(6, upgraded.migrate().migrationsExecuted);
    upgraded.validate();
    assertEquals(0, upgraded.migrate().migrationsExecuted);

    try (Connection connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute("SET search_path TO " + schema);
      try (ResultSet rows =
          statement.executeQuery("SELECT available, held FROM payment_wallet")) {
        assertTrue(rows.next());
        assertEquals(45000L, rows.getLong(1));
        assertEquals(5000L, rows.getLong(2));
      }
      try (ResultSet rows =
          statement.executeQuery(
              "SELECT version, description FROM flyway_schema_history"
                  + " WHERE version IN ('1.16', '1.22') ORDER BY version")) {
        assertTrue(rows.next());
        assertEquals("1.16", rows.getString(1));
        assertEquals("create payment wallet and settlement", rows.getString(2));
        assertTrue(rows.next());
        assertEquals("1.22", rows.getString(1));
        assertEquals("allow multi subject connections", rows.getString(2));
      }
      try (ResultSet rows =
          statement.executeQuery(
              "SELECT indexdef FROM pg_indexes WHERE schemaname = current_schema()"
                  + " AND indexname IN ('uq_connection_request_pending',"
                  + " 'uq_class_student_tutor_active')")) {
        assertTrue(rows.next());
        assertTrue(rows.getString(1).contains("need_id"), "V1.25 đổi goal_id -> need_id, index giữ nguyên");
        assertTrue(rows.next());
        assertTrue(rows.getString(1).contains("need_id"), "V1.25 đổi goal_id -> need_id, index giữ nguyên");
      }
    }
  }

  @Test
  void auditUpgradePreservesExistingRowsAndSupportsStructuredMetadata() throws Exception {
    String schema = "audit_snapshot_upgrade";
    var builder = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations(DEV_LOCATIONS).schemas(schema);
    assertEquals(13, builder.target("1.13").load().migrate().migrationsExecuted);
    try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("set search_path to " + schema);
      statement.execute("insert into audit_log(actor_user_id,action,target_type,target_id,reason,created_at) values ('11111111-1111-1111-1111-111111111111','LINK_REVOKED','PARENT_STUDENT_LINK','99999999-1111-1111-1111-111111111111','historical reason','2026-10-01T00:00:00Z')");
    }
    var upgraded = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations(DEV_LOCATIONS).schemas(schema).load();
    assertEquals(14, upgraded.migrate().migrationsExecuted);
    upgraded.validate();
    assertEquals(0, upgraded.migrate().migrationsExecuted);
    try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("set search_path to " + schema);
      try (var rows = statement.executeQuery("select reason,created_at,metadata::text from audit_log")) {
        assertTrue(rows.next());
        assertEquals("historical reason",rows.getString(1));
        assertEquals(java.time.Instant.parse("2026-10-01T00:00:00Z"),rows.getTimestamp(2).toInstant());
        assertEquals("{}",rows.getString(3));
      }
    }
  }

  @Test
  void quotaUpgradePreservesHistoricalAttemptsAndIsIdempotent() throws Exception {
    String schema = "ai_quota_upgrade";
    Flyway previous =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations(DEV_LOCATIONS)
            .schemas(schema)
            .target(MigrationVersion.fromVersion("1.11"))
            .load();
    previous.migrate();
    try (var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("set search_path to " + schema);
      statement.execute(
          "insert into ai_request_log(user_id,feature,status,created_at) select"
              + " '11111111-1111-1111-1111-111111111111'::uuid,'MATCH_EXPLANATION',s,now()-interval"
              + " '1 hour' from unnest(array['SUCCESS','ERROR','TIMEOUT']) s");
      statement.execute(
          "insert into ai_request_log(user_id,feature,status)"
              + " values('11111111-1111-1111-1111-111111111111','TUTOR_BIO','SUCCESS')");
    }
    Flyway upgraded =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations(DEV_LOCATIONS)
            .schemas(schema)
            .load();
    assertEquals(16, upgraded.migrate().migrationsExecuted);
    upgraded.validate();
    assertEquals(0, upgraded.migrate().migrationsExecuted);
    try (var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("set search_path to " + schema);
      try (var rows =
          statement.executeQuery(
              "select count(*) from ai_match_reservation r join ai_request_log l on"
                  + " l.log_id=r.reservation_id and l.created_at=r.reserved_at and"
                  + " l.status=r.status")) {
        assertTrue(rows.next());
        assertEquals(3, rows.getInt(1));
      }
      try (var rows = statement.executeQuery("select count(*) from ai_match_quota_actor")) {
        assertTrue(rows.next());
        assertEquals(1, rows.getInt(1));
      }
    }
  }

  @Test
  @DisplayName(
      "Nâng cấp Connection V1.10 -> V1.14: bảo toàn lịch sử, JSONB, relink sau revoke và partial unique indexes")
  void connectionUpgradePreservesHistoricalDataAndEnforcesIntegrityConstraints() throws Exception {
    String schema = "connection_migration_upgrade";

    // 1. Migrate tới V1.10 (trước khi có V1.11 relink và version column)
    Flyway previous =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations(DEV_LOCATIONS)
            .schemas(schema)
            .target(MigrationVersion.fromVersion("1.10"))
            .load();
    assertEquals(10, previous.migrate().migrationsExecuted, "Phải chạy đúng 11 migrations tới V1.10");

    UUID studentUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID studentProfileId = UUID.fromString("11111111-1111-1111-1111-000000000001");
    UUID tutorUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    UUID tutorProfileId = UUID.fromString("22222222-2222-2222-2222-000000000001");
    UUID parentUserId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    UUID goalId = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");
    UUID inv1Id = UUID.fromString("bbbbbbbb-1111-1111-1111-111111111111");
    UUID inv2Id = UUID.fromString("bbbbbbbb-2222-2222-2222-222222222222");
    UUID link1Id = UUID.fromString("cccccccc-1111-1111-1111-111111111111");
    UUID req1Id = UUID.fromString("dddddddd-1111-1111-1111-111111111111");
    UUID req2Id = UUID.fromString("dddddddd-2222-2222-2222-222222222222");

    // 2. Chèn dữ liệu lịch sử phong phú ở V1.10
    try (Connection connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute("SET search_path TO " + schema);

      // 2.1. Tạo learning goal
      statement.executeUpdate(
          "INSERT INTO learning_goal(goal_id, student_id, subject_id, current_level, desired_level, goal_type, deadline, mode, budget_max, status) "
              + "VALUES ('" + goalId + "', '" + studentProfileId + "', 1, 5, 8, 'EXAM_PREPARATION', CURRENT_DATE + 30, 'BOTH', 300000, 'ACTIVE')");

      // 2.2. Lời mời liên kết: 1 PENDING, 1 EXPIRED
      statement.executeUpdate(
          "INSERT INTO link_invitation(invitation_id, inviter_user_id, invited_user_id, status, created_at, expires_at) "
              + "VALUES ('" + inv1Id + "', '" + parentUserId + "', '" + studentUserId + "', 'PENDING', now() - interval '1 day', now() + interval '13 days')");
      statement.executeUpdate(
          "INSERT INTO link_invitation(invitation_id, inviter_user_id, invited_user_id, status, created_at, expires_at) "
              + "VALUES ('" + inv2Id + "', '" + studentUserId + "', '" + parentUserId + "', 'EXPIRED', now() - interval '20 days', now() - interval '6 days')");

      // 2.3. Liên kết cha mẹ - học sinh: 1 REVOKED link
      statement.executeUpdate(
          "INSERT INTO parent_student_link(link_id, parent_user_id, student_id, status, confirmed_at, revoked_at, revoked_by, revoke_reason) "
              + "VALUES ('" + link1Id + "', '" + parentUserId + "', '" + studentProfileId + "', 'REVOKED', now() - interval '30 days', now() - interval '10 days', '" + studentUserId + "', 'Học sinh chủ động hủy liên kết')");

      // 2.4. Xác nhận tại V1.10: uk_parent_student cấm tạo link mới cho cùng cặp dù link cũ đã REVOKED
      assertThrows(
          SQLException.class,
          () -> {
            try (Statement s = connection.createStatement()) {
              s.executeUpdate(
                  "INSERT INTO parent_student_link(link_id, parent_user_id, student_id, status, confirmed_at) "
                      + "VALUES (gen_random_uuid(), '" + parentUserId + "', '" + studentProfileId + "', 'CONFIRMED', now())");
            }
          },
          "Tại V1.10, ràng buộc uk_parent_student chặn relink sau khi đã có bản ghi REVOKED");

      // 2.5. Yêu cầu kết nối: 1 PENDING với JSONB desired_slots, 1 REJECTED
      statement.executeUpdate(
          "INSERT INTO connection_request(request_id, student_id, tutor_id, goal_id, sent_by_user_id, desired_slots, message, status, created_at, expires_at) "
              + "VALUES ('" + req1Id + "', '" + studentProfileId + "', '" + tutorProfileId + "', '" + goalId + "', '" + studentUserId + "', "
              + "'[{\"dayOfWeek\": 2, \"startTime\": \"18:00\", \"endTime\": \"20:00\"}]'::jsonb, 'Cần tìm gia sư kèm hình học', 'PENDING', now() - interval '1 day', now() + interval '6 days')");
      statement.executeUpdate(
          "INSERT INTO connection_request(request_id, student_id, tutor_id, goal_id, sent_by_user_id, desired_slots, message, status, reject_reason, created_at, expires_at, responded_at) "
              + "VALUES ('" + req2Id + "', '" + studentProfileId + "', '" + tutorProfileId + "', '" + goalId + "', '" + studentUserId + "', "
              + "'[]'::jsonb, 'Lịch học trước đây', 'REJECTED', 'Gia sư kín lịch', now() - interval '15 days', now() - interval '8 days', now() - interval '14 days')");
    }

    // 3. Nâng cấp lên V1.14 (V1.11, V1.12, V1.13, V1.14)
    Flyway upgraded =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations(DEV_LOCATIONS)
            .schemas(schema)
            .outOfOrder(false)
            .validateOnMigrate(true)
            .baselineOnMigrate(false)
            .load();
    assertEquals(17, upgraded.migrate().migrationsExecuted, "Phải thực thi đúng 17 migrations: V1.11 -> V1.27");
    upgraded.validate();
    assertEquals(0, upgraded.migrate().migrationsExecuted, "Rerun phải là no-op");

    // 4. Kiểm chứng dữ liệu lịch sử được bảo tồn nguyên vẹn & các tính năng mới của V1.11+
    try (Connection connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute("SET search_path TO " + schema);

      // 4.1. Lịch sử link_invitation bảo tồn và có version = 0
      try (ResultSet rs =
          statement.executeQuery(
              "SELECT status, version FROM link_invitation WHERE invitation_id = '" + inv1Id + "'")) {
        assertTrue(rs.next());
        assertEquals("PENDING", rs.getString(1));
        assertEquals(0L, rs.getLong(2));
      }
      try (ResultSet rs =
          statement.executeQuery(
              "SELECT status, version FROM link_invitation WHERE invitation_id = '" + inv2Id + "'")) {
        assertTrue(rs.next());
        assertEquals("EXPIRED", rs.getString(1));
        assertEquals(0L, rs.getLong(2));
      }

      // 4.2. Lịch sử parent_student_link REVOKED bảo tồn và có version = 0
      try (ResultSet rs =
          statement.executeQuery(
              "SELECT status, version, revoke_reason FROM parent_student_link WHERE link_id = '" + link1Id + "'")) {
        assertTrue(rs.next());
        assertEquals("REVOKED", rs.getString(1));
        assertEquals(0L, rs.getLong(2));
        assertEquals("Học sinh chủ động hủy liên kết", rs.getString(3));
      }

      // 4.3. Lịch sử connection_request bảo tồn version = 0, JSONB desired_slots và trỏ tới nhu cầu cùng ID mục tiêu cũ
      try (ResultSet rs =
          statement.executeQuery(
              "SELECT status, version, desired_slots::text, need_id FROM connection_request WHERE request_id = '" + req1Id + "'")) {
        assertTrue(rs.next());
        assertEquals("PENDING", rs.getString(1));
        assertEquals(0L, rs.getLong(2));
        assertTrue(rs.getString(3).contains("18:00"), "JSONB desired_slots phải được bảo toàn nguyên vẹn");
        assertEquals(goalId.toString(), rs.getString(4), "V1.25 giữ nguyên ID khi đổi mục tiêu thành nhu cầu học");
      }
      try (ResultSet rs = statement.executeQuery(
          "SELECT COUNT(*) FROM learning_need WHERE need_id = '" + goalId + "'")) {
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1), "Mục tiêu cũ phải được chuyển thành nhu cầu học");
      }

      // 4.4. Relink sau khi revoke: Bây giờ được phép INSERT CONFIRMED mới cho cùng cặp
      UUID relinkId = UUID.randomUUID();
      statement.executeUpdate(
          "INSERT INTO parent_student_link(link_id, parent_user_id, student_id, status, confirmed_at) "
              + "VALUES ('" + relinkId + "', '" + parentUserId + "', '" + studentProfileId + "', 'CONFIRMED', now())");

      // Không cho phép 2 link cùng CONFIRMED cho cùng cặp (uq_parent_student_confirmed)
      assertThrows(
          SQLException.class,
          () -> {
            try (Statement s = connection.createStatement()) {
              s.executeUpdate(
                  "INSERT INTO parent_student_link(link_id, parent_user_id, student_id, status, confirmed_at) "
                      + "VALUES (gen_random_uuid(), '" + parentUserId + "', '" + studentProfileId + "', 'CONFIRMED', now())");
            }
          },
          "Partial unique index uq_parent_student_confirmed cấm 2 link CONFIRMED đồng thời");

      // Cho phép thêm link REVOKED thứ hai cho cùng cặp
      statement.executeUpdate(
          "INSERT INTO parent_student_link(link_id, parent_user_id, student_id, status, confirmed_at, revoked_at) "
              + "VALUES (gen_random_uuid(), '" + parentUserId + "', '" + studentProfileId + "', 'REVOKED', now() - interval '2 days', now() - interval '1 day')");

      // 4.5. Partial unique index uq_connection_request_pending: Cấm 2 request PENDING đồng thời
      assertThrows(
          SQLException.class,
          () -> {
            try (Statement s = connection.createStatement()) {
              s.executeUpdate(
                  "INSERT INTO connection_request(request_id, student_id, tutor_id, need_id, sent_by_user_id, status, expires_at) "
                      + "VALUES (gen_random_uuid(), '" + studentProfileId + "', '" + tutorProfileId + "', '" + goalId + "', '" + studentUserId + "', 'PENDING', now() + interval '7 days')");
            }
          },
          "Partial index uq_connection_request_pending cấm 2 request PENDING đồng thời cho cùng 1 cặp student-tutor");

      // 4.6. Partial unique index uq_link_invitation_pending: Cấm 2 invitation PENDING (kể cả đảo chiều)
      assertThrows(
          SQLException.class,
          () -> {
            try (Statement s = connection.createStatement()) {
              s.executeUpdate(
                  "INSERT INTO link_invitation(invitation_id, inviter_user_id, invited_user_id, status, expires_at) "
                      + "VALUES (gen_random_uuid(), '" + studentUserId + "', '" + parentUserId + "', 'PENDING', now() + interval '14 days')");
            }
          },
          "Partial index uq_link_invitation_pending cấm 2 invitation PENDING hai chiều");

      // 4.7. Partial unique index uq_class_student_tutor_active: Cấm 2 class ACTIVE đồng thời
      UUID class1Id = UUID.randomUUID();
      statement.executeUpdate(
          "INSERT INTO class(class_id, connection_request_id, student_id, tutor_id, need_id, price_per_session, status) "
              + "VALUES ('" + class1Id + "', '" + req1Id + "', '" + studentProfileId + "', '" + tutorProfileId + "', '" + goalId + "', 200000, 'ACTIVE')");
      assertThrows(
          SQLException.class,
          () -> {
            try (Statement s = connection.createStatement()) {
              s.executeUpdate(
                  "INSERT INTO class(class_id, connection_request_id, student_id, tutor_id, need_id, price_per_session, status) "
                      + "VALUES (gen_random_uuid(), '" + req2Id + "', '" + studentProfileId + "', '" + tutorProfileId + "', '" + goalId + "', 200000, 'ACTIVE')");
            }
          },
          "Partial index uq_class_student_tutor_active cấm 2 class ACTIVE đồng thời cho cùng cặp");

      // 4.8. Check constraints: tự gửi invitation cho chính mình bị chặn
      assertThrows(
          SQLException.class,
          () -> {
            try (Statement s = connection.createStatement()) {
              s.executeUpdate(
                  "INSERT INTO link_invitation(invitation_id, inviter_user_id, invited_user_id, status, expires_at) "
                      + "VALUES (gen_random_uuid(), '" + studentUserId + "', '" + studentUserId + "', 'PENDING', now() + interval '14 days')");
            }
          },
          "chk_invitation_different_users cấm gửi invitation cho chính mình");

      // 4.9. Table connection_invite_quota hoạt động
      statement.executeUpdate(
          "INSERT INTO connection_invite_quota(user_id, window_started_at, attempts) "
              + "VALUES ('" + parentUserId + "', now(), 3)");
      try (ResultSet rs =
          statement.executeQuery(
              "SELECT attempts FROM connection_invite_quota WHERE user_id = '" + parentUserId + "'")) {
        assertTrue(rs.next());
        assertEquals(3, rs.getInt(1));
      }
    }
  }
}

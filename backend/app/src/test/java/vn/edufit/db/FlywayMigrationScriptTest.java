package vn.edufit.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử đơn vị kiểm tra quy chuẩn các file script migration của Flyway.
 *
 * <p>Mục đích kiểm thử:
 * <ul>
 *   <li><b>Quy chuẩn đặt tên:</b> Mọi file migration bắt buộc tuân thủ
 *       định dạng: {@code V1.{seq}__{description}.sql} hoặc {@code V{yyyy}.{MM}.{dd}.{seq}__{description}.sql}.</li>
 *   <li><b>Tính toàn vẹn dữ liệu:</b> Cấm tuyệt đối các file rỗng 0 bytes hoặc file không có nội dung SQL.</li>
 *   <li><b>Chạy nhanh độc lập:</b> Bài test này chạy trong vài mili-giây mà không cần Docker.</li>
 * </ul>
 */
@DisplayName("Kiểm thử quy chuẩn các file Flyway Migration (Convention & Naming)")
class FlywayMigrationScriptTest {

  /**
   * Pattern biểu thức chính quy kiểm tra định dạng tên file migration:
   * Chấp nhận: V1.01__create_account_and_session_tables.sql hoặc V2026.09.30.01__*.sql
   */
  private static final Pattern MIGRATION_NAME_PATTERN =
      Pattern.compile("^V(?:1\\.\\d{2}|\\d{4}\\.\\d{2}\\.\\d{2}\\.\\d{2})__[a-z0-9_]+\\.sql$");

  private final Path migrationRootDir =
      Paths.get("src", "main", "resources", "db", "migration");
  private final Path seedRootDir =
      Paths.get("src", "main", "resources", "db", "seed");

  private int checksum(Path file) throws IOException {
    var checksum = new java.util.zip.CRC32();
    for (String line : Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
      checksum.update(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    return (int) checksum.getValue();
  }

  @Test
  @DisplayName("Tất cả file SQL migration phải tuân thủ chuẩn đặt tên và không được để trống")
  void allMigrationFilesShouldFollowNamingConvention() throws IOException {
    assertTrue(Files.exists(migrationRootDir), "Thư mục migration phải tồn tại");

    try (Stream<Path> paths = Files.walk(migrationRootDir)) {
      List<Path> sqlFiles = paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".sql"))
          .sorted()
          .toList();

      assertFalse(sqlFiles.isEmpty(), "Phải có ít nhất một file migration SQL trong db/migration");
      assertEquals(23, sqlFiles.size(), "Payment đến V1.21, Connection V1.22, danh mục V1.23, nhu cầu học V1.24–V1.26; seed demo đã tách sang db/seed");
      assertEquals(sqlFiles.size(), sqlFiles.stream()
          .map(path -> path.getFileName().toString().split("__", 2)[0])
          .distinct().count(), "Version Flyway phải duy nhất trên tất cả module");
      assertTrue(Files.exists(migrationRootDir.resolve("connection")
          .resolve("V1.11__allow_relink_after_revocation.sql")),
          "Migration cho relink, optimistic version và invite quota phải tồn tại");
      assertTrue(Files.exists(migrationRootDir.resolve("platform/V1.23__seed_reference_catalogs.sql")),
          "Danh mục tham chiếu phải có ở mọi môi trường");
      for (Path file : sqlFiles) {
        assertFalse(Files.readString(file).contains("$argon2id$"),
            "Migration production [" + file.getFileName() + "] không được chứa tài khoản/mật khẩu demo");
      }

      // Seed demo được di chuyển nguyên trạng: checksum giữ nguyên để DB dev đã áp dụng vẫn validate được
      assertEquals(-1594466614, checksum(seedRootDir.resolve("V1.09__seed_demo_accounts_and_catalogs.sql")),
          "V1.09 phải khớp history main, không thay bằng DDL Connection");
      assertEquals(-1594466614, checksum(seedRootDir.resolve("V1.10__seed_demo_accounts_and_catalogs.sql")),
          "Giữ nguyên checksum V1.10 đã phát hành");
      assertTrue(Files.exists(seedRootDir.resolve("V1.13__seed_demo_matching_data.sql")),
          "Seed matching nằm ở db/seed");
      assertEquals(1468271677, checksum(migrationRootDir.resolve("connection/V1.11__allow_relink_after_revocation.sql")),
          "Connection chỉ đổi version migration, giữ nguyên SQL");

      assertTrue(Files.exists(migrationRootDir.resolve("platform/V1.14__add_structured_audit_metadata.sql")));
      assertTrue(Files.exists(migrationRootDir.resolve("iam/V1.15__add_oauth_and_social_accounts.sql")),
          "Migration cho OAuth và user social account phải tồn tại");
      assertTrue(Files.exists(migrationRootDir.resolve("connection/V1.22__allow_multi_subject_connections.sql")),
          "Migration cho phép multi-subject connection và active class phải tồn tại");
      for (Path file : sqlFiles) {
        String fileName = file.getFileName().toString();
        assertTrue(
            MIGRATION_NAME_PATTERN.matcher(fileName).matches(),
            "File migration [" + fileName + "] không đúng định dạng V1.{seq}__*.sql hoặc V{yyyy}.{MM}.{dd}.{seq}__*.sql"
        );

        long fileSize = Files.size(file);
        assertTrue(
            fileSize > 100,
            "File migration [" + fileName + "] không được để trống (kích thước: " + fileSize + " bytes)"
        );
      }
    }
  }

  @Test
  @DisplayName("File migration phải chứa các bảng cốt lõi theo docs/edufit-schema.dbml")
  void migrationFilesShouldContainCoreTables() throws IOException {
    Path iamDir = migrationRootDir.resolve("iam");
    assertTrue(Files.exists(iamDir), "Thư mục migration/iam phải tồn tại");

    Path iamMigration = iamDir.resolve("V1.01__create_account_and_session_tables.sql");
    assertTrue(Files.exists(iamMigration), "Script V1.01 phải tồn tại");
    String iamContent = Files.readString(iamMigration);
    assertTrue(iamContent.contains("CREATE TABLE IF NOT EXISTS account"), "Phải chứa bảng account");
    assertTrue(iamContent.contains("CREATE TABLE IF NOT EXISTS email_token"), "Phải chứa bảng email_token");
    assertTrue(iamContent.contains("CREATE TABLE IF NOT EXISTS SPRING_SESSION"), "Phải chứa bảng SPRING_SESSION");

    Path schedulingDir = migrationRootDir.resolve("scheduling");
    Path schedulingMigration = schedulingDir.resolve("V1.05__create_tutoring_session_tables.sql");
    assertTrue(Files.exists(schedulingMigration), "Script V1.05 phải tồn tại");
    String schedulingContent = Files.readString(schedulingMigration);
    assertTrue(schedulingContent.contains("CREATE TABLE IF NOT EXISTS tutoring_session"), "Phải chứa bảng tutoring_session");
    assertTrue(schedulingContent.contains("no_tutor_overlap"), "Phải chứa exclusion constraint no_tutor_overlap");
  }
}

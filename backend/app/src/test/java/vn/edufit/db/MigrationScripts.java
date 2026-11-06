package vn.edufit.db;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.flywaydb.core.api.MigrationVersion;

/**
 * Đếm migration có version trực tiếp từ mã nguồn, để test không phải sửa tay mỗi khi thêm migration mới.
 *
 * <p>Thư mục tương đối theo module {@code app} (thư mục làm việc khi Maven chạy test).
 */
final class MigrationScripts {

  static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration");
  static final Path SEED = Path.of("src", "main", "resources", "db", "seed");

  private MigrationScripts() {}

  /** Version của mọi file {@code V*.sql} trong các thư mục (quét đệ quy như Flyway). */
  static List<MigrationVersion> versions(Path... roots) {
    return Stream.of(roots).flatMap(MigrationScripts::walk)
        .map(p -> p.getFileName().toString())
        .filter(name -> name.startsWith("V") && name.endsWith(".sql") && name.contains("__"))
        .map(name -> MigrationVersion.fromVersion(name.substring(1, name.indexOf("__"))))
        .toList();
  }

  static int count(Path... roots) {
    return versions(roots).size();
  }

  /** Version cuối của schema (db/migration). Seed demo V1.34 chỉ dành cho CSDL dev mới, không áp lên dữ liệu có sẵn. */
  static MigrationVersion schemaHead() {
    return versions(MIGRATION).stream().max(MigrationVersion::compareTo).orElseThrow();
  }

  /** Số migration có version lớn hơn {@code version} (phần còn lại sau khi đã migrate tới target). */
  static int countAfter(String version, Path... roots) {
    return count(v -> v.isNewerThan(version) && !v.isNewerThan(schemaHead().getVersion()), roots);
  }

  /** Số migration có version nhỏ hơn hoặc bằng {@code version} (migrate với target). */
  static int countUpTo(String version, Path... roots) {
    return count(v -> !v.isNewerThan(version), roots);
  }

  private static int count(Predicate<MigrationVersion> filter, Path... roots) {
    return (int) versions(roots).stream().filter(filter).count();
  }

  private static Stream<Path> walk(Path root) {
    try {
      return Files.walk(root).filter(Files::isRegularFile).toList().stream();
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}

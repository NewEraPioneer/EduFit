package vn.edufit.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testcontainers phải chạy đúng image PostgreSQL mà {@code compose.yaml} dùng cho môi trường thật,
 * và không test nào được tự bỏ qua khi thiếu Docker (Exit Code 0 giả).
 */
class ContainerImageParityTest {

  private static final Path BACKEND_ROOT = Paths.get("..").toAbsolutePath().normalize();
  private static final Path COMPOSE_FILE = BACKEND_ROOT.resolveSibling("compose.yaml");
  private static final Pattern COMPOSE_DB_IMAGE = Pattern.compile("(?m)^\\s+image:\\s*(postgres:\\S+)\\s*$");
  private static final Pattern CONTAINER_IMAGE = Pattern.compile("PostgreSQLContainer<[^>]*>\\(\"([^\"]+)\"\\)");

  @Test
  @DisplayName("Mọi PostgreSQLContainer trong test dùng đúng image postgres của compose.yaml")
  void testcontainersUseComposePostgresImage() throws IOException {
    Matcher compose = COMPOSE_DB_IMAGE.matcher(Files.readString(COMPOSE_FILE));
    assertTrue(compose.find(), "compose.yaml phải khai báo image postgres cho service db");
    String expected = compose.group(1);

    Map<String, List<String>> imagesByFile;
    try (Stream<Path> sources = testSources()) {
      imagesByFile = sources.collect(Collectors.toMap(
          path -> BACKEND_ROOT.relativize(path).toString(),
          path -> CONTAINER_IMAGE.matcher(read(path)).results().map(m -> m.group(1)).toList()));
    }
    assertFalse(imagesByFile.values().stream().allMatch(List::isEmpty), "Phải tìm thấy PostgreSQLContainer trong test");

    Map<String, List<String>> mismatched = imagesByFile.entrySet().stream()
        .filter(entry -> entry.getValue().stream().anyMatch(image -> !image.equals(expected)))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    assertEquals(Map.of(), mismatched, "Image Testcontainers phải là " + expected);
  }

  @Test
  @DisplayName("Không test nào dùng disabledWithoutDocker để tự bỏ qua khi thiếu Docker")
  void testcontainersNeverSkipSilently() throws IOException {
    List<String> skipping;
    try (Stream<Path> sources = testSources()) {
      skipping = sources.filter(path -> read(path).contains("disabledWithoutDocker"))
          .map(path -> BACKEND_ROOT.relativize(path).toString())
          .filter(path -> !path.endsWith("ContainerImageParityTest.java"))
          .toList();
    }
    assertEquals(List.of(), skipping);
  }

  private static Stream<Path> testSources() throws IOException {
    return Files.walk(BACKEND_ROOT)
        .filter(path -> path.toString().endsWith(".java"))
        .filter(path -> path.toString().contains("src" + java.io.File.separator + "test"))
        .filter(path -> !path.toString().contains(java.io.File.separator + "target" + java.io.File.separator));
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}

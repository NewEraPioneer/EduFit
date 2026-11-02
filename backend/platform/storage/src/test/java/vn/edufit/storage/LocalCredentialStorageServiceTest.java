package vn.edufit.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;
import vn.edufit.storage.api.StoredCredentialFile;
import vn.edufit.storage.api.StoredCredentialResource;
import vn.edufit.storage.infra.CredentialStorageProperties;
import vn.edufit.storage.infra.FileValidationService;
import vn.edufit.storage.infra.LocalCredentialStorageService;

@DisplayName("Kiểm thử LocalCredentialStorageService")
class LocalCredentialStorageServiceTest {

  @TempDir
  private Path tempDir;

  private LocalCredentialStorageService storageService;

  @BeforeEach
  void setUp() {
    CredentialStorageProperties properties = new CredentialStorageProperties();
    properties.setRoot(tempDir);
    FileValidationService validationService = new FileValidationService(properties);
    storageService = new LocalCredentialStorageService(properties, validationService);
  }

  @Test
  @DisplayName("STO-LOC-001: Lưu, load và xóa file bằng storage key an toàn")
  void shouldStoreLoadAndDeleteCredentialFile() throws IOException {
    byte[] content = "%PDF-1.4\ncredential".getBytes();
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "credential.pdf",
        "application/pdf",
        content
    );

    StoredCredentialFile stored = storageService.store(file);
    StoredCredentialResource loaded = storageService.load(stored.storageKey());

    assertEquals("credential.pdf", stored.fileName());
    assertEquals("application/pdf", stored.contentType());
    assertEquals(content.length, stored.sizeBytes());
    assertTrue(stored.storageKey().endsWith(".pdf"));
    assertArrayEquals(content, loaded.resource().getContentAsByteArray());

    storageService.delete(stored.storageKey());

    assertFalse(Files.exists(tempDir.resolve(stored.storageKey())));
  }

  @Test
  @DisplayName("STO-LOC-002: Chặn storage key path traversal khi load file")
  void shouldRejectPathTraversalStorageKeyWhenLoading() {
    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> storageService.load("../secret.pdf")
    );

    assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
  }

  @Test
  @DisplayName("STO-LOC-003: Chặn storage key path traversal khi xóa file")
  void shouldRejectPathTraversalStorageKeyWhenDeleting() {
    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> storageService.delete("../secret.pdf")
    );

    assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
  }

  @Test
  @DisplayName("STO-LOC-004: Khởi tạo thư mục storage thành công qua init()")
  void shouldInitializeStorageDirectorySuccessfully() {
    Path nonExistent = tempDir.resolve("new-credentials-dir");
    CredentialStorageProperties props = new CredentialStorageProperties();
    props.setRoot(nonExistent);
    FileValidationService validation = new FileValidationService(props);
    LocalCredentialStorageService service = new LocalCredentialStorageService(props, validation);

    assertFalse(Files.exists(nonExistent));
    service.init();
    assertTrue(Files.exists(nonExistent));
    assertTrue(Files.isDirectory(nonExistent));
  }

  @Test
  @DisplayName("STO-LOC-005: Tự động tạo thư mục khi lưu file vào thư mục chưa tồn tại")
  void shouldCreateDirectoryWhenStoringFile() throws IOException {
    Path nestedDir = tempDir.resolve("nested").resolve("credentials");
    CredentialStorageProperties props = new CredentialStorageProperties();
    props.setRoot(nestedDir);
    FileValidationService validation = new FileValidationService(props);
    LocalCredentialStorageService service = new LocalCredentialStorageService(props, validation);

    byte[] content = "%PDF-1.4\ncontent".getBytes();
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "nested-credential.pdf",
        "application/pdf",
        content
    );

    StoredCredentialFile stored = service.store(file);
    assertNotNull(stored);
    assertTrue(Files.exists(nestedDir.resolve(stored.storageKey())));
  }

  @Test
  @DisplayName("STO-LOC-006: Ném InvalidOperationException kèm cause khi thư mục không thể ghi")
  void shouldThrowInvalidOperationExceptionWithCauseWhenStoragePathIsBlocked() throws IOException {
    // Tạo một tệp thường để chặn tạo thư mục con bên dưới nó
    Path blockingFile = tempDir.resolve("blocked-file");
    Files.createFile(blockingFile);

    Path impossibleDir = blockingFile.resolve("cannot-create-dir");
    CredentialStorageProperties props = new CredentialStorageProperties();
    props.setRoot(impossibleDir);
    FileValidationService validation = new FileValidationService(props);
    LocalCredentialStorageService service = new LocalCredentialStorageService(props, validation);

    byte[] content = "%PDF-1.4\ncontent".getBytes();
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "test.pdf",
        "application/pdf",
        content
    );

    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> service.store(file)
    );

    assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
    assertEquals("Không thể lưu file bằng cấp vào local storage", ex.getMessage());
    assertNotNull(ex.getCause());
    assertTrue(ex.getCause() instanceof IOException);
  }

  @Test
  @DisplayName("STO-LOC-007: Ném EntityNotFoundException khi tải file không tồn tại")
  void shouldThrowEntityNotFoundWhenLoadingNonExistentFile() {
    assertThrows(
        EntityNotFoundException.class,
        () -> storageService.load("non-existent-file.pdf")
    );
  }

  @Test
  @DisplayName("STO-LOC-008: Chặn storage key rỗng hoặc null khi load và delete")
  void shouldRejectNullOrBlankStorageKey() {
    assertThrows(InvalidOperationException.class, () -> storageService.load(null));
    assertThrows(InvalidOperationException.class, () -> storageService.load("   "));
    assertThrows(InvalidOperationException.class, () -> storageService.delete(null));
    assertThrows(InvalidOperationException.class, () -> storageService.delete("   "));
  }

  @Test
  @DisplayName("STO-LOC-009: Xử lý an toàn khi init() gặp lỗi IO (không làm sập bean startup)")
  void shouldHandleInitGracefullyWhenBlocked() throws IOException {
    Path blockingFile = tempDir.resolve("blocked-root-init");
    Files.createFile(blockingFile);

    CredentialStorageProperties props = new CredentialStorageProperties();
    props.setRoot(blockingFile.resolve("impossible"));
    FileValidationService validation = new FileValidationService(props);
    LocalCredentialStorageService service = new LocalCredentialStorageService(props, validation);

    // Không được throw exception ra ngoài
    service.init();
    service.afterPropertiesSet();
  }

  @Test
  @DisplayName("STO-LOC-010: Ném InvalidOperationException kèm cause khi delete gặp lỗi IO")
  void shouldThrowInvalidOperationExceptionWhenDeleteFailsWithIoError() throws IOException {
    // Tạo 1 thư mục chứa file con để khi gọi delete(folder) sẽ bị DirectoryNotEmptyException (IOException)
    Path folder = tempDir.resolve("non-empty-folder");
    Files.createDirectories(folder);
    Files.createFile(folder.resolve("child.txt"));

    InvalidOperationException ex = assertThrows(
        InvalidOperationException.class,
        () -> storageService.delete("non-empty-folder")
    );

    assertEquals(ErrorCode.INVALID_OPERATION, ex.getErrorCode());
    assertEquals("Không thể xóa file bằng cấp khỏi local storage", ex.getMessage());
    assertNotNull(ex.getCause());
    assertTrue(ex.getCause() instanceof IOException);
  }
}

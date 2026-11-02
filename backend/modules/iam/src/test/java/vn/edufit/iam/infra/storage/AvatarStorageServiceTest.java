package vn.edufit.iam.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.InvalidOperationException;

@DisplayName("Kiểm thử AvatarStorageService - Quản lý lưu trữ tệp ảnh")
class AvatarStorageServiceTest {

  @TempDir
  Path tempDir;

  private AvatarStorageService avatarStorageService;

  @BeforeEach
  void setUp() {
    avatarStorageService = new AvatarStorageService(tempDir.toString());
  }

  @Test
  @DisplayName("Lưu tệp ảnh đại diện JPG/PNG hợp lệ thành công")
  void storeAvatar_validFile_success() {
    UUID accountId = UUID.randomUUID();
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "avatar.png",
        "image/png",
        "fake-image-content".getBytes()
    );

    String avatarUrl = avatarStorageService.storeAvatar(accountId, file);

    assertThat(avatarUrl).startsWith("/api/v1/auth/avatar/" + accountId + "_");
    assertThat(avatarUrl).endsWith(".png");

    String fileName = avatarUrl.substring("/api/v1/auth/avatar/".length());
    Resource resource = avatarStorageService.loadAvatarResource(fileName);
    assertThat(resource.exists()).isTrue();
  }

  @Test
  @DisplayName("Từ chối tệp không phải định dạng ảnh")
  void storeAvatar_invalidFileType_throwsException() {
    UUID accountId = UUID.randomUUID();
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "script.exe",
        "application/octet-stream",
        "malicious".getBytes()
    );

    assertThatThrownBy(() -> avatarStorageService.storeAvatar(accountId, file))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("Định dạng tệp ảnh không hợp lệ");
  }

  @Test
  @DisplayName("Từ chối tệp rỗng")
  void storeAvatar_emptyFile_throwsException() {
    UUID accountId = UUID.randomUUID();
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "empty.jpg",
        "image/jpeg",
        new byte[0]
    );

    assertThatThrownBy(() -> avatarStorageService.storeAvatar(accountId, file))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("không được để trống");
  }

  @Test
  @DisplayName("Chặn tấn công Path Traversal khi tải ảnh")
  void loadAvatar_pathTraversal_throwsException() {
    assertThatThrownBy(() -> avatarStorageService.loadAvatarResource("../../../etc/passwd"))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("Tên tệp ảnh đại diện không hợp lệ");
  }

  @Test
  @DisplayName("Ném lỗi khi tải tệp không tồn tại")
  void loadAvatar_notFound_throwsException() {
    assertThatThrownBy(() -> avatarStorageService.loadAvatarResource("nonexistent.png"))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  @DisplayName("Xóa tệp ảnh thành công nếu là tệp local")
  void deleteAvatarFileIfLocal_success() throws IOException {
    UUID accountId = UUID.randomUUID();
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "my_pic.webp",
        "image/webp",
        "test-content".getBytes()
    );

    String avatarUrl = avatarStorageService.storeAvatar(accountId, file);
    String fileName = avatarUrl.substring("/api/v1/auth/avatar/".length());

    Path filePath = tempDir.resolve(fileName);
    assertThat(Files.exists(filePath)).isTrue();

    avatarStorageService.deleteAvatarFileIfLocal(avatarUrl);
    assertThat(Files.exists(filePath)).isFalse();
  }
}

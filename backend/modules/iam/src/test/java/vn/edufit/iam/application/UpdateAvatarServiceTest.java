package vn.edufit.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.infra.persistence.Account;
import vn.edufit.iam.infra.persistence.AccountRepository;
import vn.edufit.iam.infra.storage.AvatarStorageService;
import vn.edufit.iam.web.dto.AuthUserResponse;
import vn.edufit.shared.exception.EntityNotFoundException;

@DisplayName("Kiểm thử UpdateAvatarService - Quản lý ảnh đại diện")
class UpdateAvatarServiceTest {

  private AccountRepository accountRepository;
  private AvatarStorageService avatarStorageService;
  private UpdateAvatarService updateAvatarService;

  @BeforeEach
  void setUp() {
    accountRepository = mock(AccountRepository.class);
    avatarStorageService = mock(AvatarStorageService.class);
    updateAvatarService = new UpdateAvatarService(accountRepository, avatarStorageService);
  }

  @Test
  @DisplayName("Tải lên ảnh đại diện thành công và xóa tệp cũ nếu là ảnh local")
  void uploadAvatar_success() {
    UUID accountId = UUID.randomUUID();
    Account account = Account.createOAuthAccount(
        "test@edufit.vn",
        "Nguyen Van Test",
        AccountRole.STUDENT,
        "/api/v1/auth/avatar/old_avatar.png"
    );

    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "avatar.jpg",
        "image/jpeg",
        new byte[]{1, 2, 3}
    );
    when(avatarStorageService.storeAvatar(eq(accountId), any(MultipartFile.class)))
        .thenReturn("/api/v1/auth/avatar/new_avatar.jpg");

    AuthUserResponse response = updateAvatarService.uploadAvatar(accountId, file);

    assertThat(response.avatarUrl()).isEqualTo("/api/v1/auth/avatar/new_avatar.jpg");
    assertThat(account.getAvatarUrl()).isEqualTo("/api/v1/auth/avatar/new_avatar.jpg");
    verify(avatarStorageService).deleteAvatarFileIfLocal("/api/v1/auth/avatar/old_avatar.png");
    verify(accountRepository).save(account);
  }

  @Test
  @DisplayName("Cập nhật ảnh đại diện bằng URL trực tiếp")
  void updateAvatarUrl_success() {
    UUID accountId = UUID.randomUUID();
    Account account = Account.createOAuthAccount(
        "test@edufit.vn",
        "Nguyen Van Test",
        AccountRole.STUDENT,
        null
    );

    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

    String dicebearUrl = "https://api.dicebear.com/7.x/bottts/svg?seed=test";
    AuthUserResponse response = updateAvatarService.updateAvatarUrl(accountId, dicebearUrl);

    assertThat(response.avatarUrl()).isEqualTo(dicebearUrl);
    assertThat(account.getAvatarUrl()).isEqualTo(dicebearUrl);
    verify(accountRepository).save(account);
  }

  @Test
  @DisplayName("Xóa ảnh đại diện đưa về null")
  void removeAvatar_success() {
    UUID accountId = UUID.randomUUID();
    Account account = Account.createOAuthAccount(
        "test@edufit.vn",
        "Nguyen Van Test",
        AccountRole.STUDENT,
        "/api/v1/auth/avatar/current.png"
    );

    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

    AuthUserResponse response = updateAvatarService.removeAvatar(accountId);

    assertThat(response.avatarUrl()).isNull();
    assertThat(account.getAvatarUrl()).isNull();
    verify(avatarStorageService).deleteAvatarFileIfLocal("/api/v1/auth/avatar/current.png");
    verify(accountRepository).save(account);
  }

  @Test
  @DisplayName("Báo lỗi nếu không tìm thấy tài khoản người dùng")
  void userNotFound_throwsException() {
    UUID accountId = UUID.randomUUID();
    when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

    MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});

    assertThatThrownBy(() -> updateAvatarService.uploadAvatar(accountId, file))
        .isInstanceOf(EntityNotFoundException.class);
  }
}

package vn.edufit.iam.application;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.edufit.iam.infra.persistence.Account;
import vn.edufit.iam.infra.persistence.AccountRepository;
import vn.edufit.iam.infra.storage.AvatarStorageService;
import vn.edufit.iam.web.dto.AuthUserResponse;
import vn.edufit.shared.exception.EntityNotFoundException;

/**
 * Xử lý cập nhật, tải lên và xóa ảnh đại diện của người dùng (Nhiệm vụ 1.3).
 */
@Service
public class UpdateAvatarService {

  private final AccountRepository accountRepository;
  private final AvatarStorageService avatarStorageService;

  public UpdateAvatarService(
      AccountRepository accountRepository,
      AvatarStorageService avatarStorageService
  ) {
    this.accountRepository = accountRepository;
    this.avatarStorageService = avatarStorageService;
  }

  /**
   * Tải lên ảnh đại diện mới từ tệp multipart và cập nhật vào hồ sơ người dùng.
   */
  @Transactional
  public AuthUserResponse uploadAvatar(UUID accountId, MultipartFile file) {
    Objects.requireNonNull(accountId, "accountId không được để trống");
    Account account = accountRepository.findById(accountId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy thông tin tài khoản người dùng"));

    String oldAvatarUrl = account.getAvatarUrl();
    String newAvatarUrl = avatarStorageService.storeAvatar(accountId, file);

    account.updateAvatar(newAvatarUrl);
    accountRepository.save(account);

    // Xóa file ảnh cũ nếu trước đó người dùng đã upload ảnh local
    if (oldAvatarUrl != null) {
      avatarStorageService.deleteAvatarFileIfLocal(oldAvatarUrl);
    }

    return AuthUserResponse.from(account);
  }

  /**
   * Cập nhật ảnh đại diện bằng URL (ví dụ: ảnh mẫu Dicebear hoặc link ảnh ngoài).
   */
  @Transactional
  public AuthUserResponse updateAvatarUrl(UUID accountId, String avatarUrl) {
    Objects.requireNonNull(accountId, "accountId không được để trống");
    Account account = accountRepository.findById(accountId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy thông tin tài khoản người dùng"));

    String oldAvatarUrl = account.getAvatarUrl();
    account.updateAvatar(avatarUrl);
    accountRepository.save(account);

    if (oldAvatarUrl != null) {
      avatarStorageService.deleteAvatarFileIfLocal(oldAvatarUrl);
    }

    return AuthUserResponse.from(account);
  }

  /**
   * Xóa ảnh đại diện (đưa về mặc định).
   */
  @Transactional
  public AuthUserResponse removeAvatar(UUID accountId) {
    Objects.requireNonNull(accountId, "accountId không được để trống");
    Account account = accountRepository.findById(accountId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy thông tin tài khoản người dùng"));

    String oldAvatarUrl = account.getAvatarUrl();
    account.updateAvatar(null);
    accountRepository.save(account);

    if (oldAvatarUrl != null) {
      avatarStorageService.deleteAvatarFileIfLocal(oldAvatarUrl);
    }

    return AuthUserResponse.from(account);
  }
}

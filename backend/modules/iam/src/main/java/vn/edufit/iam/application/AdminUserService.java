package vn.edufit.iam.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.infra.persistence.Account;
import vn.edufit.iam.infra.persistence.AccountRepository;
import vn.edufit.iam.web.dto.AdminUserItemResponse;
import vn.edufit.iam.web.dto.AdminUserListResponse;
import vn.edufit.iam.web.dto.AdminUserStatsResponse;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/**
 * Dịch vụ Quản trị người dùng toàn hệ thống (UC Quản trị viên).
 */
@Service
public class AdminUserService {

  private final AccountRepository accountRepository;

  public AdminUserService(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  /**
   * Lấy danh sách tài khoản theo bộ lọc tìm kiếm và phân trang.
   */
  @Transactional(readOnly = true)
  public AdminUserListResponse getUsers(
      AccountRole role,
      AccountStatus status,
      String search,
      int page,
      int size
  ) {
    int safePage = Math.max(0, page);
    int safeSize = Math.max(1, Math.min(size, 100));
    Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

    String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
    Page<Account> accountPage = accountRepository.searchAccounts(role, status, cleanSearch, pageable);

    List<AdminUserItemResponse> users = accountPage.getContent().stream()
        .map(AdminUserItemResponse::from)
        .toList();

    return new AdminUserListResponse(
        users,
        accountPage.getTotalElements(),
        accountPage.getTotalPages(),
        accountPage.getNumber(),
        accountPage.getSize()
    );
  }

  /**
   * Lấy thông tin chi tiết một người dùng theo ID.
   */
  @Transactional(readOnly = true)
  public AdminUserItemResponse getUserById(UUID id) {
    Account account = accountRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với mã định danh: " + id));
    return AdminUserItemResponse.from(account);
  }

  /**
   * Khóa tài khoản người dùng (chuyển sang trạng thái DEACTIVATED).
   */
  @Transactional
  public AdminUserItemResponse lockUser(UUID targetUserId, UUID currentAdminId, String reason) {
    return updateUserStatus(targetUserId, AccountStatus.DEACTIVATED, currentAdminId, reason);
  }

  /**
   * Mở khóa tài khoản người dùng (chuyển sang trạng thái ACTIVE).
   */
  @Transactional
  public AdminUserItemResponse unlockUser(UUID targetUserId, UUID currentAdminId) {
    return updateUserStatus(targetUserId, AccountStatus.ACTIVE, currentAdminId, "Mở khóa tài khoản bởi Quản trị viên");
  }

  /**
   * Cập nhật trạng thái người dùng (ACTIVE / DEACTIVATED).
   */
  @Transactional
  public AdminUserItemResponse updateUserStatus(
      UUID targetUserId,
      AccountStatus newStatus,
      UUID currentAdminId,
      String reason
  ) {
    Objects.requireNonNull(targetUserId, "Mã người dùng không được để trống");
    Objects.requireNonNull(newStatus, "Trạng thái mới không được để trống");

    Account account = accountRepository.findById(targetUserId)
        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với mã định danh: " + targetUserId));

    // Không cho phép Quản trị viên tự khóa tài khoản của chính mình
    if (Objects.equals(account.getId(), currentAdminId) && newStatus == AccountStatus.DEACTIVATED) {
      throw new InvalidOperationException(
          ErrorCode.INVALID_OPERATION,
          "Bạn không thể tự vô hiệu hóa tài khoản Quản trị viên của chính mình"
      );
    }

    account.updateStatus(newStatus);
    accountRepository.save(account);

    return AdminUserItemResponse.from(account);
  }

  /**
   * Lấy tổng quan các chỉ số người dùng trong hệ thống.
   */
  @Transactional(readOnly = true)
  public AdminUserStatsResponse getUserStats() {
    long totalUsers = accountRepository.count();
    long totalStudents = accountRepository.countByRole(AccountRole.STUDENT);
    long totalTutors = accountRepository.countByRole(AccountRole.TUTOR);
    long totalParents = accountRepository.countByRole(AccountRole.PARENT);
    long totalActive = accountRepository.countByStatus(AccountStatus.ACTIVE);
    long totalDeactivated = accountRepository.countByStatus(AccountStatus.DEACTIVATED);

    long activeStudents = accountRepository.countByRoleAndStatus(AccountRole.STUDENT, AccountStatus.ACTIVE);
    long deactivatedStudents = accountRepository.countByRoleAndStatus(AccountRole.STUDENT, AccountStatus.DEACTIVATED);
    long activeParents = accountRepository.countByRoleAndStatus(AccountRole.PARENT, AccountStatus.ACTIVE);
    long deactivatedParents = accountRepository.countByRoleAndStatus(AccountRole.PARENT, AccountStatus.DEACTIVATED);
    long activeTutors = accountRepository.countByRoleAndStatus(AccountRole.TUTOR, AccountStatus.ACTIVE);
    long deactivatedTutors = accountRepository.countByRoleAndStatus(AccountRole.TUTOR, AccountStatus.DEACTIVATED);

    return new AdminUserStatsResponse(
        totalUsers,
        totalStudents,
        totalTutors,
        totalParents,
        totalActive,
        totalDeactivated,
        activeStudents,
        deactivatedStudents,
        activeParents,
        deactivatedParents,
        activeTutors,
        deactivatedTutors
    );
  }
}

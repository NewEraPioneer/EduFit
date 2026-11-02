package vn.edufit.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.infra.persistence.Account;
import vn.edufit.iam.infra.persistence.AccountRepository;
import vn.edufit.iam.web.dto.AdminUserItemResponse;
import vn.edufit.iam.web.dto.AdminUserListResponse;
import vn.edufit.iam.web.dto.AdminUserStatsResponse;
import vn.edufit.shared.exception.EntityNotFoundException;
import vn.edufit.shared.exception.InvalidOperationException;

@DisplayName("Kiểm thử AdminUserService - Quản trị người dùng")
class AdminUserServiceTest {

  private AccountRepository accountRepository;
  private AdminUserService adminUserService;

  @BeforeEach
  void setUp() {
    accountRepository = mock(AccountRepository.class);
    adminUserService = new AdminUserService(accountRepository);
  }

  @Test
  @DisplayName("Lấy danh sách người dùng có phân trang và tìm kiếm")
  void getUsers_returnsPagedUsers() {
    Account student = Account.createOAuthAccount("student@edufit.vn", "Student Test", AccountRole.STUDENT, null);
    Account tutor = Account.createOAuthAccount("tutor@edufit.vn", "Tutor Test", AccountRole.TUTOR, null);

    when(accountRepository.searchAccounts(eq(AccountRole.STUDENT), eq(AccountStatus.ACTIVE), eq("student"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(student)));

    AdminUserListResponse result = adminUserService.getUsers(AccountRole.STUDENT, AccountStatus.ACTIVE, "student", 0, 10);

    assertThat(result.users()).hasSize(1);
    assertThat(result.users().getFirst().email()).isEqualTo("student@edufit.vn");
    assertThat(result.totalElements()).isEqualTo(1);
  }

  @Test
  @DisplayName("Khóa tài khoản người dùng thành công")
  void lockUser_success() {
    UUID targetUserId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    Account account = Account.of(targetUserId, "target@edufit.vn", "hash", "Target User", AccountRole.STUDENT, AccountStatus.ACTIVE);

    when(accountRepository.findById(targetUserId)).thenReturn(Optional.of(account));
    when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

    AdminUserItemResponse res = adminUserService.lockUser(targetUserId, adminId, "Vi phạm quy chế");

    assertThat(res.status()).isEqualTo(AccountStatus.DEACTIVATED);
    assertThat(account.getStatus()).isEqualTo(AccountStatus.DEACTIVATED);
    verify(accountRepository).save(account);
  }

  @Test
  @DisplayName("Mở khóa tài khoản người dùng thành công")
  void unlockUser_success() {
    UUID targetUserId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    Account account = Account.of(targetUserId, "target@edufit.vn", "hash", "Target User", AccountRole.STUDENT, AccountStatus.DEACTIVATED);

    when(accountRepository.findById(targetUserId)).thenReturn(Optional.of(account));
    when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

    AdminUserItemResponse res = adminUserService.unlockUser(targetUserId, adminId);

    assertThat(res.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    verify(accountRepository).save(account);
  }

  @Test
  @DisplayName("Không cho phép Quản trị viên tự khóa tài khoản của chính mình")
  void adminLockSelf_throwsException() {
    UUID adminId = UUID.randomUUID();
    Account adminAccount = Account.of(
        adminId,
        "admin@edufit.vn",
        "hash",
        "Admin",
        AccountRole.ADMIN,
        AccountStatus.ACTIVE
    );

    when(accountRepository.findById(adminId)).thenReturn(Optional.of(adminAccount));

    assertThatThrownBy(() -> adminUserService.lockUser(adminId, adminId, "Tự khóa"))
        .isInstanceOf(InvalidOperationException.class)
        .hasMessageContaining("Bạn không thể tự vô hiệu hóa tài khoản Quản trị viên của chính mình");
  }

  @Test
  @DisplayName("Ném lỗi khi không tìm thấy người dùng")
  void userNotFound_throwsException() {
    UUID targetId = UUID.randomUUID();
    when(accountRepository.findById(targetId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adminUserService.getUserById(targetId))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  @DisplayName("Lấy thống kê người dùng hệ thống")
  void getUserStats_success() {
    when(accountRepository.count()).thenReturn(100L);
    when(accountRepository.countByRole(AccountRole.STUDENT)).thenReturn(60L);
    when(accountRepository.countByRole(AccountRole.TUTOR)).thenReturn(30L);
    when(accountRepository.countByRole(AccountRole.PARENT)).thenReturn(10L);
    when(accountRepository.countByStatus(AccountStatus.ACTIVE)).thenReturn(95L);
    when(accountRepository.countByStatus(AccountStatus.DEACTIVATED)).thenReturn(5L);

    when(accountRepository.countByRoleAndStatus(AccountRole.STUDENT, AccountStatus.ACTIVE)).thenReturn(58L);
    when(accountRepository.countByRoleAndStatus(AccountRole.STUDENT, AccountStatus.DEACTIVATED)).thenReturn(2L);
    when(accountRepository.countByRoleAndStatus(AccountRole.PARENT, AccountStatus.ACTIVE)).thenReturn(9L);
    when(accountRepository.countByRoleAndStatus(AccountRole.PARENT, AccountStatus.DEACTIVATED)).thenReturn(1L);
    when(accountRepository.countByRoleAndStatus(AccountRole.TUTOR, AccountStatus.ACTIVE)).thenReturn(28L);
    when(accountRepository.countByRoleAndStatus(AccountRole.TUTOR, AccountStatus.DEACTIVATED)).thenReturn(2L);

    AdminUserStatsResponse stats = adminUserService.getUserStats();

    assertThat(stats.totalUsers()).isEqualTo(100L);
    assertThat(stats.totalStudents()).isEqualTo(60L);
    assertThat(stats.totalTutors()).isEqualTo(30L);
    assertThat(stats.totalParents()).isEqualTo(10L);
    assertThat(stats.totalActive()).isEqualTo(95L);
    assertThat(stats.totalDeactivated()).isEqualTo(5L);
    assertThat(stats.activeStudents()).isEqualTo(58L);
    assertThat(stats.deactivatedStudents()).isEqualTo(2L);
    assertThat(stats.activeParents()).isEqualTo(9L);
    assertThat(stats.deactivatedParents()).isEqualTo(1L);
    assertThat(stats.activeTutors()).isEqualTo(28L);
    assertThat(stats.deactivatedTutors()).isEqualTo(2L);
  }
}

package vn.edufit.iam.infra.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;

/**
 * Spring Data JPA Repository quản lý truy xuất bảng {@code accounts}.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

  /**
   * Tìm tài khoản theo email (đã chuẩn hóa lowercase).
   */
  Optional<Account> findByEmail(String email);

  /**
   * Kiểm tra sự tồn tại của email trong hệ thống (BR-01).
   */
  boolean existsByEmail(String email);

  /**
   * Tìm kiếm và phân trang người dùng cho Quản trị viên (Admin User Management).
   */
  @Query("SELECT a FROM Account a WHERE "
      + "(:role IS NULL OR a.role = :role) AND "
      + "(:status IS NULL OR a.status = :status) AND "
      + "(:search IS NULL OR :search = '' OR LOWER(a.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(a.email) LIKE LOWER(CONCAT('%', :search, '%')))")
  Page<Account> searchAccounts(
      @Param("role") AccountRole role,
      @Param("status") AccountStatus status,
      @Param("search") String search,
      Pageable pageable
  );

  /**
   * Đếm số lượng tài khoản theo vai trò (Role).
   */
  long countByRole(AccountRole role);

  long countByStatus(AccountStatus status);

  long countByRoleAndStatus(AccountRole role, AccountStatus status);

  @Query("select a.id from Account a where a.role = vn.edufit.iam.api.AccountRole.ADMIN"
      + " and a.status = vn.edufit.iam.api.AccountStatus.ACTIVE")
  java.util.List<UUID> findActiveAdminIds();
}

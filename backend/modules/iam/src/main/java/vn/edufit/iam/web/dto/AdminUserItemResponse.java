package vn.edufit.iam.web.dto;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.infra.persistence.Account;

/**
 * Thông tin chi tiết một tài khoản người dùng phục vụ trang Quản trị Admin.
 */
public record AdminUserItemResponse(
    UUID id,
    String email,
    String fullName,
    AccountRole role,
    AccountStatus status,
    String avatarUrl,
    Instant createdAt,
    Instant lastLoginAt
) {
  public static AdminUserItemResponse from(Account a) {
    return new AdminUserItemResponse(
        a.getId(),
        a.getEmail(),
        a.getFullName(),
        a.getRole(),
        a.getStatus(),
        a.getAvatarUrl(),
        a.getCreatedAt(),
        a.getLastLoginAt()
    );
  }
}

package vn.edufit.iam.web.dto;

import java.util.UUID;
import vn.edufit.iam.api.AccountRole;
import vn.edufit.iam.api.AccountStatus;
import vn.edufit.iam.infra.persistence.Account;

/**
 * Payload thông tin tài khoản an toàn trả về cho client sau khi xác thực/đăng ký.
 */
public record AuthUserResponse(
    UUID accountId,
    String email,
    String fullName,
    AccountRole role,
    AccountStatus status,
    String avatarUrl
) {

  public AuthUserResponse(UUID accountId, String email, String fullName, AccountRole role, AccountStatus status) {
    this(accountId, email, fullName, role, status, null);
  }

  public static AuthUserResponse from(Account account) {
    return new AuthUserResponse(
        account.getId(),
        account.getEmail(),
        account.getFullName(),
        account.getRole(),
        account.getStatus(),
        account.getAvatarUrl()
    );
  }
}

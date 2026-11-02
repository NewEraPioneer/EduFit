package vn.edufit.iam.application;

import vn.edufit.iam.infra.persistence.Account;
import vn.edufit.iam.web.dto.OAuthLoginResult;

/**
 * Kết quả xử lý xác thực nội bộ OAuth2 chứa Account (để thiết lập session) và DTO phản hồi.
 */
public record OAuthProcessOutcome(
    Account account,
    OAuthLoginResult result
) {
  public static OAuthProcessOutcome authenticated(Account account, OAuthLoginResult result) {
    return new OAuthProcessOutcome(account, result);
  }

  public static OAuthProcessOutcome requiresRole(OAuthLoginResult result) {
    return new OAuthProcessOutcome(null, result);
  }

  public boolean isAuthenticated() {
    return account != null;
  }
}

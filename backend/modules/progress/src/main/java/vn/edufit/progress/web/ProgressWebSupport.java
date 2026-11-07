package vn.edufit.progress.web;

import org.springframework.beans.factory.ObjectProvider;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.InvalidOperationException;

/** Tiện ích dùng chung cho các controller của module Progress. */
final class ProgressWebSupport {

  private ProgressWebSupport() {}

  /** Người dùng đã xác thực của request hiện tại. */
  static CurrentUser currentUser(ObjectProvider<CurrentUser> provider) {
    CurrentUser user = provider.getIfAvailable();
    if (user == null || user.getUserId() == null) {
      throw new InvalidOperationException(ErrorCode.UNAUTHORIZED, "Phiên làm việc chưa được xác thực hoặc đã hết hạn");
    }
    return user;
  }
}

package vn.edufit.iam.application.port;

/**
 * Cổng thu hồi phiên đăng nhập (Spring Session) của một tài khoản.
 *
 * <p>Dùng sau khi đặt lại mật khẩu để mọi thiết bị đang đăng nhập bằng mật khẩu cũ bị đăng xuất.
 */
public interface SessionRevoker {

  /**
   * Thu hồi toàn bộ phiên đang hoạt động của tài khoản.
   *
   * @param principalName tên principal của phiên (email đã chuẩn hóa, xem {@code EduFitUserDetails#getUsername()})
   * @return số phiên đã thu hồi
   */
  int revokeAllSessions(String principalName);
}

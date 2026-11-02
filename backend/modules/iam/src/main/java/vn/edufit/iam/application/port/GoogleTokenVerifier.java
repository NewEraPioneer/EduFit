package vn.edufit.iam.application.port;

import vn.edufit.iam.application.OAuthUserInfo;

/**
 * Cổng xác thực tính hợp lệ và chữ ký số mật mã học của Google ID Token (Ports & Adapters).
 *
 * <p>Mục đích:
 * <ul>
 *   <li>Tách biệt nghiệp vụ đăng nhập cốt lõi khỏi thư viện Google SDK và hạ tầng mạng bên ngoài.</li>
 *   <li>Cho phép giả lập (mock/stub) dễ dàng trong các bài kiểm thử tự động mà không cần mạng Internet.</li>
 * </ul>
 */
public interface GoogleTokenVerifier {

  /**
   * Xác thực chữ ký số RSA, nhà phát hành, thời hạn và audience của Google ID Token.
   *
   * @param idToken chuỗi JWT ID Token thô nhận từ Google Identity Services
   * @return {@link OAuthUserInfo} chứa thông tin người dùng được chứng thực nếu token hợp lệ
   */
  OAuthUserInfo verify(String idToken);
}

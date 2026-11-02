package vn.edufit.iam.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import vn.edufit.iam.api.dto.UserSummaryView;

/**
 * Bề mặt công khai (Facade) của module IAM dành cho các module khác trong hệ thống.
 *
 * <p>Theo quy tắc kiến trúc ADR-002: Các module bên ngoài (ví dụ {@code connection}, {@code scheduling})
 * CHỈ được phép tiêm phụ thuộc (Inject) và gọi vào {@link IamFacade}, tuyệt đối không truy cập trực tiếp
 * vào tầng nội bộ (Entity, Repository, Service) của IAM.
 */
public interface IamFacade {

  /**
   * Tìm kiếm thông tin tóm tắt an toàn của người dùng theo ID.
   *
   * @param id mã định danh tài khoản
   * @return {@link Optional} chứa {@link UserSummaryView} nếu tìm thấy
   */
  Optional<UserSummaryView> findUserSummaryById(UUID id);

  Optional<UserSummaryView> findUserSummaryByEmail(String email);

  /**
   * Tìm kiếm thông tin tóm tắt an toàn của nhiều người dùng theo danh sách ID (batch).
   *
   * @param ids danh sách mã định danh tài khoản
   * @return {@link Map} ánh xạ từ UUID tài khoản sang {@link UserSummaryView}
   */
  Map<UUID, UserSummaryView> findUserSummariesByIds(List<UUID> ids);

  /**
   * Kiểm tra sự tồn tại của người dùng trong hệ thống theo ID.
   *
   * @param id mã định danh tài khoản
   * @return true nếu tài khoản tồn tại
   */
  boolean existsById(UUID id);

  /**
   * Đếm tổng số lượng tài khoản trong hệ thống.
   *
   * @return tổng số tài khoản
   */
  long countTotalUsers();

  /**
   * Đếm số lượng tài khoản theo vai trò cụ thể.
   *
   * @param role vai trò tài khoản
   * @return số lượng tài khoản có vai trò tương ứng
   */
  long countUsersByRole(AccountRole role);

  /** Active administrative recipients for internal case notifications; no credentials exposed. */
  List<UUID> findActiveAdminIds();
}

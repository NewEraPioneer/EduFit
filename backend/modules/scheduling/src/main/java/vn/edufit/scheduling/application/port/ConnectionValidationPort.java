package vn.edufit.scheduling.application.port;

import java.util.UUID;

/**
 * Outbound Port xác thực quan hệ kết nối / lớp học giữa Gia sư và Học sinh.
 *
 * <p>Theo nguyên lý Ports & Adapters (Hexagonal Architecture):
 * Tầng Application của {@code scheduling} định nghĩa interface này để kiểm tra xem
 * {@code classId} (lớp học dạy kèm) có đang ở trạng thái ACTIVE và hợp lệ giữa {@code tutorId} và {@code studentId} hay không.
 *
 * <p>Việc thực thi cụ thể do Outbound Adapter đảm nhiệm, có thể gọi sang {@code ConnectionFacade}
 * hoặc sử dụng Mock Adapter khi module kết nối đang phát triển độc lập.
 */
public interface ConnectionValidationPort {

  /**
   * Kiểm tra xem mối quan hệ kết nối lớp học giữa Gia sư và Học sinh có đang hoạt động (ACTIVE) không.
   *
   * @param classId   Định danh lớp học / hợp đồng dạy kèm (tương đương Engagement ID).
   * @param tutorId   Định danh tài khoản Gia sư.
   * @param studentId Định danh tài khoản Học sinh.
   * @return {@code true} nếu quan hệ hợp lệ và được phép tạo lịch học; ngược lại {@code false}.
   */
  boolean isConnectionActive(UUID classId, UUID tutorId, UUID studentId);

  /**
   * Kiểm tra xem lớp học có đang ở trạng thái hoạt động (ACTIVE) hay không.
   */
  boolean isClassActive(UUID classId);

  /**
   * Lấy thông tin lớp học đang hoạt động (ACTIVE) bao gồm studentId và tutorId.
   */
  java.util.Optional<ActiveClassInfo> findActiveClassById(UUID classId);

  record ActiveClassInfo(UUID classId, UUID studentId, UUID tutorId, UUID needId) {}
}

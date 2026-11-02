package vn.edufit.scheduling.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound Port đồng bộ biên bản buổi học và tiến độ (Session Note) sang module {@code progress}.
 *
 * <p>Theo SRS UC4.6 và ADR-002:
 * Khi Gia sư hoàn thành việc ghi nhận kết quả buổi học (COMPLETED), nếu có nhập biên bản/ghi chú buổi học,
 * hệ thống sẽ chuyển tiếp thông tin này sang phân hệ quản lý Tiến độ học tập.
 */
public interface ProgressSyncPort {

  /**
   * Đồng bộ biên bản buổi học sang phân hệ tiến độ học tập.
   *
   * @param sessionId   Định danh buổi học.
   * @param classId     Định danh lớp học.
   * @param content     Nội dung ghi chú / nhận xét của Gia sư.
   * @param recordedAt  Thời điểm ghi nhận.
   */
  void recordSessionNote(UUID sessionId, UUID classId, String content, Instant recordedAt);
}

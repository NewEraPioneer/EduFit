package vn.edufit.scheduling.domain.model;

/**
 * Danh sách 8 trạng thái hữu hạn trong vòng đời của một buổi học (Tutoring Session).
 *
 * <p>Căn cứ kỹ thuật & nghiệp vụ:
 * <ul>
 *   <li><b>SRS EduFit & Đặc tả DB (V1.05):</b> Bảng {@code tutoring_session} ràng buộc kiểm tra
 *       {@code chk_session_status CHECK (status IN ('PROPOSED', 'SCHEDULED', 'COMPLETED', 'ABSENT',
 *       'CANCELLED', 'REJECTED', 'WITHDRAWN', 'EXPIRED'))}.</li>
 *   <li><b>Quy tắc BR-41 đến BR-48:</b> Máy trạng thái quản lý các bước tương tác giữa Gia sư và Học sinh.</li>
 *   <li><b>Ràng buộc loại trừ (Exclusion Constraint):</b> Chỉ có các buổi học ở trạng thái {@link #SCHEDULED}
 *       mới bị áp dụng PostgreSQL GiST Exclusion Constraint chống trùng lịch (NFR-17).</li>
 * </ul>
 */
public enum SessionStatus {

  /** Buổi học được đề xuất bởi một bên (Học sinh hoặc Gia sư), đang chờ đối tác phản hồi. */
  PROPOSED,

  /** Buổi học đã được đối tác chấp nhận và chính thức xếp lịch (áp dụng khóa chống trùng lịch GiST). */
  SCHEDULED,

  /** Buổi học đã diễn ra thành công và được Gia sư ghi nhận kết quả / biên bản (UC4.6, BR-47). */
  COMPLETED,

  /** Buổi học đã không diễn ra do một hoặc cả hai bên vắng mặt (No-Show). */
  ABSENT,

  /** Buổi học đã chính thức bị hủy trước giờ học bởi một bên (UC4.5, BR-45, BR-46). */
  CANCELLED,

  /** Đề xuất buổi học bị đối tác từ chối (UC4.3). */
  REJECTED,

  /** Người đề xuất tự rút lại đề xuất của mình trước khi đối tác phản hồi. */
  WITHDRAWN,

  /** Đề xuất buổi học đã quá hạn phản hồi (sau 48h hoặc chạm mốc giờ học) và bị hủy tự động (FR-17, BR-43). */
  EXPIRED;

  /**
   * Kiểm tra xem trạng thái hiện tại có phải là trạng thái kết thúc (Terminal State) hay không.
   *
   * <p>Một khi phiên học rơi vào trạng thái kết thúc, tuyệt đối KHÔNG cho phép chuyển đổi sang bất kỳ
   * trạng thái nào khác (tính bất biến của trạng thái cuối).
   *
   * @return {@code true} nếu là trạng thái kết thúc, ngược lại {@code false}.
   */
  public boolean isTerminal() {
    return this == COMPLETED
        || this == ABSENT
        || this == CANCELLED
        || this == REJECTED
        || this == WITHDRAWN
        || this == EXPIRED;
  }
}

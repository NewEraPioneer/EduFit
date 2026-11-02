package vn.edufit.scheduling.domain.model;

/**
 * Hình thức tổ chức buổi học (Dạy kèm trực tuyến hoặc trực tiếp).
 *
 * <p>Khớp với ràng buộc DB {@code chk_session_mode CHECK (mode IN ('ONLINE', 'OFFLINE'))}.
 */
public enum SessionMode {

  /** Dạy trực tuyến qua phòng học ảo (Google Meet, Zoom, MS Teams...). */
  ONLINE,

  /** Dạy trực tiếp tại địa điểm thỏa thuận (nhà học sinh, quán cà phê, thư viện...). */
  OFFLINE
}

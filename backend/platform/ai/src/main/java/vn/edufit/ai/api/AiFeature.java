package vn.edufit.ai.api;

/**
 * Danh mục các tính năng có sử dụng dịch vụ AI trong nền tảng EduFit.
 *
 * <p>Mục đích thiết kế và liên kết cơ sở dữ liệu:
 * <ul>
 *   <li><b>Ràng buộc toàn vẹn CSDL:</b> Khớp chính xác 100% với CHECK CONSTRAINT
 *       {@code chk_ai_log_feature} của bảng {@code ai_request_log}
 *       được định nghĩa trong migration {@code V1.08__create_audit_and_ai_log_tables.sql}.</li>
 *   <li><b>Phân tách nghiệp vụ:</b> Giúp hệ thống ghi log kiểm toán (FR-05)
 *       và phân loại hạn mức sử dụng (BR-10, BR-25) độc lập cho từng use case.</li>
 * </ul>
 */
public enum AiFeature {

  /**
   * Tính năng gợi ý tạo Bio chuyên nghiệp cho Gia sư (UC1.7).
   * <p>Áp dụng giới hạn hạn ngạch tối đa 5 lần/phiên sửa hồ sơ theo BR-10.
   */
  TUTOR_BIO,

  /**
   * Tính năng giải thích lý do đề xuất gia sư Top 1 trong kết quả Matching (UC2.4).
   * <p>Áp dụng nguyên tắc chỉ dùng sự thật có sẵn theo BR-24 và giới hạn theo BR-25.
   */
  MATCH_EXPLANATION,

  /**
   * Bản nháp lộ trình học tập (3–8 cột mốc) cho gia sư lập lộ trình của lớp (Progress, Giai đoạn 3).
   * <p>Chỉ gửi môn, khối, mục tiêu và danh mục chủ đề — không có dữ liệu cá nhân. Giới hạn 10 lần/giờ/gia sư.
   */
  LEARNING_PLAN_DRAFT
}

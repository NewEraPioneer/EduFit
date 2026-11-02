package vn.edufit.shared.exception;

/**
 * Bảng mã định danh lỗi nghiệp vụ chuẩn (Error Code) cho toàn bộ hệ thống EduFit.
 *
 * <p>Mục đích thiết kế:
 * <ul>
 *   <li><b>Gắn liền với Ngoại lệ (Exceptions):</b> Mỗi lỗi phát sinh đều mang một mã {@code ErrorCode} duy nhất.</li>
 *   <li><b>Độc lập với HTTP:</b> Mã lỗi này đại diện cho sự vi phạm quy tắc nghiệp vụ (Business Rule) hoặc trạng thái dữ liệu,
 *       không phải mã trạng thái HTTP (như 400, 404). Việc ánh xạ từ mã lỗi sang mã HTTP là nhiệm vụ của tầng {@code app}.</li>
 *   <li><b>Chuẩn hóa cho Client (Next.js):</b> Giúp Frontend có thể bắt đúng mã lỗi để hiển thị thông báo đa ngôn ngữ (i18n)
 *       hoặc thực hiện rẽ nhánh giao diện mà không phải phân tích chuỗi văn bản (String message).</li>
 * </ul>
 */
public enum ErrorCode {

  // =========================================================================
  // 1. CÁC MÃ LỖI CHUNG (HỆ THỐNG & TRẠNG THÁI CĂN BẢN)
  // =========================================================================

  /** Lỗi hệ thống không lường trước được (lỗi máy chủ, mất kết nối cơ sở dữ liệu...). */
  INTERNAL_SERVER_ERROR,

  /** Chưa xác thực: Người dùng chưa đăng nhập hoặc phiên làm việc đã hết hạn. */
  UNAUTHORIZED,

  /** Không có quyền truy cập: Người dùng đã đăng nhập nhưng không đủ quyền thực hiện hành động này. */
  FORBIDDEN,

  /** Dữ liệu đầu vào không hợp lệ (vi phạm ràng buộc validation như email sai định dạng, chuỗi rỗng...). */
  VALIDATION_FAILED,

  /** Tài nguyên yêu cầu không tồn tại trong hệ thống. */
  RESOURCE_NOT_FOUND,

  /** Xung đột tài nguyên (dữ liệu đã tồn tại hoặc trạng thái bị mâu thuẫn). */
  CONFLICT_DETECTED,

  /** Thao tác không hợp lệ với trạng thái hiện tại của thực thể. */
  INVALID_OPERATION,

  // =========================================================================
  // 2. CÁC MÃ LỖI NGHIỆP VỤ ĐẶC THÙ THEO MODULE (SRS EDUFIT)
  // =========================================================================

  // --- Module IAM (Xác thực & Tài khoản - UC1.1 đến UC1.4) ---
  /** Email này đã được đăng ký bởi tài khoản khác trong hệ thống. */
  USER_ALREADY_EXISTS,

  /** Thông tin đăng nhập không chính xác (sai email hoặc mật khẩu). */
  INVALID_CREDENTIALS,

  /** Tài khoản đã bị vô hiệu hóa (DEACTIVATED). */
  USER_NOT_ACTIVE,

  /** Tài khoản tạm thời bị khóa do vượt quá số lần đăng nhập sai (khóa 15 phút sau 5 lần). */
  ACCOUNT_LOCKED,

  /** Token xác thực hoặc reset mật khẩu không hợp lệ, không tồn tại hoặc sai định dạng. */
  INVALID_TOKEN,

  /** Token xác thực hoặc reset mật khẩu đã quá hạn hiệu lực. */
  TOKEN_EXPIRED,

  /** Token xác thực hoặc reset mật khẩu đã được sử dụng trước đó. */
  TOKEN_ALREADY_USED,

  /** Vượt quá tần suất yêu cầu cho phép (Rate limit / Cooldown). */
  RATE_LIMIT_EXCEEDED,

  /** Mật khẩu hiện tại cung cấp không chính xác khi thực hiện đổi mật khẩu. */
  INVALID_CURRENT_PASSWORD,

  /** Mật khẩu mới không được trùng với mật khẩu hiện tại. */
  PASSWORD_REUSE_FORBIDDEN,

  // --- Module Verification (Duyệt bằng cấp Gia sư - UC1.10, UC1.12) ---
  /** Gia sư chưa nộp đủ bằng cấp để yêu cầu duyệt hồ sơ. */
  CREDENTIAL_NOT_SUBMITTED,

  /** Định dạng tệp bằng cấp tải lên không hợp lệ (vi phạm NFR-08 Magic Bytes). */
  INVALID_FILE_TYPE,

  // --- Module Scheduling (Lịch học - UC4.1 đến UC4.6 / BR-42) ---
  /** Khung giờ đề xuất bị xung đột/trùng lịch với lịch dạy hoặc lịch học đã có (BR-42, NFR-17). */
  SCHEDULE_OVERLAP,

  // --- Module Connection (Liên kết & Ghép nối - Milestone 1) ---
  /** Khung giờ đề xuất bị trùng với lịch lớp học hoặc buổi học đã chốt của gia sư. */
  SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE,

  /** Đề xuất buổi học đã quá hạn phản hồi và không còn hiệu lực (FR-17, BR-41). */
  PROPOSAL_EXPIRED,

  /** Buổi học đã kết thúc hoặc đã hủy, không thể thay đổi trạng thái (BR-40). */
  SESSION_ALREADY_FINALIZED,

  // --- Module Review (Đánh giá gia sư - UC6.1 / NFR-24) ---
  /** Học sinh đã đánh giá gia sư này cho khóa học hiện tại rồi, không được đánh giá lại (NFR-24). */
  REVIEW_ALREADY_EXISTS,

  /** Chưa hoàn thành đủ số buổi học quy định để được đánh giá gia sư (UC6.1). */
  NOT_ENOUGH_SESSIONS,

  /** Chỉ tác giả tạo đánh giá mới có quyền chỉnh sửa đánh giá này (BR-57). */
  ONLY_AUTHOR_CAN_EDIT,

  /** Điểm đánh giá (rating) không hợp lệ, phải là số nguyên từ 1 đến 5 (BR-58). */
  INVALID_RATING,

  // --- Module AI Platform (Cổng gọi LLM - FR-05, NFR-10) ---
  /** Dịch vụ AI bên ngoài không khả dụng hoặc trả về lỗi kết nối. */
  AI_SERVICE_UNAVAILABLE,

  /** Dịch vụ AI quá hạn thời gian phản hồi (vượt quá 15 giây theo NFR-10). */
  AI_REQUEST_TIMEOUT,

  // --- Module Progress (Kế hoạch học tập & Tiến độ - UC5.1 đến UC5.5) ---
  /** Xung đột phiên bản kế hoạch học tập (Optimistic Locking Conflict). */
  PLAN_VERSION_CONFLICT
}

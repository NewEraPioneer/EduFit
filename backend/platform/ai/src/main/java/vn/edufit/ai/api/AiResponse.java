package vn.edufit.ai.api;

import java.util.Objects;

/**
 * Đối tượng kết quả trả về (Value Object / Record) từ AI Gateway sau khi hoàn tất xử lý.
 *
 * <p>Mục đích thiết kế:
 *
 * <ul>
 *   <li><b>Đóng gói kết quả hoàn chỉnh:</b> Chứa chuỗi văn bản đã được làm sạch qua {@code
 *       OutputSanitizer} (cắt tỉa khoảng trắng, kiểm tra rỗng, kiểm tra giới hạn độ dài).
 *   <li><b>Đo lường hiệu năng kỹ thuật:</b> Cung cấp thời gian thực thi (latency tính bằng
 *       mili-giây) giúp tầng gọi giám sát được độ phản hồi của nhà cung cấp LLM.
 *   <li><b>Theo dõi chi phí và hạn ngạch:</b> Cung cấp số lượng token tiêu thụ (nếu LLM Provider có
 *       trả về thông số thống kê trong {@code usageMetadata}) để phục vụ theo dõi chi phí mà vẫn
 *       tuân thủ FR-05 (vì không lưu nội dung prompt vào DB).
 * </ul>
 *
 * @param text Chuỗi văn bản kết quả đã được làm sạch và kiểm duyệt tính hợp lệ.
 * @param latencyMs Tổng thời gian xử lý yêu cầu tính bằng mili-giây (latency).
 * @param tokenCount Tổng số lượng token tiêu thụ (có thể null nếu Provider không hỗ trợ).
 * @param generatedByModel false cho stub; caller dùng facts/fallback thay vì hiển thị nội dung giả
 *     lập.
 */
public record AiResponse(String text, int latencyMs, Integer tokenCount, boolean generatedByModel) {

  /** Compact constructor kiểm tra tính hợp lệ cơ bản. */
  public AiResponse {
    Objects.requireNonNull(text, "text kết quả không được phép null");
    if (latencyMs < 0) {
      throw new IllegalArgumentException("latencyMs không được phép là số âm");
    }
  }
}

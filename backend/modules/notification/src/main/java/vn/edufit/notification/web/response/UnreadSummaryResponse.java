package vn.edufit.notification.web.response;

import java.util.Map;

/**
 * Phản hồi tóm tắt số lượng thông báo chưa đọc theo danh mục (phục vụ huy hiệu và chấm sidebar).
 */
public record UnreadSummaryResponse(
    long total,
    Map<String, Long> byCategory
) {}

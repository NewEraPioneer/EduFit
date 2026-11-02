package vn.edufit.notification.web.response;

import java.util.UUID;

/**
 * DTO phản hồi sau khi đánh dấu một thông báo đã đọc.
 */
public record MarkReadResponse(
    UUID notificationId,
    boolean isRead
) {}

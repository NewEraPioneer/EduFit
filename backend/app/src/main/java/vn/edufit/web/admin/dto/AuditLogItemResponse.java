package vn.edufit.web.admin.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Type-safe DTO chi tiết bản ghi nhật ký kiểm toán.
 */
public record AuditLogItemResponse(
    UUID id,
    UUID actorUserId,
    String action,
    String targetType,
    UUID targetId,
    String reason,
    Map<String, String> metadata,
    Instant createdAt
) {}

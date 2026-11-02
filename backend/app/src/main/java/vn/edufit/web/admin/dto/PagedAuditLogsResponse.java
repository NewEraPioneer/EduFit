package vn.edufit.web.admin.dto;

import java.util.List;

/**
 * Type-safe DTO phân trang danh sách nhật ký kiểm toán.
 */
public record PagedAuditLogsResponse(
    List<AuditLogItemResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}

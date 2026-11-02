package vn.edufit.iam.web.dto;

import java.util.List;

/**
 * Danh sách người dùng có phân trang phục vụ quản trị Admin.
 */
public record AdminUserListResponse(
    List<AdminUserItemResponse> users,
    long totalElements,
    int totalPages,
    int page,
    int size
) {
}

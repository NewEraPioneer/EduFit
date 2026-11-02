package vn.edufit.profile.web.response;

import java.time.Instant;

/**
 * Type-safe DTO phản hồi thông tin môn học dành cho Quản trị viên (Admin Catalog).
 */
public record SubjectAdminResponse(
    Integer id,
    String name,
    String code,
    String slug,
    String category,
    String description,
    boolean isActive,
    Instant createdAt
) {}

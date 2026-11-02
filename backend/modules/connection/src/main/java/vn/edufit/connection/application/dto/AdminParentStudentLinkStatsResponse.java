package vn.edufit.connection.application.dto;

/**
 * Thống kê các chỉ số liên kết Phụ huynh - Học sinh dành cho Quản trị viên.
 */
public record AdminParentStudentLinkStatsResponse(
    long totalLinks,
    long activeLinks,
    long revokedLinks,
    long uniqueParents,
    long uniqueStudents
) {}

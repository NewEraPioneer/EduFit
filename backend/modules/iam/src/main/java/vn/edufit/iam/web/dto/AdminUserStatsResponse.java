package vn.edufit.iam.web.dto;

/**
 * Dữ liệu thống kê tổng quan về người dùng cho Dashboard Quản trị viên.
 */
public record AdminUserStatsResponse(
    long totalUsers,
    long totalStudents,
    long totalTutors,
    long totalParents,
    long totalActive,
    long totalDeactivated,
    long activeStudents,
    long deactivatedStudents,
    long activeParents,
    long deactivatedParents,
    long activeTutors,
    long deactivatedTutors
) {
}

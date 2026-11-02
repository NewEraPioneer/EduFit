package vn.edufit.web.admin.dto;

/**
 * Type-safe DTO phản hồi số liệu thống kê thời gian thực cho Admin Dashboard.
 *
 * <p>Triệt tiêu hoàn toàn số liệu mock, nạp 100% từ cơ sở dữ liệu thật qua các facade hệ thống.
 */
public record AdminDashboardStatsResponse(
    long totalUsers,
    long totalTutors,
    long totalStudents,
    long totalParents,
    long activeClasses,
    long completedSessions,
    long pendingKyc
) {}

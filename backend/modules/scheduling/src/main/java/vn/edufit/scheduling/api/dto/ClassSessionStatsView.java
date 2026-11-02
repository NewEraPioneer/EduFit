package vn.edufit.scheduling.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO tổng hợp số liệu thống kê các buổi học của một lớp học (Dùng cho Progress Dashboard).
 *
 * @param classId          Mã lớp học.
 * @param completedCount   Số buổi đã hoàn thành (COMPLETED).
 * @param absentCount      Số buổi vắng mặt (ABSENT).
 * @param cancelledCount   Số buổi đã hủy (CANCELLED).
 * @param upcomingCount    Số buổi sắp diễn ra (SCHEDULED tương lai).
 * @param nextSessionStart Thời điểm bắt đầu của buổi học tiếp theo gần nhất.
 * @param lastSessionStart Thời điểm bắt đầu của buổi học gần nhất trong quá khứ.
 */
public record ClassSessionStatsView(
    UUID classId,
    int completedCount,
    int absentCount,
    int cancelledCount,
    int upcomingCount,
    Instant nextSessionStart,
    Instant lastSessionStart
) {}

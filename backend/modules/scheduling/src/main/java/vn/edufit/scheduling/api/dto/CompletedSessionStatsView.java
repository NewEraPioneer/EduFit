package vn.edufit.scheduling.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Public DTO chứa số liệu thống kê về các buổi học đã hoàn thành của một lớp học hoặc một cặp Gia sư - Học sinh.
 *
 * <p>Mục đích sử dụng:
 * <ul>
 *   <li><b>Module {@code review} (BR-61):</b> Kiểm tra học sinh đã hoàn thành tối thiểu bao nhiêu buổi
 *       học để mở quyền đánh giá Gia sư (chống đánh giá ảo).</li>
 *   <li><b>Module {@code progress}:</b> Tính toán tổng số buổi học đã hoàn thành để đối chiếu với kế hoạch học tập.</li>
 * </ul>
 *
 * @param classId                Mã lớp học.
 * @param tutorId                Mã gia sư.
 * @param studentId              Mã học sinh.
 * @param totalCompletedSessions Tổng số buổi học ở trạng thái COMPLETED.
 * @param totalAbsentSessions    Tổng số buổi học ở trạng thái ABSENT (vắng mặt).
 * @param lastCompletedAt        Thời điểm hoàn thành buổi học gần nhất (UTC), có thể null nếu chưa có.
 */
public record CompletedSessionStatsView(
    UUID classId,
    UUID tutorId,
    UUID studentId,
    int totalCompletedSessions,
    int totalAbsentSessions,
    Instant lastCompletedAt
) {}

package vn.edufit.scheduling.web.response;

import java.util.List;

/**
 * Response DTO trả về kết quả sau khi phản hồi chấp thuận đề xuất đổi lịch định kỳ (UC4.8).
 */
public record RecurringScheduleResultResponse(
    RecurringProposalResponse proposal,
    int cancelledSessionCount,
    List<SessionResponse> generatedSessions
) {}

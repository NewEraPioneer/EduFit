import { fetchApi, ApiResponse, ApiError } from "@/lib/api";
import type {
  CalendarItem,
  SessionDetail,
  RescheduleProposal,
  ProposeSessionPayload,
  RespondProposalPayload,
  RescheduleSessionPayload,
  RespondReschedulePayload,
  CancelSessionPayload,
  ProposeRecurringPayload,
  RespondRecurringPayload,
  RecurringProposalResponse,
  RecurringScheduleResult,
} from "../types";

export interface GetCalendarParams {
  from: string; // ISO-8601 string
  to: string; // ISO-8601 string
  studentId?: string;
}

/**
 * Kiểm tra xem một đối tượng lỗi có phải là lỗi xung đột 409 CONFLICT (PostgreSQL GiST overlap) hay không.
 */
export function isConflictError(error: unknown): boolean {
  if (error instanceof ApiError) {
    if (error.status === 409) return true;
    if (
      error.code === "SCHEDULE_OVERLAP" ||
      error.code === "CONFLICT_DETECTED" ||
      error.code === "SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE"
    ) {
      return true;
    }
  }
  if (typeof error === "object" && error !== null) {
    const errObj = error as { status?: number; code?: string };
    if (errObj.status === 409) return true;
    if (
      errObj.code === "SCHEDULE_OVERLAP" ||
      errObj.code === "CONFLICT_DETECTED" ||
      errObj.code === "SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE"
    ) {
      return true;
    }
  }
  return false;
}

/**
 * Lấy thông điệp lỗi tiếng Việt thân thiện khi phát sinh xung đột lịch học.
 */
export function getConflictMessage(
  error: unknown,
  fallback = "Khung giờ này đã bị trùng với lịch khác! Vui lòng chọn một khung giờ khác."
): string {
  if (error instanceof ApiError && error.message) {
    return error.message;
  }
  if (typeof error === "object" && error !== null) {
    const message = (error as { message?: string }).message;
    if (message) return message;
  }
  return fallback;
}

export const schedulingApi = {
  /**
   * Tra cứu lịch học theo khoảng thời gian và hồ sơ người dùng.
   * GET /api/v1/scheduling/calendar?from=...&to=...&studentId=...
   */
  async getCalendar(params: GetCalendarParams): Promise<ApiResponse<CalendarItem[]>> {
    const searchParams = new URLSearchParams({
      from: params.from,
      to: params.to,
    });
    if (params.studentId) {
      searchParams.set("studentId", params.studentId);
    }
    return fetchApi<CalendarItem[]>(
      `/api/v1/scheduling/calendar?${searchParams.toString()}`,
      { method: "GET" }
    );
  },

  /**
   * Lấy thông tin chi tiết đầy đủ của một buổi học theo ID.
   * GET /api/v1/scheduling/sessions/{id}
   */
  async getSessionDetail(sessionId: string): Promise<ApiResponse<SessionDetail>> {
    return fetchApi<SessionDetail>(
      `/api/v1/scheduling/sessions/${encodeURIComponent(sessionId)}`,
      { method: "GET" }
    );
  },

  /**
   * Đề xuất buổi học mới (UC4.1).
   * POST /api/v1/scheduling/sessions/propose
   */
  async proposeSession(payload: ProposeSessionPayload): Promise<ApiResponse<SessionDetail>> {
    return fetchApi<SessionDetail>("/api/v1/scheduling/sessions/propose", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  /**
   * Phản hồi đề xuất buổi học: Chấp nhận hoặc Từ chối (UC4.3).
   * POST /api/v1/scheduling/sessions/{id}/respond
   */
  async respondToProposal(
    sessionId: string,
    payload: RespondProposalPayload
  ): Promise<ApiResponse<SessionDetail>> {
    return fetchApi<SessionDetail>(
      `/api/v1/scheduling/sessions/${encodeURIComponent(sessionId)}/respond`,
      {
        method: "POST",
        body: JSON.stringify(payload),
      }
    );
  },

  /**
   * Yêu cầu dời lịch học sang thời gian mới (UC4.4).
   * POST /api/v1/scheduling/sessions/{id}/reschedule
   */
  async rescheduleSession(
    sessionId: string,
    payload: RescheduleSessionPayload
  ): Promise<ApiResponse<RescheduleProposal>> {
    return fetchApi<RescheduleProposal>(
      `/api/v1/scheduling/sessions/${encodeURIComponent(sessionId)}/reschedule`,
      {
        method: "POST",
        body: JSON.stringify(payload),
      }
    );
  },

  /**
   * Phản hồi đề xuất đổi lịch học (UC4.4).
   * POST /api/v1/scheduling/sessions/reschedule-proposals/{proposalId}/respond
   */
  async respondToReschedule(
    proposalId: string,
    payload: RespondReschedulePayload
  ): Promise<ApiResponse<RescheduleProposal>> {
    return fetchApi<RescheduleProposal>(
      `/api/v1/scheduling/sessions/reschedule-proposals/${encodeURIComponent(proposalId)}/respond`,
      {
        method: "POST",
        body: JSON.stringify(payload),
      }
    );
  },

  /**
   * Lấy thông tin đề xuất đổi lịch học đang chờ xử lý của buổi học (UC4.4).
   * GET /api/v1/scheduling/sessions/{id}/reschedule-proposal
   */
  async getRescheduleProposal(
    sessionId: string
  ): Promise<ApiResponse<RescheduleProposal | null>> {
    return fetchApi<RescheduleProposal | null>(
      `/api/v1/scheduling/sessions/${encodeURIComponent(sessionId)}/reschedule-proposal`,
      { method: "GET" }
    );
  },


  /**
   * Hủy buổi học đã lên lịch (UC4.5).
   * POST /api/v1/scheduling/sessions/{id}/cancel
   */
  async cancelSession(
    sessionId: string,
    payload: CancelSessionPayload
  ): Promise<ApiResponse<SessionDetail>> {
    return fetchApi<SessionDetail>(
      `/api/v1/scheduling/sessions/${encodeURIComponent(sessionId)}/cancel`,
      {
        method: "POST",
        body: JSON.stringify(payload),
      }
    );
  },

  /**
   * Đề xuất đổi lịch học định kỳ theo bounded horizon 4-8 tuần (UC4.8).
   * POST /api/v1/scheduling/recurring/propose
   */
  async proposeRecurringSchedule(
    payload: ProposeRecurringPayload
  ): Promise<ApiResponse<RecurringProposalResponse>> {
    return fetchApi<RecurringProposalResponse>("/api/v1/scheduling/recurring/propose", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  /**
   * Phản hồi đề xuất đổi lịch học định kỳ (UC4.8).
   * POST /api/v1/scheduling/recurring/respond
   */
  async respondRecurringSchedule(
    payload: RespondRecurringPayload
  ): Promise<ApiResponse<RecurringScheduleResult>> {
    return fetchApi<RecurringScheduleResult>("/api/v1/scheduling/recurring/respond", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  /**
   * Lấy thông tin đề xuất đổi lịch định kỳ đang chờ xử lý của lớp học (UC4.8).
   * GET /api/v1/scheduling/recurring/classes/{classId}/active-proposal
   */
  async getActiveRecurringProposal(
    classId: string
  ): Promise<ApiResponse<RecurringProposalResponse | null>> {
    return fetchApi<RecurringProposalResponse | null>(
      `/api/v1/scheduling/recurring/classes/${encodeURIComponent(classId)}/active-proposal`,
      { method: "GET" }
    );
  },

  /**
   * Lấy chi tiết đề xuất đổi lịch định kỳ theo ID.
   * GET /api/v1/scheduling/recurring/proposals/{proposalId}
   */
  async getRecurringProposal(
    proposalId: string
  ): Promise<ApiResponse<RecurringProposalResponse>> {
    return fetchApi<RecurringProposalResponse>(
      `/api/v1/scheduling/recurring/proposals/${encodeURIComponent(proposalId)}`,
      { method: "GET" }
    );
  },
};


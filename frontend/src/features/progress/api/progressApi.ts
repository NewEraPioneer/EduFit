import { fetchApi, ApiResponse } from "@/lib/api";
import type {
  StudentDashboardResponse,
  ParentDashboardResponse,
  PlanResponse,
  SessionNoteTimelineResponse,
  TeachingHistoryItem,
  PageResponse,
  CreatePlanPayload,
  UpdatePlanPayload,
  UpdateMilestoneStatusPayload,
  RecordSessionOutcomePayload,
  SubmitSessionFeedbackPayload,
  SessionFeedbackView,
  SelfRatingValue,
  SessionMilestones,
  TutorTodos,
  PlanTemplate,
  SavePlanTemplatePayload,
} from "../types";

export interface GetClassNotesParams {
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface GetTeachingHistoryParams {
  page?: number;
  size?: number;
  studentName?: string;
  status?: string;
  from?: string;
  to?: string;
}

export const progressApi = {
  /**
   * Lấy dữ liệu dashboard tiến độ học tập của học sinh
   * GET /api/v1/progress/dashboard/student/{studentId}
   */
  async getStudentDashboard(
    studentId: string,
    classId?: string
  ): Promise<ApiResponse<StudentDashboardResponse>> {
    const query = classId ? `?${new URLSearchParams({ classId }).toString()}` : "";
    return fetchApi<StudentDashboardResponse>(
      `/api/v1/progress/dashboard/student/${encodeURIComponent(studentId)}${query}`,
      { method: "GET" }
    );
  },

  /**
   * Lấy dữ liệu dashboard tiến độ học tập cho phụ huynh
   * GET /api/v1/progress/dashboard/parent
   */
  async getParentDashboard(): Promise<ApiResponse<ParentDashboardResponse>> {
    return fetchApi<ParentDashboardResponse>(
      "/api/v1/progress/dashboard/parent",
      { method: "GET" }
    );
  },

  /**
   * Lấy kế hoạch học tập theo lớp học (UC5.2)
   * GET /api/v1/progress/plans/{classId}
   */
  async getPlanByClassId(classId: string): Promise<ApiResponse<PlanResponse>> {
    return fetchApi<PlanResponse>(
      `/api/v1/progress/plans/${encodeURIComponent(classId)}`,
      { method: "GET" }
    );
  },

  /**
   * Tạo Kế hoạch học tập mới với danh sách cột mốc có trọng số (UC5.1)
   * POST /api/v1/progress/plans
   */
  async createPlan(payload: CreatePlanPayload): Promise<ApiResponse<PlanResponse>> {
    return fetchApi<PlanResponse>("/api/v1/progress/plans", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  /**
   * Cập nhật Kế hoạch học tập (UC5.3)
   * PUT /api/v1/progress/plans/{planId}
   */
  async updatePlan(planId: string, payload: UpdatePlanPayload): Promise<ApiResponse<PlanResponse>> {
    return fetchApi<PlanResponse>(
      `/api/v1/progress/plans/${encodeURIComponent(planId)}`,
      {
        method: "PUT",
        body: JSON.stringify(payload),
      }
    );
  },

  /**
   * Cập nhật trạng thái một cột mốc học tập (UC5.4)
   * PATCH /api/v1/progress/milestones/{milestoneId}/status
   */
  async updateMilestoneStatus(
    milestoneId: string,
    payload: UpdateMilestoneStatusPayload
  ): Promise<ApiResponse<PlanResponse>> {
    return fetchApi<PlanResponse>(
      `/api/v1/progress/milestones/${encodeURIComponent(milestoneId)}/status`,
      {
        method: "PATCH",
        body: JSON.stringify(payload),
      }
    );
  },

  /**
   * Lấy danh sách ghi chú/biên bản buổi học theo lớp học
   * GET /api/v1/progress/classes/{classId}/notes
   */
  async getClassNotes(
    classId: string,
    params?: GetClassNotesParams
  ): Promise<ApiResponse<PageResponse<SessionNoteTimelineResponse>>> {
    const searchParams = new URLSearchParams();
    if (params?.from) searchParams.set("from", params.from);
    if (params?.to) searchParams.set("to", params.to);
    if (params?.page !== undefined) searchParams.set("page", String(params.page));
    if (params?.size !== undefined) searchParams.set("size", String(params.size));

    const query = searchParams.toString();
    const url = `/api/v1/progress/classes/${encodeURIComponent(classId)}/notes${query ? `?${query}` : ""}`;

    return fetchApi<PageResponse<SessionNoteTimelineResponse>>(url, { method: "GET" });
  },

  /**
   * Lấy lịch sử giảng dạy của gia sư
   * GET /api/v1/progress/teaching-history
   */
  async getTeachingHistory(
    params?: GetTeachingHistoryParams
  ): Promise<ApiResponse<PageResponse<TeachingHistoryItem>>> {
    const searchParams = new URLSearchParams();
    if (params?.page !== undefined) searchParams.set("page", String(params.page));
    if (params?.size !== undefined) searchParams.set("size", String(params.size));
    if (params?.studentName) searchParams.set("studentName", params.studentName);
    if (params?.status) searchParams.set("status", params.status);
    if (params?.from) searchParams.set("from", params.from);
    if (params?.to) searchParams.set("to", params.to);

    const query = searchParams.toString();
    const url = `/api/v1/progress/teaching-history${query ? `?${query}` : ""}`;

    return fetchApi<PageResponse<TeachingHistoryItem>>(url, { method: "GET" });
  },

  /**
   * Ghi nhận biên bản sau buổi học & Điểm danh (UC4.6)
   * POST /api/v1/scheduling/sessions/{id}/outcome
   */
  async recordSessionOutcome(
    sessionId: string,
    payload: RecordSessionOutcomePayload
  ): Promise<ApiResponse<unknown>> {
    return fetchApi<unknown>(
      `/api/v1/scheduling/sessions/${encodeURIComponent(sessionId)}/outcome`,
      {
        method: "POST",
        body: JSON.stringify(payload),
      }
    );
  },

  /**
   * Phản hồi 10 giây sau buổi học (mức tự lập theo cột mốc được dạy, việc về nhà, thái độ)
   * POST /api/v1/progress/sessions/{sessionId}/feedback
   */
  /**
   * Gắn cột mốc dự kiến (tối đa 3) cho buổi học chưa diễn ra; mảng rỗng = bỏ dự kiến
   * PUT /api/v1/progress/sessions/{sessionId}/planned-milestones
   */
  async planSessionMilestones(sessionId: string, milestoneIds: string[]): Promise<ApiResponse<SessionMilestones>> {
    return fetchApi<SessionMilestones>(
      `/api/v1/progress/sessions/${encodeURIComponent(sessionId)}/planned-milestones`,
      { method: "PUT", body: JSON.stringify({ milestoneIds }) }
    );
  },

  /**
   * Cột mốc hiệu lực của các buổi học trên lịch (thực tế thắng dự kiến)
   * GET /api/v1/progress/sessions/milestones?sessionIds=a,b
   */
  async getSessionMilestones(sessionIds: string[]): Promise<ApiResponse<SessionMilestones[]>> {
    const query = new URLSearchParams({ sessionIds: sessionIds.join(",") }).toString();
    return fetchApi<SessionMilestones[]>(`/api/v1/progress/sessions/milestones?${query}`, { method: "GET" });
  },

  /**
   * Mẫu lộ trình: gia sư thấy mẫu hệ thống + mẫu riêng (có classId thì lọc theo môn × khối × mục tiêu của lớp);
   * admin thấy mẫu hệ thống. GET /api/v1/progress/templates
   */
  async listPlanTemplates(classId?: string): Promise<ApiResponse<PlanTemplate[]>> {
    const query = classId ? `?${new URLSearchParams({ classId }).toString()}` : "";
    return fetchApi<PlanTemplate[]>(`/api/v1/progress/templates${query}`, { method: "GET" });
  },

  /** POST /api/v1/progress/templates */
  async createPlanTemplate(payload: SavePlanTemplatePayload): Promise<ApiResponse<PlanTemplate>> {
    return fetchApi<PlanTemplate>("/api/v1/progress/templates", { method: "POST", body: JSON.stringify(payload) });
  },

  /** PUT /api/v1/progress/templates/{templateId} (bắt buộc version) */
  async updatePlanTemplate(templateId: string, payload: SavePlanTemplatePayload): Promise<ApiResponse<PlanTemplate>> {
    return fetchApi<PlanTemplate>(`/api/v1/progress/templates/${encodeURIComponent(templateId)}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },

  /** DELETE /api/v1/progress/templates/{templateId} */
  async deletePlanTemplate(templateId: string): Promise<ApiResponse<null>> {
    return fetchApi<null>(`/api/v1/progress/templates/${encodeURIComponent(templateId)}`, { method: "DELETE" });
  },

  /**
   * Ngăn "Việc cần làm" của gia sư
   * GET /api/v1/progress/dashboard/tutor/todos
   */
  async getTutorTodos(): Promise<ApiResponse<TutorTodos>> {
    return fetchApi<TutorTodos>("/api/v1/progress/dashboard/tutor/todos", { method: "GET" });
  },

  /**
   * Cảm nhận 1 chạm của học sinh sau buổi học (chọn lại thì ghi đè)
   * PUT /api/v1/progress/sessions/{sessionId}/self-rating
   */
  async rateSession(
    sessionId: string,
    rating: SelfRatingValue
  ): Promise<ApiResponse<{ sessionId: string; rating: SelfRatingValue }>> {
    return fetchApi<{ sessionId: string; rating: SelfRatingValue }>(
      `/api/v1/progress/sessions/${encodeURIComponent(sessionId)}/self-rating`,
      { method: "PUT", body: JSON.stringify({ rating }) }
    );
  },

  async submitSessionFeedback(
    sessionId: string,
    payload: SubmitSessionFeedbackPayload
  ): Promise<ApiResponse<SessionFeedbackView>> {
    return fetchApi<SessionFeedbackView>(
      `/api/v1/progress/sessions/${encodeURIComponent(sessionId)}/feedback`,
      {
        method: "POST",
        body: JSON.stringify(payload),
      }
    );
  },
};

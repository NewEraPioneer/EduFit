import { apiClient } from "@/lib/api";
import type {
  ConnectionRequest,
  LinkInvitation,
  SendConnectionRequestPayload,
  StudentLink,
  TutorInboxItem,
  TutoringClass,
  RespondRequestPayload,
  RespondRequestResult,
} from "../types";

export const connectionApi = {
  /**
   * Lấy danh sách liên kết phụ huynh - học sinh đã xác nhận
   */
  async getLinks(): Promise<StudentLink[]> {
    const res = await apiClient.get<StudentLink[]>("/api/v1/connections/parent-student/links");
    return (res.data || []).filter((link) => link.status === "CONFIRMED");
  },

  /**
   * Gửi lời mời liên kết phụ huynh - học sinh qua email
   */
  async inviteParentStudent(targetEmail: string): Promise<void> {
    await apiClient.post<void>("/api/v1/connections/parent-student/invite", {
      targetEmail,
    });
  },

  async getLinkInvitations(): Promise<LinkInvitation[]> {
    const res = await apiClient.get<LinkInvitation[]>("/api/v1/connections/parent-student/invitations");
    return res.data || [];
  },

  async respondLinkInvitation(invitationId: string, accept: boolean): Promise<StudentLink | null> {
    const res = await apiClient.post<StudentLink | null>("/api/v1/connections/parent-student/respond", {
      invitationId,
      accept,
    });
    return res.data ?? null;
  },

  async withdrawLinkInvitation(invitationId: string): Promise<void> {
    await apiClient.post<void>(
      `/api/v1/connections/parent-student/invitations/${encodeURIComponent(invitationId)}/withdraw`
    );
  },

  async revokeLink(linkId: string, reason?: string): Promise<void> {
    const url = `/api/v1/connections/parent-student/links/${encodeURIComponent(linkId)}`;
    if (reason) {
      await apiClient.delete<void>(url, { body: JSON.stringify({ reason }) });
    } else {
      await apiClient.delete<void>(url);
    }
  },

  /**
   * Lấy danh sách các yêu cầu kết nối đã gửi (phía học sinh hoặc phụ huynh)
   */
  async getSentRequests(studentId?: string): Promise<ConnectionRequest[]> {
    const query = studentId ? `?studentId=${encodeURIComponent(studentId)}` : "";
    const res = await apiClient.get<ConnectionRequest[]>(`/api/v1/connections/requests${query}`);
    return res.data || [];
  },

  /**
   * Gửi yêu cầu kết nối học kèm tới gia sư
   */
  async sendRequest(payload: SendConnectionRequestPayload): Promise<ConnectionRequest> {
    const res = await apiClient.post<ConnectionRequest>("/api/v1/connections/requests", payload);
    return res.data as ConnectionRequest;
  },

  /**
   * Hủy yêu cầu kết nối đang chờ (phía học sinh / phụ huynh)
   */
  async cancelRequest(requestId: string): Promise<void> {
    await apiClient.post<void>(
      `/api/v1/connections/requests/${encodeURIComponent(requestId)}/cancel`
    );
  },

  /**
   * Lấy hộp thư yêu cầu kết nối gửi đến gia sư (Tutor Inbox).
   * Hỗ trợ tự động tính toán hasSlotConflict và conflictingSlots từ backend.
   */
  async getTutorInbox(page?: number, size?: number): Promise<TutorInboxItem[]> {
    if (page !== undefined && size !== undefined) {
      const res = await apiClient.get<{ items: TutorInboxItem[] }>(
        `/api/v1/connections/requests/page?page=${page}&size=${size}`
      );
      return res.data?.items || [];
    }
    const res = await apiClient.get<TutorInboxItem[]>("/api/v1/connections/requests");
    return res.data || [];
  },

  /**
   * Lấy danh sách các yêu cầu kết nối đang chờ phản hồi (PENDING) cho gia sư
   */
  async getPendingRequests(): Promise<TutorInboxItem[]> {
    const items = await this.getTutorInbox();
    return items.filter((item) => item.status === "PENDING");
  },

  /**
   * Gia sư phản hồi yêu cầu kết nối:
   * - accept: true -> Duyệt yêu cầu & tạo Lớp học mới (TutoringClass)
   * - accept: false -> Từ chối yêu cầu kèm lý do
   */
  async respondRequest(
    requestId: string,
    payload: RespondRequestPayload
  ): Promise<RespondRequestResult | null> {
    const res = await apiClient.post<RespondRequestResult | null>(
      `/api/v1/connections/requests/${encodeURIComponent(requestId)}/respond`,
      payload
    );
    return res.data ?? null;
  },

  /**
   * Lấy danh sách lớp học mà gia sư đang phụ trách
   */
  async getTutorClasses(): Promise<TutoringClass[]> {
    const res = await apiClient.get<TutoringClass[]>("/api/v1/connections/classes");
    return res.data || [];
  },

  /**
   * Lấy danh sách lớp học mà học sinh đang tham gia
   */
  async getStudentClasses(): Promise<TutoringClass[]> {
    const res = await apiClient.get<TutoringClass[]>("/api/v1/connections/classes");
    return res.data || [];
  },

  /**
   * Kết thúc lớp học đang hoạt động (Class Termination).
   * Backend sẽ phát sự kiện TutoringClassEndedEvent tự động hủy các buổi học tương lai trên GiST.
   */
  async endClass(classId: string, reason?: string): Promise<TutoringClass> {
    const res = await apiClient.post<TutoringClass>(
      `/api/v1/connections/classes/${encodeURIComponent(classId)}/end`,
      { reason: reason?.trim() || undefined }
    );
    return res.data as TutoringClass;
  },
};

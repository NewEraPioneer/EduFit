import { apiClient } from "@/lib/api";

export interface StudentLink { linkId: string; studentId: string; status: string }
export interface ConnectionRequest {
  requestId: string; studentId: string; tutorId: string; needId: string;
  status: string; createdAt: string; expiresAt: string;
}
export const connectionService = {
  async links(): Promise<StudentLink[]> {
    const res = await apiClient.get<StudentLink[]>("/api/v1/connections/parent-student/links");
    return (res.data || []).filter(link => link.status === "CONFIRMED");
  },
  async requests(studentId?: string): Promise<ConnectionRequest[]> {
    const query = studentId ? `?studentId=${encodeURIComponent(studentId)}` : "";
    const res = await apiClient.get<ConnectionRequest[]>(`/api/v1/connections/requests${query}`);
    return res.data || [];
  },
  async send(payload: { needId: string; tutorId: string; desiredSlots: string[]; message?: string }) {
    const res = await apiClient.post<ConnectionRequest>("/api/v1/connections/requests", payload);
    return res.data as ConnectionRequest;
  },
};

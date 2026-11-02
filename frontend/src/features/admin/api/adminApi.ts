import { apiClient } from "@/lib/api";
import type {
  AdminDashboardStats,
  SubjectAdmin,
  CreateSubjectPayload,
  PagedAuditLogs,
  AuditLogQueryParams,
  ParentStudentAdminLink,
} from "../types";

export const adminApi = {
  /**
   * Lấy số liệu thống kê thời gian thực từ cơ sở dữ liệu hệ thống
   * Endpoint: GET /api/v1/admin/dashboard/stats
   */
  async getDashboardStats(): Promise<AdminDashboardStats> {
    const res = await apiClient.get<AdminDashboardStats>("/api/v1/admin/dashboard/stats");
    const data = res.data ?? {
      totalUsers: 0,
      totalTutors: 0,
      totalStudents: 0,
      totalParents: 0,
      activeClasses: 0,
      completedSessions: 0,
      pendingVerifications: 0,
      pendingKyc: 0,
    };
    return {
      ...data,
      pendingVerifications: data.pendingVerifications ?? data.pendingKyc ?? 0,
    };
  },

  /**
   * Lấy danh sách toàn bộ môn học trong danh mục (bao gồm cả trạng thái ẩn/hiện)
   * Endpoint: GET /api/v1/admin/catalogs/subjects
   */
  async getSubjects(): Promise<SubjectAdmin[]> {
    const res = await apiClient.get<SubjectAdmin[]>("/api/v1/admin/catalogs/subjects");
    return Array.isArray(res.data) ? res.data : [];
  },

  /**
   * Tạo môn học mới vào danh mục hệ thống
   * Endpoint: POST /api/v1/admin/catalogs/subjects
   */
  async createSubject(payload: CreateSubjectPayload): Promise<SubjectAdmin> {
    const res = await apiClient.post<SubjectAdmin>("/api/v1/admin/catalogs/subjects", payload);
    return res.data as SubjectAdmin;
  },

  /**
   * Bật/Tắt trạng thái hoạt động (soft-toggle) của môn học
   * Endpoint: PATCH /api/v1/admin/catalogs/subjects/{id}/toggle-status
   */
  async toggleSubjectStatus(id: number): Promise<SubjectAdmin> {
    const res = await apiClient.patch<SubjectAdmin>(`/api/v1/admin/catalogs/subjects/${id}/toggle-status`);
    return res.data as SubjectAdmin;
  },

  /**
   * Cập nhật trạng thái hiển thị rõ ràng của môn học
   * Endpoint: PATCH /api/v1/admin/catalogs/subjects/{id}/status
   */
  async updateSubjectStatus(id: number, isActive: boolean): Promise<SubjectAdmin> {
    const res = await apiClient.patch<SubjectAdmin>(`/api/v1/admin/catalogs/subjects/${id}/status`, { isActive });
    return res.data as SubjectAdmin;
  },

  /**
   * Tra cứu nhật ký kiểm toán hệ thống có phân trang và lọc theo hành động
   * Endpoint: GET /api/v1/admin/audit-logs
   */
  async getAuditLogs(params?: AuditLogQueryParams): Promise<PagedAuditLogs> {
    const searchParams = new URLSearchParams();
    if (params?.page !== undefined) searchParams.set("page", String(params.page));
    if (params?.size !== undefined) searchParams.set("size", String(params.size));
    if (params?.action && params.action.trim()) searchParams.set("action", params.action.trim());

    const queryString = searchParams.toString() ? `?${searchParams.toString()}` : "";
    const res = await apiClient.get<PagedAuditLogs>(`/api/v1/admin/audit-logs${queryString}`);
    return res.data ?? {
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    };
  },

  /**
   * Tra cứu liên kết phụ huynh - học sinh (UC3.3)
   * Endpoint: GET /api/v1/connections/admin/parent-student/links hoặc GET /api/v1/connections/parent-student/links
   */
  async getParentStudentLinks(email?: string): Promise<ParentStudentAdminLink[]> {
    const query = email ? `?email=${encodeURIComponent(email.trim())}` : "";
    try {
      const res = await apiClient.get<ParentStudentAdminLink[]>(`/api/v1/connections/admin/parent-student/links${query}`);
      return Array.isArray(res.data) ? res.data : [];
    } catch {
      // Fallback nếu endpoint admin chuyên dụng chưa được gọi với email
      const res = await apiClient.get<ParentStudentAdminLink[]>("/api/v1/connections/parent-student/links");
      return Array.isArray(res.data) ? res.data : [];
    }
  },

  /**
   * Thu hồi liên kết phụ huynh - học sinh theo UC3.3
   * Endpoint: DELETE /api/v1/connections/parent-student/links/{id}
   */
  async revokeParentStudentLink(linkId: string): Promise<void> {
    await apiClient.delete(`/api/v1/connections/parent-student/links/${linkId}`);
  },
};

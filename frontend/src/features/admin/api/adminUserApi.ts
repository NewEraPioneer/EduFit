import { fetchApi, ApiResponse } from "@/lib/api";
import type {
  AdminUser,
  AdminUserListResponse,
  AdminUserStats,
  GetUsersParams,
  AdminParentStudentLink,
  AdminParentStudentLinkListResponse,
  AdminParentStudentLinkStats,
  GetFamilyLinksParams,
} from "../types";

export const adminUserApi = {
  /**
   * Lấy danh sách người dùng với phân trang, lọc vai trò/trạng thái và tìm kiếm
   */
  async getUsers(params: GetUsersParams = {}): Promise<ApiResponse<AdminUserListResponse>> {
    const query = new URLSearchParams();
    if (params.role) query.set("role", params.role);
    if (params.status) query.set("status", params.status);
    if (params.search) query.set("search", params.search);
    if (params.page !== undefined) query.set("page", params.page.toString());
    if (params.size !== undefined) query.set("size", params.size.toString());

    const qs = query.toString();
    const endpoint = `/api/v1/admin/users${qs ? `?${qs}` : ""}`;
    return fetchApi<AdminUserListResponse>(endpoint, { method: "GET" });
  },

  /**
   * Lấy thống kê tổng quan người dùng theo nhóm và trạng thái
   */
  async getUserStats(): Promise<ApiResponse<AdminUserStats>> {
    return fetchApi<AdminUserStats>("/api/v1/admin/users/stats", { method: "GET" });
  },

  /**
   * Lấy chi tiết thông tin một người dùng theo ID
   */
  async getUserById(id: string): Promise<ApiResponse<AdminUser>> {
    return fetchApi<AdminUser>(`/api/v1/admin/users/${id}`, { method: "GET" });
  },

  /**
   * Khóa tài khoản người dùng
   */
  async lockUser(id: string, reason?: string): Promise<ApiResponse<AdminUser>> {
    const query = reason ? `?reason=${encodeURIComponent(reason)}` : "";
    return fetchApi<AdminUser>(`/api/v1/admin/users/${id}/lock${query}`, {
      method: "POST",
    });
  },

  /**
   * Mở khóa tài khoản người dùng
   */
  async unlockUser(id: string): Promise<ApiResponse<AdminUser>> {
    return fetchApi<AdminUser>(`/api/v1/admin/users/${id}/unlock`, {
      method: "POST",
    });
  },

  /**
   * Cập nhật trạng thái người dùng (ACTIVE / DEACTIVATED)
   */
  async updateStatus(
    id: string,
    status: "ACTIVE" | "DEACTIVATED",
    reason?: string
  ): Promise<ApiResponse<AdminUser>> {
    return fetchApi<AdminUser>(`/api/v1/admin/users/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status, reason }),
    });
  },

  /**
   * Lấy danh sách liên kết Phụ huynh - Con với phân trang, lọc trạng thái và tìm kiếm
   */
  async getFamilyLinks(
    params: GetFamilyLinksParams = {}
  ): Promise<ApiResponse<AdminParentStudentLinkListResponse>> {
    const query = new URLSearchParams();
    if (params.status && params.status !== "ALL") query.set("status", params.status);
    if (params.search) query.set("search", params.search);
    if (params.page !== undefined) query.set("page", params.page.toString());
    if (params.size !== undefined) query.set("size", params.size.toString());

    const qs = query.toString();
    const endpoint = `/api/v1/connections/admin/parent-student/links/all${qs ? `?${qs}` : ""}`;
    return fetchApi<AdminParentStudentLinkListResponse>(endpoint, { method: "GET" });
  },

  /**
   * Lấy thống kê số lượng liên kết Phụ huynh - Con
   */
  async getFamilyLinkStats(): Promise<ApiResponse<AdminParentStudentLinkStats>> {
    return fetchApi<AdminParentStudentLinkStats>(
      "/api/v1/connections/admin/parent-student/links/stats",
      { method: "GET" }
    );
  },

  /**
   * Quản trị viên hủy liên kết giữa Phụ huynh và Học sinh
   */
  async revokeFamilyLink(id: string, reason: string): Promise<ApiResponse<void>> {
    return fetchApi<void>(
      `/api/v1/connections/admin/parent-student/links/${id}/revoke`,
      {
        method: "POST",
        body: JSON.stringify({ reason }),
      }
    );
  },
};


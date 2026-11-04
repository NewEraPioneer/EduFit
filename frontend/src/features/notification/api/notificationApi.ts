import { fetchApi, ApiResponse } from "@/lib/api";
import type {
  NotificationItem,
  NotificationListResponse,
  UnreadCountResponse,
  UnreadSummaryResponse,
  MarkReadResponse,
  MarkAllReadResponse,
} from "../types";

export const notificationApi = {
  /**
   * Lấy danh sách thông báo của người dùng với tùy chọn lọc chưa đọc và phân trang
   * GET /api/v1/notifications
   */
  async getNotifications(
    unreadOnly = false,
    page = 0,
    size = 20
  ): Promise<ApiResponse<NotificationListResponse>> {
    const params = new URLSearchParams({
      unreadOnly: String(unreadOnly),
      page: String(page),
      size: String(size),
    });
    return fetchApi<NotificationListResponse>(
      `/api/v1/notifications?${params.toString()}`,
      { method: "GET" }
    );
  },

  /**
   * Lấy số lượng thông báo chưa đọc phục vụ badge
   * GET /api/v1/notifications/unread-count
   */
  async getUnreadCount(): Promise<ApiResponse<UnreadCountResponse>> {
    return fetchApi<UnreadCountResponse>(
      "/api/v1/notifications/unread-count",
      { method: "GET" }
    );
  },

  /**
   * Lấy tóm tắt số lượng thông báo chưa đọc theo danh mục phục vụ chấm sidebar
   * GET /api/v1/notifications/unread-summary
   */
  async getUnreadSummary(): Promise<ApiResponse<UnreadSummaryResponse>> {
    return fetchApi<UnreadSummaryResponse>(
      "/api/v1/notifications/unread-summary",
      { method: "GET" }
    );
  },

  /**
   * Đánh dấu một thông báo là đã đọc
   * PATCH /api/v1/notifications/{id}/read
   */
  async markAsRead(id: string): Promise<ApiResponse<MarkReadResponse>> {
    return fetchApi<MarkReadResponse>(
      `/api/v1/notifications/${encodeURIComponent(id)}/read`,
      { method: "PATCH" }
    );
  },

  /**
   * Đánh dấu tất cả thông báo là đã đọc
   * PATCH /api/v1/notifications/read-all
   */
  async markAllAsRead(): Promise<ApiResponse<MarkAllReadResponse>> {
    return fetchApi<MarkAllReadResponse>(
      "/api/v1/notifications/read-all",
      { method: "PATCH" }
    );
  },
};

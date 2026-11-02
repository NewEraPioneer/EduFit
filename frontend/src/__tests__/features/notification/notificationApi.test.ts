import { describe, it, expect, vi, beforeEach } from "vitest";
import { notificationApi } from "@/features/notification/api/notificationApi";
import * as apiModule from "@/lib/api";

describe("Notification API Client", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("gọi GET /api/v1/notifications với query params chuẩn xác", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: {
        items: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        unreadCount: 0,
      },
    });

    const res = await notificationApi.getNotifications(true, 1, 15);

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/notifications?unreadOnly=true&page=1&size=15",
      { method: "GET" }
    );
    expect(res.success).toBe(true);
  });

  it("gọi GET /api/v1/notifications/unread-count để lấy số lượng chưa đọc", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: { unreadCount: 3 },
    });

    const res = await notificationApi.getUnreadCount();

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/notifications/unread-count",
      { method: "GET" }
    );
    expect(res.data?.unreadCount).toBe(3);
  });

  it("gọi PATCH /api/v1/notifications/{id}/read để đánh dấu 1 tin đã đọc", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: { notificationId: "notif-123", isRead: true },
    });

    const res = await notificationApi.markAsRead("notif-123");

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/notifications/notif-123/read",
      { method: "PATCH" }
    );
    expect(res.data?.isRead).toBe(true);
  });

  it("gọi PATCH /api/v1/notifications/read-all để đánh dấu đọc tất cả", async () => {
    const fetchSpy = vi.spyOn(apiModule, "fetchApi").mockResolvedValue({
      success: true,
      data: { count: 5 },
    });

    const res = await notificationApi.markAllAsRead();

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/v1/notifications/read-all",
      { method: "PATCH" }
    );
    expect(res.data?.count).toBe(5);
  });
});

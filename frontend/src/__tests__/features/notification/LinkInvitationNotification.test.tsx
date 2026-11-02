import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NotificationDropdown } from "@/features/notification/components/NotificationDropdown";
import { notificationApi } from "@/features/notification/api/notificationApi";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

describe("thông báo lời mời liên kết", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    push.mockClear();
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue({ success: true, data: {
      items: [{ notificationId: "notification-1", type: "LINK_INVITED", title: "Lời mời liên kết", shortText: "Bạn có lời mời", isRead: false, createdAt: "2026-10-18T00:00:00Z" }],
      page: 0, size: 20, totalElements: 1, totalPages: 1, unreadCount: 1,
    } });
    vi.spyOn(notificationApi, "markAsRead").mockResolvedValue({ success: true, data: { notificationId: "notification-1", isRead: true } });
  });

  it("đưa học sinh đến nơi xử lý lời mời", async () => {
    const countChanged = vi.fn();
    render(<NotificationDropdown role="STUDENT" onUnreadCountChange={countChanged} />);
    fireEvent.click(await screen.findByRole("button", { name: /Lời mời liên kết/ }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/student/family"));
    expect(notificationApi.markAsRead).toHaveBeenCalledWith("notification-1");
    expect(countChanged).toHaveBeenCalledWith(0);
  });

  it("đưa gia sư đến nơi duyệt yêu cầu kết nối khi nhận CONNECTION_REQUEST", async () => {
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue({ success: true, data: {
      items: [{ notificationId: "notification-2", type: "CONNECTION_REQUEST", title: "Yêu cầu kết nối mới", shortText: "Học sinh muốn học", isRead: false, createdAt: "2026-10-18T00:00:00Z" }],
      page: 0, size: 20, totalElements: 1, totalPages: 1, unreadCount: 1,
    } });
    render(<NotificationDropdown role="TUTOR" />);
    fireEvent.click(await screen.findByRole("button", { name: /Yêu cầu kết nối mới/ }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/tutor/requests"));
  });

  it("đưa người dùng đến lịch học khi nhận thông báo SESSION_PROPOSED", async () => {
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue({ success: true, data: {
      items: [{ notificationId: "notification-3", type: "SESSION_PROPOSED", title: "Đề xuất buổi học", shortText: "Buổi học mới", isRead: false, createdAt: "2026-10-18T00:00:00Z" }],
      page: 0, size: 20, totalElements: 1, totalPages: 1, unreadCount: 1,
    } });
    render(<NotificationDropdown role="STUDENT" />);
    fireEvent.click(await screen.findByRole("button", { name: /Đề xuất buổi học/ }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/student/schedule"));
  });

  it("đưa gia sư đến trang đánh giá khi nhận REVIEW_RECEIVED", async () => {
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue({ success: true, data: {
      items: [{ notificationId: "notification-4", type: "REVIEW_RECEIVED", title: "Đánh giá mới", shortText: "5 sao từ học sinh", isRead: false, createdAt: "2026-10-18T00:00:00Z" }],
      page: 0, size: 20, totalElements: 1, totalPages: 1, unreadCount: 1,
    } });
    render(<NotificationDropdown role="TUTOR" />);
    fireEvent.click(await screen.findByRole("button", { name: /Đánh giá mới/ }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/tutor/reviews"));
  });

  it("đưa học sinh đến trang tiến độ khi nhận thông báo MILESTONE_COMPLETED", async () => {
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue({ success: true, data: {
      items: [{ notificationId: "notification-5", type: "MILESTONE_COMPLETED", title: "Hoàn thành cột mốc", shortText: "Bạn đã đạt cột mốc mới", isRead: false, createdAt: "2026-10-18T00:00:00Z" }],
      page: 0, size: 20, totalElements: 1, totalPages: 1, unreadCount: 1,
    } });
    render(<NotificationDropdown role="STUDENT" />);
    fireEvent.click(await screen.findByRole("button", { name: /Hoàn thành cột mốc/ }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/student/progress"));
  });
});

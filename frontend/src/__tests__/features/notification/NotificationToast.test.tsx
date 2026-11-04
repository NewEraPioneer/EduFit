import { afterEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  NotificationProvider,
  NotificationFloatingToast,
  notificationApi,
} from "@/features/notification";

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
}));

describe("NotificationFloatingToast", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    mockPush.mockReset();
  });

  it("không hiển thị toast cho các thông báo cũ trong lần fetch đầu tiên", async () => {
    vi.useFakeTimers();
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({
      success: true,
      data: { total: 1, byCategory: { SCHEDULE: 1 } },
    });
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue({
      success: true,
      data: {
        items: [
          {
            notificationId: "old-1",
            type: "SESSION_RESCHEDULE_PROPOSED",
            title: "Đề xuất đổi lịch học",
            shortText: "Có đề xuất dời buổi học",
            isRead: false,
            createdAt: new Date().toISOString(),
          },
        ],
        page: 0,
        size: 5,
        totalElements: 1,
        totalPages: 1,
        unreadCount: 1,
      },
    });

    render(
      <NotificationProvider role="STUDENT">
        <NotificationFloatingToast role="STUDENT" />
      </NotificationProvider>
    );

    await act(async () => {
      await Promise.resolve();
    });

    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("hiển thị toast khi có thông báo mới sau chu kỳ polling tiếp theo và tự ẩn sau 4.5s", async () => {
    vi.useFakeTimers();
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({
      success: true,
      data: { total: 0, byCategory: {} },
    });
    const getNotificationsSpy = vi.spyOn(notificationApi, "getNotifications");

    // Lần 1: Không có thông báo
    getNotificationsSpy.mockResolvedValueOnce({
      success: true,
      data: {
        items: [],
        page: 0,
        size: 5,
        totalElements: 0,
        totalPages: 0,
        unreadCount: 0,
      },
    });

    render(
      <NotificationProvider role="STUDENT">
        <NotificationFloatingToast role="STUDENT" />
      </NotificationProvider>
    );

    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.queryByRole("alert")).toBeNull();

    // Lần 2: Có thông báo mới sau 15s
    getNotificationsSpy.mockResolvedValueOnce({
      success: true,
      data: {
        items: [
          {
            notificationId: "new-notif-1",
            type: "SESSION_RESCHEDULE_PROPOSED",
            title: "Đề xuất đổi lịch học",
            shortText: "Gia sư đề xuất đổi sang 19:00",
            isRead: false,
            createdAt: new Date().toISOString(),
          },
        ],
        page: 0,
        size: 5,
        totalElements: 1,
        totalPages: 1,
        unreadCount: 1,
      },
    });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(15_000);
    });

    const alert = screen.getByRole("alert");
    expect(alert).toBeDefined();
    expect(alert.textContent).toContain("Đề xuất đổi lịch học");
    expect(alert.textContent).toContain("Gia sư đề xuất đổi sang 19:00");

    // Sau 4.5s tự động ẩn
    await act(async () => {
      await vi.advanceTimersByTimeAsync(4500);
    });

    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("click nút đóng [X] tắt toast ngay lập tức", async () => {
    vi.useFakeTimers();
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({
      success: true,
      data: { total: 0, byCategory: {} },
    });
    const getNotificationsSpy = vi.spyOn(notificationApi, "getNotifications");

    getNotificationsSpy
      .mockResolvedValueOnce({
        success: true,
        data: { items: [], page: 0, size: 5, totalElements: 0, totalPages: 0, unreadCount: 0 },
      })
      .mockResolvedValueOnce({
        success: true,
        data: {
          items: [
            {
              notificationId: "notif-x",
              type: "SESSION_RESCHEDULE_REJECTED",
              title: "Đề xuất đổi lịch bị từ chối",
              shortText: "Học sinh từ chối yêu cầu đổi lịch",
              isRead: false,
              createdAt: new Date().toISOString(),
            },
          ],
          page: 0,
          size: 5,
          totalElements: 1,
          totalPages: 1,
          unreadCount: 1,
        },
      });

    render(
      <NotificationProvider role="TUTOR">
        <NotificationFloatingToast role="TUTOR" />
      </NotificationProvider>
    );

    await act(async () => {
      await Promise.resolve();
    });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(15_000);
    });

    expect(screen.getByRole("alert")).toBeDefined();

    const closeBtn = screen.getByLabelText("Đóng thông báo");
    await act(async () => {
      closeBtn.click();
    });

    expect(screen.queryByRole("alert")).toBeNull();
  });
});

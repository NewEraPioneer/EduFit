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

  const unread = (id: string, minute: number) => ({
    notificationId: id,
    type: "SESSION_CANCELLED",
    title: `Tin ${id}`,
    shortText: "x",
    isRead: false,
    createdAt: new Date(Date.UTC(2026, 10, 5, 10, minute)).toISOString(),
  });
  const listOf = (items: ReturnType<typeof unread>[]) => ({
    success: true as const,
    data: { items, page: 0, size: 20, totalElements: items.length, totalPages: 1, unreadCount: items.length },
  });

  it("F-10: đọc bớt thông báo cũ không làm thông báo cũ hơn bị toast như mới; chỉ thông báo thực sự mới được toast", async () => {
    vi.useFakeTimers();
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({ success: true, data: { total: 0, byCategory: {} } });
    const list = vi.spyOn(notificationApi, "getNotifications");
    const [n1, n2, n3, n4] = [unread("n1", 1), unread("n2", 2), unread("n3", 3), unread("n4", 4)];
    list.mockResolvedValueOnce(listOf([n3, n2, n1])); // lần tải đầu: đã biết, không toast
    render(<NotificationProvider role="STUDENT"><NotificationFloatingToast role="STUDENT" /></NotificationProvider>);
    await act(async () => { await Promise.resolve(); });

    list.mockResolvedValueOnce(listOf([n2, n1])); // người dùng đã đọc n3
    await act(async () => { await vi.advanceTimersByTimeAsync(15_000); });
    expect(screen.queryByRole("alert")).toBeNull();

    list.mockResolvedValueOnce(listOf([n4, n2, n1])); // n4 mới đến
    await act(async () => { await vi.advanceTimersByTimeAsync(15_000); });
    const alerts = screen.getAllByRole("alert");
    expect(alerts).toHaveLength(1);
    expect(alerts[0].textContent).toContain("Tin n4");
  });

  it("F-10: nhiều thông báo đến giữa hai lần poll vẫn được toast (tối đa 5 cái mới nhất)", async () => {
    vi.useFakeTimers();
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({ success: true, data: { total: 0, byCategory: {} } });
    const list = vi.spyOn(notificationApi, "getNotifications");
    list.mockResolvedValueOnce(listOf([]));
    render(<NotificationProvider role="STUDENT"><NotificationFloatingToast role="STUDENT" /></NotificationProvider>);
    await act(async () => { await Promise.resolve(); });

    const burst = Array.from({ length: 7 }, (_, i) => unread(`b${i}`, 10 + i)).reverse(); // mới nhất trước
    list.mockResolvedValueOnce(listOf(burst));
    await act(async () => { await vi.advanceTimersByTimeAsync(15_000); });

    const shown = screen.getAllByRole("alert").map((a) => a.textContent ?? "");
    expect(shown).toHaveLength(5);
    expect(shown.join("|")).toContain("Tin b6");
    expect(shown.join("|")).not.toContain("Tin b0");
  });

  it("F-11: gắn lại Provider (đổi người dùng) bắt đầu từ trạng thái sạch, không toast thông báo của phiên trước", async () => {
    vi.useFakeTimers();
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({ success: true, data: { total: 0, byCategory: {} } });
    const list = vi.spyOn(notificationApi, "getNotifications");
    list.mockResolvedValue(listOf([unread("u1", 1)]));
    const first = render(<NotificationProvider role="STUDENT"><NotificationFloatingToast role="STUDENT" /></NotificationProvider>);
    await act(async () => { await Promise.resolve(); });
    first.unmount();

    list.mockResolvedValue(listOf([unread("other-user", 2)]));
    render(<NotificationProvider role="TUTOR"><NotificationFloatingToast role="TUTOR" /></NotificationProvider>);
    await act(async () => { await Promise.resolve(); });

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

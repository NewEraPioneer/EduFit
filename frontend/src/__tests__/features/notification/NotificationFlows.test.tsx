import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  NotificationDropdown,
  NotificationProvider,
  getNotificationRoute,
  notificationApi,
  useNotification,
} from "@/features/notification";
import type { NotificationItem } from "@/features/notification";

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
}));

type Role = "STUDENT" | "PARENT" | "TUTOR" | "ADMIN";

function item(over: Partial<NotificationItem> = {}): NotificationItem {
  return {
    notificationId: "n-1",
    type: "SESSION_RESCHEDULE_PROPOSED",
    title: "Đề xuất đổi lịch học",
    shortText: "Gia sư đề xuất đổi sang 19:00",
    isRead: false,
    createdAt: new Date().toISOString(),
    ...over,
  };
}

function page(items: NotificationItem[], unreadCount = items.filter((i) => !i.isRead).length) {
  return {
    success: true,
    data: { items, page: 0, size: 20, totalElements: items.length, totalPages: 1, unreadCount },
  };
}

/** Dropdown luôn chạy dưới NotificationProvider như trong DashboardShell: số chưa đọc và mark-read do context lo. */
function renderDropdown(ui: React.ReactElement, unread = 0) {
  const summary = vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({
    success: true,
    data: { total: unread, byCategory: {} },
  });
  render(<NotificationProvider role="STUDENT">{ui}</NotificationProvider>);
  // Máy chủ trả số chưa đọc mới sau khi đọc: test đổi giá trị trước khi thao tác
  return (total: number) => summary.mockResolvedValue({ success: true, data: { total, byCategory: {} } });
}

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  mockPush.mockReset();
});

/**
 * Mọi (type, targetType) mà backend thực sự ghi vào bảng thông báo, kèm đích điều hướng mong đợi
 * theo từng vai trò. Giữ bảng này đồng bộ với các listener/notifier phía backend.
 */
describe("Điều hướng theo từng loại thông báo của backend", () => {
  const schedule = (suffix = "") => ({
    STUDENT: `/student/schedule${suffix}`, PARENT: `/parent/schedule${suffix}`,
    TUTOR: `/tutor/schedule${suffix}`, ADMIN: `/student/schedule${suffix}` });
  const sessionLink = schedule("?sessionId=t-1");
  const cases: Array<[string, string | undefined, Record<Role, string>]> = [
    // Buổi học: backend ghi targetType SESSION + id buổi => mở thẳng chi tiết buổi học (deep link)
    ["SESSION_PROPOSED", "SESSION", sessionLink],
    ["SESSION_PROPOSAL_REJECTED", "SESSION", sessionLink],
    ["SESSION_SCHEDULED", "SESSION", sessionLink],
    ["SESSION_RESCHEDULED", "SESSION", sessionLink],
    ["SESSION_RESCHEDULE_PROPOSED", "SESSION", sessionLink],
    ["SESSION_RESCHEDULE_REJECTED", "SESSION", sessionLink],
    ["SESSION_CANCELLED", "SESSION", sessionLink],
    ["SESSION_EXPIRED", "SESSION", sessionLink],
    ["SESSION_REMINDER", "SESSION", sessionLink],
    ["SESSION_OUTCOME_OVERDUE", "SESSION", sessionLink],
    ["SESSION_FEEDBACK_REMINDER", "SESSION", sessionLink],
    // Thay đổi hàng loạt trỏ về lớp, không có một buổi cụ thể => trang lịch
    ["SESSION_RECURRING_CHANGED", "CLASS", schedule()],
    ["SESSION_BULK_CANCELLED", "CLASS", schedule()],
    ["SESSION_BATCH_SCHEDULED", "CLASS", schedule()],
    ["SESSION_BATCH_REJECTED", "CLASS", schedule()],
    ["MILESTONE_ACHIEVED", "MILESTONE", {
      STUDENT: "/student/progress", PARENT: "/parent/progress", TUTOR: "/tutor/classes", ADMIN: "/student/progress" }],
    ["PERIOD_REPORT_SENT", "PERIOD_REPORT", {
      STUDENT: "/student/progress", PARENT: "/parent/progress", TUTOR: "/tutor/classes", ADMIN: "/student/progress" }],
    ["REVIEW_RECEIVED", "REVIEW", {
      STUDENT: "/student/classes", PARENT: "/parent/dashboard", TUTOR: "/tutor/reviews", ADMIN: "/student/classes" }],
    ["REVIEW_UPDATED", "REVIEW", {
      STUDENT: "/student/classes", PARENT: "/parent/dashboard", TUTOR: "/tutor/reviews", ADMIN: "/student/classes" }],
    ["VERIFICATION_APPROVED", "VERIFICATION", {
      STUDENT: "/tutor/verification", PARENT: "/tutor/verification", TUTOR: "/tutor/verification", ADMIN: "/admin/verification-queue" }],
    ["VERIFICATION_REVOKED", "VERIFICATION", {
      STUDENT: "/tutor/verification", PARENT: "/tutor/verification", TUTOR: "/tutor/verification", ADMIN: "/admin/verification-queue" }],
    ["LINK_INVITED", "PARENT_STUDENT_LINK", {
      STUDENT: "/student/family", PARENT: "/parent/children", TUTOR: "/student/family", ADMIN: "/student/family" }],
    // Học sinh xem yêu cầu kết nối ở tab yêu cầu (đúng như mục Sidebar), chấp nhận thì tới lớp
    ["CONNECTION_REQUESTED", "CONNECTION_REQUEST", {
      STUDENT: "/student/tutors?tab=requests", PARENT: "/parent/tutors", TUTOR: "/tutor/requests", ADMIN: "/student/tutors?tab=requests" }],
    ["CONNECTION_REJECTED", "CONNECTION_REQUEST", {
      STUDENT: "/student/tutors?tab=requests", PARENT: "/parent/tutors", TUTOR: "/tutor/requests", ADMIN: "/student/tutors?tab=requests" }],
    ["CONNECTION_ACCEPTED", "CLASS", {
      STUDENT: "/student/classes", PARENT: "/parent/children", TUTOR: "/tutor/classes", ADMIN: "/student/classes" }],
  ];

  for (const [type, targetType, expected] of cases) {
    for (const role of Object.keys(expected) as Role[]) {
      it(`${type} → ${expected[role]} (${role})`, () => {
        expect(getNotificationRoute(item({ type, targetType, targetId: "t-1" }), role)).toBe(expected[role]);
      });
    }
  }

  it("thông báo thanh toán theo buổi học dẫn tới trang chi tiết buổi học của đúng vai trò", () => {
    const n = item({ type: "PAYMENT_SETTLED", targetType: "PAYMENT_SESSION", targetId: "s 1" });
    expect(getNotificationRoute(n, "STUDENT")).toBe("/student/payments/sessions/s%201");
    expect(getNotificationRoute(n, "PARENT")).toBe("/parent/billing/sessions/s%201");
    expect(getNotificationRoute(n, "TUTOR")).toBe("/tutor/earnings/sessions/s%201");
    expect(getNotificationRoute(n, "ADMIN")).toBe("/admin/disputes/s%201");
  });

  it("loại không xác định về dashboard của vai trò", () => {
    expect(getNotificationRoute(item({ type: "UNKNOWN", targetType: undefined }), "TUTOR")).toBe("/tutor/dashboard");
  });
});

describe("NotificationDropdown", () => {
  it("hiển thị danh sách, huy hiệu chưa đọc và nút Đọc tất cả", async () => {
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(
      page([item({ notificationId: "a" }), item({ notificationId: "b", title: "Cột mốc", isRead: true })]),
    );
    renderDropdown(<NotificationDropdown role="STUDENT" />, 1);

    expect(await screen.findByText("Đề xuất đổi lịch học")).toBeDefined();
    expect(screen.getByText("Cột mốc")).toBeDefined();
    expect(await screen.findByText("Đọc tất cả")).toBeDefined();
    expect(await screen.findByText("Chưa đọc (1)")).toBeDefined();
  });

  it("hiển thị trạng thái rỗng", async () => {
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(page([]));
    renderDropdown(<NotificationDropdown role="STUDENT" />);
    expect(await screen.findByText("Chưa có thông báo nào")).toBeDefined();
  });

  it("hiển thị lỗi khi tải thất bại", async () => {
    vi.spyOn(notificationApi, "getNotifications").mockRejectedValue(new Error("Mất kết nối"));
    renderDropdown(<NotificationDropdown role="STUDENT" />);
    expect(await screen.findByText("Mất kết nối")).toBeDefined();
  });

  it("bấm vào thông báo chưa đọc: đánh dấu đã đọc, điều hướng đúng đích và đóng dropdown", async () => {
    const markRead = vi.spyOn(notificationApi, "markAsRead").mockResolvedValue({ success: true } as never);
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(
      page([item({ notificationId: "m1", type: "MILESTONE_ACHIEVED", targetType: "MILESTONE", title: "Chúc mừng! Đạt cột mốc mới" })]),
    );
    const onClose = vi.fn();
    const serverNowHasUnread = renderDropdown(<NotificationDropdown role="PARENT" onClose={onClose} />, 1);
    serverNowHasUnread(0);

    await userEvent.click(await screen.findByText("Chúc mừng! Đạt cột mốc mới"));

    expect(markRead).toHaveBeenCalledTimes(1);
    expect(markRead).toHaveBeenCalledWith("m1");
    expect(mockPush).toHaveBeenCalledWith("/parent/progress");
    expect(onClose).toHaveBeenCalled();
    // Số chưa đọc trên chuông trừ ngay (cùng một nguồn với TopHeader), không bị trừ hai lần
    expect(await screen.findByText("Chưa đọc (0)")).toBeDefined();
  });

  it("bấm vào thông báo đã đọc: không gọi markAsRead nhưng vẫn điều hướng", async () => {
    const markRead = vi.spyOn(notificationApi, "markAsRead").mockResolvedValue({ success: true } as never);
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(
      page([item({ notificationId: "r1", isRead: true, type: "REVIEW_RECEIVED", targetType: "REVIEW", title: "Đánh giá mới" })]),
    );
    renderDropdown(<NotificationDropdown role="TUTOR" />);
    await userEvent.click(await screen.findByText("Đánh giá mới"));
    expect(markRead).not.toHaveBeenCalled();
    expect(mockPush).toHaveBeenCalledWith("/tutor/reviews");
  });

  it("Đọc tất cả: gọi API, bỏ chấm chưa đọc và ẩn nút", async () => {
    const markAll = vi.spyOn(notificationApi, "markAllAsRead").mockResolvedValue({ success: true } as never);
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(
      page([item({ notificationId: "a" }), item({ notificationId: "b" })]),
    );
    const serverNowHasUnread = renderDropdown(<NotificationDropdown role="STUDENT" />, 2);
    await screen.findByText("Đọc tất cả");
    serverNowHasUnread(0);

    await userEvent.click(screen.getByText("Đọc tất cả"));

    expect(markAll).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(screen.queryByText("Đọc tất cả")).toBeNull());
  });

  it("tab Chưa đọc yêu cầu API lọc chưa đọc", async () => {
    const spy = vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(page([item()]));
    renderDropdown(<NotificationDropdown role="STUDENT" />);
    await screen.findByText("Đề xuất đổi lịch học");
    const unreadCalls = () => spy.mock.calls.filter((call) => call[0] === true).length;
    const before = unreadCalls();

    await userEvent.click(screen.getByText(/Chưa đọc \(/));

    await waitFor(() => expect(unreadCalls()).toBeGreaterThan(before));
  });

  it("F-15: đủ 20 mục thì có nút Xem thêm và tải 20 mục kế tiếp", async () => {
    const twenty = Array.from({ length: 20 }, (_, i) => item({ notificationId: `n${i}`, title: `Tin ${i}` }));
    const spy = vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(page(twenty));
    renderDropdown(<NotificationDropdown role="STUDENT" />, 20);

    await userEvent.click(await screen.findByText("Xem thêm"));

    await waitFor(() => expect(spy).toHaveBeenCalledWith(false, 0, 40));
  });

  it("ít hơn một trang thì không có nút Xem thêm", async () => {
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(page([item()]));
    renderDropdown(<NotificationDropdown role="STUDENT" />, 1);
    await screen.findByText("Đề xuất đổi lịch học");
    expect(screen.queryByText("Xem thêm")).toBeNull();
  });

  it("F-16: bấm nút chuông không bị coi là 'bấm ra ngoài'; bấm chỗ khác thì đóng", async () => {
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(page([item()]));
    const onClose = vi.fn();
    renderDropdown(
      <>
        <button type="button" data-notification-trigger>chuông</button>
        <NotificationDropdown role="STUDENT" onClose={onClose} />
      </>,
    );
    await screen.findByText("Đề xuất đổi lịch học");

    await userEvent.pointer({ keys: "[MouseLeft>]", target: screen.getByText("chuông") });
    expect(onClose).not.toHaveBeenCalled();

    await userEvent.pointer({ keys: "[MouseLeft>]", target: document.body });
    expect(onClose).toHaveBeenCalled();
  });

  it("tự làm mới danh sách sau 15 giây", async () => {
    vi.useFakeTimers();
    const spy = vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(page([]));
    renderDropdown(<NotificationDropdown role="STUDENT" />);
    await act(async () => { await Promise.resolve(); });
    // chỉ đếm lần gọi của dropdown (tab "Tất cả" = unreadOnly false); Provider luôn hỏi unreadOnly = true
    const listCalls = () => spy.mock.calls.filter((call) => call[0] === false).length;
    const before = listCalls();

    await act(async () => { await vi.advanceTimersByTimeAsync(15_000); });

    expect(listCalls()).toBeGreaterThan(before);
  });
});

describe("NotificationProvider: chấm cảnh báo sidebar và bộ đếm", () => {
  function Probe() {
    const { unreadCount, unreadCategoryMap, markAllAsRead, markAsRead } = useNotification();
    return (
      <div>
        <span data-testid="count">{unreadCount}</span>
        <span data-testid="cats">{Object.keys(unreadCategoryMap).sort().join(",")}</span>
        <button onClick={() => void markAllAsRead()}>all</button>
        <button onClick={() => void markAsRead("x")}>one</button>
      </div>
    );
  }

  it("chỉ bật chấm cho danh mục có thông báo chưa đọc", async () => {
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({
      success: true,
      data: { total: 3, byCategory: { SCHEDULE: 2, PAYMENT: 1, LINK: 0 } },
    });
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(page([]));
    render(<NotificationProvider role="STUDENT"><Probe /></NotificationProvider>);

    await waitFor(() => expect(screen.getByTestId("count").textContent).toBe("3"));
    expect(screen.getByTestId("cats").textContent).toBe("PAYMENT,SCHEDULE");
  });

  it("Đọc tất cả xóa ngay bộ đếm và mọi chấm", async () => {
    vi.spyOn(notificationApi, "getUnreadSummary")
      .mockResolvedValueOnce({ success: true, data: { total: 2, byCategory: { SCHEDULE: 2 } } })
      .mockResolvedValue({ success: true, data: { total: 0, byCategory: {} } });
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(page([]));
    vi.spyOn(notificationApi, "markAllAsRead").mockResolvedValue({ success: true } as never);
    render(<NotificationProvider role="STUDENT"><Probe /></NotificationProvider>);
    await waitFor(() => expect(screen.getByTestId("count").textContent).toBe("2"));

    await userEvent.click(screen.getByText("all"));

    await waitFor(() => expect(screen.getByTestId("count").textContent).toBe("0"));
    expect(screen.getByTestId("cats").textContent).toBe("");
  });

  it("lỗi mạng khi polling không làm hỏng giao diện", async () => {
    vi.spyOn(notificationApi, "getUnreadSummary").mockRejectedValue(new Error("offline"));
    vi.spyOn(notificationApi, "getNotifications").mockRejectedValue(new Error("offline"));
    render(<NotificationProvider role="STUDENT"><Probe /></NotificationProvider>);
    await act(async () => { await Promise.resolve(); });
    expect(screen.getByTestId("count").textContent).toBe("0");
  });
});

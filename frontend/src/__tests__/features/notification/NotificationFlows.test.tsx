import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
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

/** Dropdown luôn chạy dưới NotificationProvider như trong DashboardShell: số chưa đọc và danh sách do context quản trị */
function renderDropdown(ui: React.ReactElement, unread = 0) {
  const summary = vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({
    success: true,
    data: { total: unread, byCategory: {} },
  });
  render(<NotificationProvider role="STUDENT">{ui}</NotificationProvider>);
  return (total: number) => summary.mockResolvedValue({ success: true, data: { total, byCategory: {} } });
}

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  mockPush.mockReset();
});

/**
 * Mọi (type, targetType) mà backend thực sự ghi vào bảng thông báo, kèm đích điều hướng mong đợi
 * theo từng vai trò. Đồng bộ tuyệt đối với các listener phía backend.
 */
describe("Điều hướng theo từng loại thông báo của backend", () => {
  const schedule = (suffix = "") => ({
    STUDENT: `/student/schedule${suffix}`,
    PARENT: `/parent/schedule${suffix}`,
    TUTOR: `/tutor/schedule${suffix}`,
    ADMIN: "/admin/dashboard",
  });
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
    ["SESSION_OUTCOME_OVERDUE", "SESSION", {
      STUDENT: "/student/schedule?sessionId=t-1",
      PARENT: "/parent/schedule?sessionId=t-1",
      TUTOR: "/tutor/schedule?sessionId=t-1&action=outcome",
      ADMIN: "/admin/dashboard",
    }],
    ["SESSION_FEEDBACK_REMINDER", "SESSION", {
      STUDENT: "/student/schedule?sessionId=t-1",
      PARENT: "/parent/schedule?sessionId=t-1",
      TUTOR: "/tutor/schedule?sessionId=t-1&action=outcome",
      ADMIN: "/admin/dashboard",
    }],
    // Thay đổi hàng loạt trỏ về lớp, không có một buổi cụ thể => trang lịch
    ["SESSION_RECURRING_CHANGED", "CLASS", schedule()],
    ["SESSION_BULK_CANCELLED", "CLASS", schedule()],
    ["SESSION_BATCH_SCHEDULED", "CLASS", schedule()],
    ["SESSION_BATCH_REJECTED", "CLASS", schedule()],
    ["MILESTONE_ACHIEVED", "MILESTONE", {
      STUDENT: "/student/progress",
      PARENT: "/parent/progress",
      TUTOR: "/tutor/classes",
      ADMIN: "/admin/dashboard",
    }],
    ["PERIOD_REPORT_SENT", "PERIOD_REPORT", {
      STUDENT: "/student/progress",
      PARENT: "/parent/progress",
      TUTOR: "/tutor/classes",
      ADMIN: "/admin/dashboard",
    }],
    ["REVIEW_RECEIVED", "REVIEW", {
      STUDENT: "/student/classes",
      PARENT: "/parent/dashboard",
      TUTOR: "/tutor/reviews",
      ADMIN: "/admin/dashboard",
    }],
    ["REVIEW_UPDATED", "REVIEW", {
      STUDENT: "/student/classes",
      PARENT: "/parent/dashboard",
      TUTOR: "/tutor/reviews",
      ADMIN: "/admin/dashboard",
    }],
    ["VERIFICATION_APPROVED", "VERIFICATION", {
      STUDENT: "/student/dashboard",
      PARENT: "/parent/dashboard",
      TUTOR: "/tutor/verification",
      ADMIN: "/admin/verification-queue",
    }],
    ["VERIFICATION_REVOKED", "VERIFICATION", {
      STUDENT: "/student/dashboard",
      PARENT: "/parent/dashboard",
      TUTOR: "/tutor/verification",
      ADMIN: "/admin/verification-queue",
    }],
    ["LINK_INVITED", "LINK_INVITATION", {
      STUDENT: "/student/family?invitationId=t-1",
      PARENT: "/parent/children?invitationId=t-1",
      TUTOR: "/tutor/dashboard",
      ADMIN: "/admin/family-links",
    }],
    // Học sinh xem yêu cầu kết nối ở tab yêu cầu, chấp nhận thì tới lớp
    ["CONNECTION_REQUESTED", "CONNECTION_REQUEST", {
      STUDENT: "/student/tutors?tab=requests",
      PARENT: "/parent/tutors",
      TUTOR: "/tutor/requests?requestId=t-1",
      ADMIN: "/admin/dashboard",
    }],
    ["CONNECTION_REJECTED", "CONNECTION_REQUEST", {
      STUDENT: "/student/tutors?tab=requests",
      PARENT: "/parent/tutors",
      TUTOR: "/tutor/requests?requestId=t-1",
      ADMIN: "/admin/dashboard",
    }],
    ["CONNECTION_ACCEPTED", "CLASS", {
      STUDENT: "/student/classes",
      PARENT: "/parent/children",
      TUTOR: "/tutor/classes",
      ADMIN: "/admin/dashboard",
    }],
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
    expect(getNotificationRoute(item({ type: "UNKNOWN", targetType: undefined }), "ADMIN")).toBe("/admin/dashboard");
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

  it("hiển thị lỗi khi tải thất bại ban đầu", async () => {
    vi.spyOn(notificationApi, "getNotifications").mockRejectedValue(new Error("Mất kết nối"));
    renderDropdown(<NotificationDropdown role="STUDENT" />);
    expect(await screen.findByText("Mất kết nối")).toBeDefined();
  });

  it("bấm vào thông báo chưa đọc: điều hướng ngay, đánh dấu đã đọc ngầm và đóng dropdown", async () => {
    const markRead = vi.spyOn(notificationApi, "markAsRead").mockResolvedValue({ success: true } as never);
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(
      page([item({ notificationId: "m1", type: "MILESTONE_ACHIEVED", targetType: "MILESTONE", targetId: undefined, title: "Chúc mừng! Đạt cột mốc mới" })]),
    );
    const onClose = vi.fn();
    const serverNowHasUnread = renderDropdown(<NotificationDropdown role="PARENT" onClose={onClose} />, 1);
    serverNowHasUnread(0);

    await userEvent.click(await screen.findByText("Chúc mừng! Đạt cột mốc mới"));

    // AC9: router.push được gọi ngay lập tức
    expect(mockPush).toHaveBeenCalledWith("/parent/progress");
    expect(onClose).toHaveBeenCalled();

    // markAsRead được gọi chạy ngầm
    await waitFor(() => expect(markRead).toHaveBeenCalledWith("m1"));

    // Số chưa đọc trên chuông trừ ngay (cùng một nguồn với TopHeader)
    expect(await screen.findByText("Chưa đọc (0)")).toBeDefined();
  });

  it("bấm vào thông báo đã đọc: không gọi markAsRead nhưng vẫn điều hướng", async () => {
    const markRead = vi.spyOn(notificationApi, "markAsRead").mockResolvedValue({ success: true } as never);
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(
      page([item({ notificationId: "r1", isRead: true, type: "REVIEW_RECEIVED", targetType: "REVIEW", targetId: undefined, title: "Đánh giá mới" })]),
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

    await waitFor(() => expect(markAll).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.queryByText("Đọc tất cả")).toBeNull());
  });

  it("tab Chưa đọc gọi server với unreadOnly=true theo chuẩn D1", async () => {
    const unreadItem = item({ notificationId: "u1", isRead: false, title: "Mục chưa đọc" });
    const readItem = item({ notificationId: "r1", isRead: true, title: "Mục đã đọc" });
    const spy = vi.spyOn(notificationApi, "getNotifications")
      .mockResolvedValueOnce(page([unreadItem, readItem]))
      .mockResolvedValueOnce(page([unreadItem], 1));
    renderDropdown(<NotificationDropdown role="STUDENT" />, 1);
    await screen.findByText("Mục chưa đọc");
    expect(screen.getByText("Mục đã đọc")).toBeDefined();

    await userEvent.click(screen.getByText(/Chưa đọc \(/));

    await waitFor(() => {
      expect(spy).toHaveBeenCalledWith(true, 0, 20);
      expect(screen.getByText("Mục chưa đọc")).toBeDefined();
      expect(screen.queryByText("Mục đã đọc")).toBeNull();
    });
  });

  it("F-15: đủ 20 mục thì có nút Xem thêm và mở rộng danh sách", async () => {
    const twentyFive = Array.from({ length: 25 }, (_, i) => item({ notificationId: `n${i}`, title: `Tin ${i}` }));
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(page(twentyFive));
    renderDropdown(<NotificationDropdown role="STUDENT" />, 25);

    const seeMore = await screen.findByText("Xem thêm");
    expect(seeMore).toBeDefined();

    await userEvent.click(seeMore);

    // Mục thứ 24 xuất hiện sau khi mở rộng
    expect(await screen.findByText("Tin 24")).toBeDefined();
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

  it("tự làm mới danh sách sau 15 giây từ Provider duy nhất (không trùng lặp)", async () => {
    vi.useFakeTimers();
    const spy = vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(page([]));
    renderDropdown(<NotificationDropdown role="STUDENT" />);
    await act(async () => { await Promise.resolve(); });
    const before = spy.mock.calls.length;

    await act(async () => { await vi.advanceTimersByTimeAsync(15_000); });

    // Chỉ có Provider gọi duy nhất 1 lần trong chu kỳ 15s
    expect(spy.mock.calls.length).toBe(before + 1);
  });

  it("F1a: bấm 'Xem thêm' tải trang mới, chu kỳ poll nền 15s KHÔNG xóa trang đã tải", async () => {
    vi.useFakeTimers();
    const page0 = Array.from({ length: 20 }, (_, i) => item({ notificationId: `p0-${i}`, title: `Trang0-${i}` }));
    const page1 = Array.from({ length: 5 }, (_, i) => item({ notificationId: `p1-${i}`, title: `Trang1-${i}` }));

    const getNotifs = vi.spyOn(notificationApi, "getNotifications")
      // Lần đầu tải trang 0
      .mockResolvedValueOnce({
        success: true,
        data: { items: page0, page: 0, size: 20, totalElements: 25, totalPages: 2, unreadCount: 0 },
      })
      // Bấm xem thêm tải trang 1
      .mockResolvedValueOnce({
        success: true,
        data: { items: page1, page: 1, size: 20, totalElements: 25, totalPages: 2, unreadCount: 0 },
      })
      // Poll nền chu kỳ tiếp theo
      .mockResolvedValue({
        success: true,
        data: { items: page0, page: 0, size: 20, totalElements: 25, totalPages: 2, unreadCount: 0 },
      });

    renderDropdown(<NotificationDropdown role="STUDENT" />);
    await act(async () => { await Promise.resolve(); });

    expect(screen.getByText("Trang0-0")).toBeDefined();
    const seeMore = screen.getByText("Xem thêm");
    await act(async () => {
      fireEvent.click(seeMore);
      await Promise.resolve();
    });

    // Mục ở trang 1 đã hiện
    expect(screen.getByText("Trang1-0")).toBeDefined();

    // Poll nền sau 15 giây
    await act(async () => {
      await vi.advanceTimersByTimeAsync(15_000);
      await Promise.resolve();
    });

    // Mục ở trang 1 VẪN CÒN, không bị xóa co lại
    expect(screen.getByText("Trang1-0")).toBeDefined();
  });

  it("F1b: tab Chưa đọc gọi server với unreadOnly=true để tải các thông báo chưa đọc", async () => {
    const unreadItem = item({ notificationId: "unread-99", title: "Thông báo chưa đọc trang 2", isRead: false });

    const getNotifs = vi.spyOn(notificationApi, "getNotifications")
      // Tab tất cả (unreadOnly = false)
      .mockResolvedValueOnce(page([item({ notificationId: "n0", isRead: true })]))
      // Tab chưa đọc (unreadOnly = true)
      .mockResolvedValueOnce(page([unreadItem], 1));

    renderDropdown(<NotificationDropdown role="STUDENT" />, 1);
    await act(async () => { await Promise.resolve(); });

    // Chuyển sang tab "Chưa đọc"
    const unreadTab = screen.getByRole("button", { name: /Chưa đọc/i });
    await userEvent.click(unreadTab);

    await waitFor(() => {
      expect(getNotifs).toHaveBeenCalledWith(true, 0, 20);
      expect(screen.getByText("Thông báo chưa đọc trang 2")).toBeDefined();
    });
  });

  it("F1c: bấm đọc thông báo trong tab Chưa đọc thì item rời khỏi danh sách và badge giảm 1", async () => {
    const unreadItem = item({ notificationId: "u-1", title: "Cần xử lý ngay", isRead: false });

    vi.spyOn(notificationApi, "getNotifications")
      .mockResolvedValueOnce(page([]))
      .mockResolvedValueOnce(page([unreadItem], 1));
    vi.spyOn(notificationApi, "markAsRead").mockResolvedValue({ success: true } as never);

    renderDropdown(<NotificationDropdown role="STUDENT" />, 1);
    await act(async () => { await Promise.resolve(); });

    // Sang tab Chưa đọc
    const unreadTab = screen.getByRole("button", { name: /Chưa đọc/i });
    await userEvent.click(unreadTab);

    const targetItem = await screen.findByText("Cần xử lý ngay");
    await userEvent.click(targetItem);

    // Item phải biến mất khỏi tab Chưa đọc
    await waitFor(() => {
      expect(screen.queryByText("Cần xử lý ngay")).toBeNull();
    });
  });
});

describe("NotificationProvider: chấm cảnh báo sidebar và bộ đếm", () => {
  function Probe() {
    const {
      unreadCount,
      unreadCategoryMap,
      markAllAsRead,
      markAsRead,
      items,
      error,
      loadMore,
      hasMore,
      refresh,
    } = useNotification() as unknown as {
      unreadCount: number;
      unreadCategoryMap: Record<string, boolean>;
      markAllAsRead: () => Promise<void>;
      markAsRead: (id: string) => Promise<void>;
      items: NotificationItem[];
      error: string | null;
      loadMore?: () => Promise<void>;
      hasMore?: boolean;
      refresh: () => Promise<void>;
    };
    return (
      <div>
        <span data-testid="count">{unreadCount}</span>
        <span data-testid="cats">{Object.keys(unreadCategoryMap).sort().join(",")}</span>
        <span data-testid="items-count">{items.length}</span>
        <span data-testid="error">{error || ""}</span>
        <span data-testid="has-more">{hasMore ? "yes" : "no"}</span>
        <ul data-testid="items-status">
          {items.map((i) => (
            <li key={i.notificationId} data-testid={`status-${i.notificationId}`}>
              {i.isRead ? "read" : "unread"}
            </li>
          ))}
        </ul>
        <button onClick={() => void markAllAsRead()}>all</button>
        <button onClick={() => void markAsRead("n1")}>one</button>
        <button onClick={() => void refresh()}>ref</button>
        {loadMore && <button onClick={() => void loadMore()}>more</button>}
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

  it("AC3: Lỗi poll nền KHÔNG xóa danh sách đang hiển thị; chỉ lỗi tải ban đầu mới hiện trạng thái lỗi", async () => {
    vi.useFakeTimers();
    // Lần tải đầu thành công có 2 thông báo
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({
      success: true,
      data: { total: 2, byCategory: {} },
    });
    const getNotifs = vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(
      page([item({ notificationId: "n1" }), item({ notificationId: "n2" })]),
    );

    render(<NotificationProvider role="STUDENT"><Probe /></NotificationProvider>);
    await act(async () => { await Promise.resolve(); });

    expect(screen.getByTestId("items-count").textContent).toBe("2");
    expect(screen.getByTestId("error").textContent).toBe("");

    // Lần poll tiếp theo bị lỗi mạng (poll nền)
    getNotifs.mockRejectedValueOnce(new Error("Mất kết nối nền"));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(15_000);
    });

    // Danh sách 2 mục cũ VẪN giữ nguyên, không bị xóa, lỗi không hiển thị đè màn hình
    expect(screen.getByTestId("items-count").textContent).toBe("2");
    expect(screen.getByTestId("error").textContent).toBe("");
  });

  it("Lỗi #1: in-flight poll chậm không ghi đè trạng thái optimistic của markAsRead", async () => {
    let resolveSlowPoll: (val: unknown) => void = () => {};
    const slowPollPromise = new Promise((resolve) => {
      resolveSlowPoll = resolve;
    });

    vi.spyOn(notificationApi, "getUnreadSummary")
      .mockResolvedValueOnce({ success: true, data: { total: 1, byCategory: {} } })
      .mockResolvedValueOnce({ success: true, data: { total: 1, byCategory: {} } })
      .mockResolvedValue({ success: true, data: { total: 0, byCategory: {} } });
    // Lần tải đầu: n1 là unread
    const getNotifs = vi.spyOn(notificationApi, "getNotifications").mockResolvedValueOnce(
      page([item({ notificationId: "n1", isRead: false })])
    );
    vi.spyOn(notificationApi, "markAsRead").mockResolvedValue({ success: true } as never);

    render(<NotificationProvider role="STUDENT"><Probe /></NotificationProvider>);
    await waitFor(() => expect(screen.getByTestId("status-n1").textContent).toBe("unread"));
    expect(screen.getByTestId("count").textContent).toBe("1");

    // Lần poll tiếp theo chạy chậm (in-flight)
    getNotifs.mockImplementationOnce(() => slowPollPromise as never);

    // Kích hoạt một vòng poll mới bắt đầu bay (in-flight)
    void userEvent.click(screen.getByText("ref"));

    // Người dùng bấm markAsRead("n1") trong khi poll đang bay
    await userEvent.click(screen.getByText("one"));

    // Optimistic update phải có hiệu lực ngay
    expect(screen.getByTestId("status-n1").textContent).toBe("read");
    expect(screen.getByTestId("count").textContent).toBe("0");

    // Bây giờ in-flight poll cũ hoàn tất trả về dữ liệu cũ (n1 vẫn chưa đọc)
    await act(async () => {
      resolveSlowPoll(page([item({ notificationId: "n1", isRead: false })]));
      await Promise.resolve();
    });

    // Kết quả stale KHÔNG được ghi đè: n1 vẫn phải là read, count vẫn là 0!
    expect(screen.getByTestId("status-n1").textContent).toBe("read");
    expect(screen.getByTestId("count").textContent).toBe("0");
  });

  it("Lỗi #1c: rollback trạng thái optimistic khi PATCH markAsRead thất bại", async () => {
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({
      success: true,
      data: { total: 1, byCategory: {} },
    });
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(
      page([item({ notificationId: "n1", isRead: false })])
    );
    // Giả lập PATCH thất bại
    vi.spyOn(notificationApi, "markAsRead").mockRejectedValue(new Error("Lỗi mạng"));

    render(<NotificationProvider role="STUDENT"><Probe /></NotificationProvider>);
    await waitFor(() => expect(screen.getByTestId("status-n1").textContent).toBe("unread"));

    // Bấm đọc
    await userEvent.click(screen.getByText("one"));

    // Sau khi PATCH fail, n1 được rollback lại unread và count khôi phục
    await waitFor(() => {
      expect(screen.getByTestId("status-n1").textContent).toBe("unread");
      expect(screen.getByTestId("count").textContent).toBe("1");
    });
  });

  it("Lỗi #2: loadMore() thực hiện phân trang server và gộp danh sách", async () => {
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({
      success: true,
      data: { total: 2, byCategory: {} },
    });
    const getNotifs = vi.spyOn(notificationApi, "getNotifications")
      // Trang 0
      .mockResolvedValueOnce({
        success: true,
        data: {
          items: [item({ notificationId: "n1" })],
          page: 0,
          size: 1,
          totalElements: 2,
          totalPages: 2,
          unreadCount: 2,
        },
      })
      // Trang 1
      .mockResolvedValueOnce({
        success: true,
        data: {
          items: [item({ notificationId: "n2" })],
          page: 1,
          size: 1,
          totalElements: 2,
          totalPages: 2,
          unreadCount: 2,
        },
      });

    render(<NotificationProvider role="STUDENT"><Probe /></NotificationProvider>);
    await waitFor(() => expect(screen.getByTestId("items-count").textContent).toBe("1"));
    expect(screen.getByTestId("has-more").textContent).toBe("yes");

    // Bấm loadMore
    await userEvent.click(screen.getByText("more"));

    // Danh sách được gộp thành 2 items
    await waitFor(() => expect(screen.getByTestId("items-count").textContent).toBe("2"));
    expect(getNotifs).toHaveBeenCalledWith(false, 1, 20);
    expect(screen.getByTestId("has-more").textContent).toBe("no");
  });

  it("Lỗi #3: xử lý khi API trả về success: false thay vì throw error", async () => {
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({
      success: true,
      data: { total: 0, byCategory: {} },
    });
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue({
      success: false,
      message: "Lỗi kết nối cơ sở dữ liệu",
    });

    render(<NotificationProvider role="STUDENT"><Probe /></NotificationProvider>);

    // Error được cập nhật để dropdown hiển thị AlertCircle
    await waitFor(() => {
      expect(screen.getByTestId("error").textContent).toBe("Lỗi kết nối cơ sở dữ liệu");
    });
  });

  it("F3: bấm markAsRead hai lần liên tiếp cho cùng một id không làm trừ badge lần 2", async () => {
    const summarySpy = vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({
      success: true,
      data: { total: 1, byCategory: {} },
    });
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(
      page([item({ notificationId: "n1", isRead: false })])
    );
    vi.spyOn(notificationApi, "markAsRead").mockResolvedValue({ success: true } as never);

    render(<NotificationProvider role="STUDENT"><Probe /></NotificationProvider>);
    await waitFor(() => expect(screen.getByTestId("count").textContent).toBe("1"));

    // Server phản ánh việc đã đọc: summary sau mutation trả về 0
    summarySpy.mockResolvedValue({ success: true, data: { total: 0, byCategory: {} } });

    // Bấm liên tiếp 2 lần
    const btn = screen.getByText("one");
    await userEvent.click(btn);
    await userEvent.click(btn);

    // unreadCount vẫn là 0, không bị âm (-1)
    expect(screen.getByTestId("count").textContent).toBe("0");
  });

  it("F4: markAllAsRead rollback không ghi đè mất thông báo mới tới trong lúc chờ", async () => {
    let rejectMarkAll: () => void = () => {};
    const markAllPromise = new Promise((_, reject) => {
      rejectMarkAll = () => reject(new Error("Lỗi mạng"));
    });

    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({
      success: true,
      data: { total: 1, byCategory: {} },
    });
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(
      page([item({ notificationId: "n1", isRead: false })])
    );
    vi.spyOn(notificationApi, "markAllAsRead").mockImplementation(() => markAllPromise as never);

    render(<NotificationProvider role="STUDENT"><Probe /></NotificationProvider>);
    await waitFor(() => expect(screen.getByTestId("count").textContent).toBe("1"));

    // Bấm đọc tất cả
    await userEvent.click(screen.getByText("all"));
    expect(screen.getByTestId("count").textContent).toBe("0");

    // Server fail
    await act(async () => {
      rejectMarkAll();
      await Promise.resolve();
    });

    // Rollback lại số lượng đã trừ lúc trước (1)
    await waitFor(() => {
      expect(screen.getByTestId("count").textContent).toBe("1");
      expect(screen.getByTestId("status-n1").textContent).toBe("unread");
    });
  });

  it("B1: polling chu kỳ sau cập nhật isRead của item hiện có và chèn brandNew lên đầu", async () => {
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({
      success: true,
      data: { total: 2, byCategory: {} },
    });
    vi.spyOn(notificationApi, "getNotifications")
      .mockResolvedValueOnce(page([
        item({ notificationId: "n1", isRead: false, createdAt: "2026-10-18T10:00:00Z" }),
        item({ notificationId: "n2", isRead: false, createdAt: "2026-10-18T09:00:00Z" }),
      ]))
      .mockResolvedValueOnce(page([
        item({ notificationId: "n3", isRead: false, createdAt: "2026-10-18T11:00:00Z" }),
        item({ notificationId: "n1", isRead: true, createdAt: "2026-10-18T10:00:00Z" }),
      ]));

    render(<NotificationProvider role="STUDENT"><Probe /></NotificationProvider>);
    await waitFor(() => expect(screen.getByTestId("status-n1").textContent).toBe("unread"));
    expect(screen.getByTestId("status-n2").textContent).toBe("unread");

    // Kích hoạt poll tiếp theo qua nút ref
    await userEvent.click(screen.getByText("ref"));

    await waitFor(() => {
      expect(screen.getByTestId("status-n1").textContent).toBe("read");
      expect(screen.getByTestId("status-n3")).toBeDefined();
      expect(screen.getByTestId("status-n2").textContent).toBe("unread");
    });
  });

  it("B2: khi mutation hoàn tất, tự động revalidate nếu nhịp poll trước đó bị hoãn", async () => {
    let resolveMark: () => void = () => {};
    const markPromise = new Promise((resolve) => {
      resolveMark = () => resolve({ success: true, data: { id: "n1", read: true } } as never);
    });

    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({
      success: true,
      data: { total: 1, byCategory: {} },
    });
    const notifSpy = vi.spyOn(notificationApi, "getNotifications")
      .mockResolvedValueOnce(page([item({ notificationId: "n1", isRead: false })]))
      .mockResolvedValue(page([item({ notificationId: "n1", isRead: true })]));
    vi.spyOn(notificationApi, "markAsRead").mockImplementation(() => markPromise as never);

    render(<NotificationProvider role="STUDENT"><Probe /></NotificationProvider>);
    await waitFor(() => expect(screen.getByTestId("status-n1").textContent).toBe("unread"));

    // Bắt đầu mutation
    await userEvent.click(screen.getByText("one"));
    expect(screen.getByTestId("status-n1").textContent).toBe("read");

    // Thử kích hoạt poll trong khi mutation đang bay: bị hoãn và đánh dấu needsRevalidate
    await userEvent.click(screen.getByText("ref"));

    // Giải phóng mutation
    await act(async () => {
      resolveMark();
      await Promise.resolve();
    });

    // Sau khi mutation kết thúc, poll được tự động kích hoạt lại để revalidate
    await waitFor(() => {
      expect(notifSpy).toHaveBeenCalledTimes(2);
    });
  });
});

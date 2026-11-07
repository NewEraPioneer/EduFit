import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { ScheduleCalendarView } from "@/features/scheduling";
import { schedulingApi } from "@/features/scheduling/api/schedulingApi";
import { NotificationProvider, notificationApi } from "@/features/notification";
import type { NotificationItem } from "@/features/notification";
import { NOTIFICATION_TEST_IDS } from "@/__tests__/fixtures/notification-test-fixtures";

let mockSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  useSearchParams: () => mockSearchParams,
  usePathname: () => "/student/schedule",
}));

vi.mock("@/context/AuthContext", () => ({
  useAuth: () => ({
    user: { id: "user-1", fullName: "Học sinh", email: "a@b.c", role: "STUDENT", status: "ACTIVE" },
    isLoading: false,
    error: null,
  }),
}));

const SESSION_ID = NOTIFICATION_TEST_IDS.SESSION_1;
// Một tuần rất xa tuần hiện tại để chứng minh lịch tự nhảy tuần
const FAR_START = "2031-03-05T08:00:00.000Z";

function sessionNotification(over: Partial<NotificationItem> = {}): NotificationItem {
  return {
    notificationId: "noti-1",
    type: "SESSION_RESCHEDULED",
    title: "Lịch học đã thay đổi",
    shortText: "Thời gian buổi học đã được cập nhật lại",
    targetType: "SESSION",
    targetId: SESSION_ID,
    isRead: false,
    createdAt: new Date().toISOString(),
    ...over,
  };
}

function mountCalendar() {
  return render(
    <NotificationProvider role="STUDENT">
      <ScheduleCalendarView role="STUDENT" availabilitySlots={[]} />
    </NotificationProvider>,
  );
}

describe("Lịch học - chỉ dẫn thay đổi từ thông báo", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockSearchParams = new URLSearchParams();
    vi.spyOn(schedulingApi, "getCalendar").mockResolvedValue({ success: true, data: [] });
    vi.spyOn(schedulingApi, "getSessionDetail").mockResolvedValue({
      success: true,
      data: {
        sessionId: SESSION_ID,
        classId: NOTIFICATION_TEST_IDS.CLASS_1,
        tutorId: "t-1",
        studentId: "s-1",
        startAt: FAR_START,
        endAt: "2031-03-05T09:30:00.000Z",
        mode: "ONLINE",
        status: "SCHEDULED",
        isLateCancel: false,
        proposedBy: "t-1",
        expiresAt: FAR_START,
        version: 1,
        createdAt: FAR_START,
      },
    });
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue({
      success: true,
      data: { total: 1, byCategory: { SCHEDULE: 1 } },
    });
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue({
      success: true,
      data: { items: [sessionNotification()], page: 0, size: 20, totalElements: 1, totalPages: 1, unreadCount: 1 },
    });
    vi.spyOn(notificationApi, "markAsRead").mockResolvedValue({ success: true } as never);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("hiện banner đỏ chỉ rõ buổi học vừa thay đổi dù người dùng không bấm thông báo", async () => {
    mountCalendar();

    const banner = await screen.findByRole("region", { name: "Thay đổi lịch học chưa xem" });
    expect(banner).toHaveTextContent("Lịch học đã thay đổi");
    expect(banner).toHaveTextContent("1 buổi học vừa thay đổi");
  });

  it("bấm 'Xem buổi học' mở đúng buổi, nhảy tới tuần chứa buổi đó và đánh dấu đã đọc", async () => {
    mountCalendar();
    const button = await screen.findByRole("button", { name: /Xem buổi học/ });
    const callsBefore = vi.mocked(schedulingApi.getCalendar).mock.calls.length;

    fireEvent.click(button);

    await waitFor(() => expect(notificationApi.markAsRead).toHaveBeenCalledWith("noti-1"));
    await waitFor(() => {
      const calls = vi.mocked(schedulingApi.getCalendar).mock.calls;
      expect(calls.length).toBeGreaterThan(callsBefore);
      const lastFrom = new Date(calls[calls.length - 1][0].from as string);
      expect(lastFrom.getUTCFullYear()).toBe(2031);
    });
    await waitFor(() =>
      expect(screen.queryByRole("region", { name: "Thay đổi lịch học chưa xem" })).not.toBeInTheDocument(),
    );
  });

  it("mở bằng deep link cũng nhảy tới tuần chứa buổi học", async () => {
    mockSearchParams = new URLSearchParams(`sessionId=${SESSION_ID}`);
    mountCalendar();

    await waitFor(() => {
      const calls = vi.mocked(schedulingApi.getCalendar).mock.calls;
      const lastFrom = new Date(calls[calls.length - 1][0].from as string);
      expect(lastFrom.getUTCFullYear()).toBe(2031);
    });
  });
});

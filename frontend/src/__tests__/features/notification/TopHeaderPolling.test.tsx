import { afterEach, describe, expect, it, vi } from "vitest";
import { act, render } from "@testing-library/react";
import { TopHeader } from "@/shared/ui/TopHeader";
import { NotificationProvider, notificationApi } from "@/features/notification";

vi.mock("@/context/ThemeContext", () => ({
  useTheme: () => ({ theme: "light", setTheme: vi.fn() }),
}));

describe("chuông thông báo", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("kiểm tra ngay, lặp mỗi 15 giây và dừng khi unmount", async () => {
    vi.useFakeTimers();
    const getSummary = vi
      .spyOn(notificationApi, "getUnreadSummary")
      .mockResolvedValue({
        success: true,
        data: { total: 2, byCategory: { SCHEDULE: 2 } },
      });
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue({
      success: true,
      data: {
        items: [],
        page: 0,
        size: 5,
        totalElements: 0,
        totalPages: 0,
        unreadCount: 2,
      },
    });

    const view = render(
      <NotificationProvider>
        <TopHeader
          user={{ fullName: "Minh", email: "minh@example.com", role: "STUDENT" }}
          onLogout={vi.fn()}
        />
      </NotificationProvider>,
    );
    await act(async () => {
      await Promise.resolve();
    });
    expect(getSummary).toHaveBeenCalledTimes(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(15_000);
    });
    expect(getSummary).toHaveBeenCalledTimes(2);

    view.unmount();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(15_000);
    });
    expect(getSummary).toHaveBeenCalledTimes(2);
  });
});

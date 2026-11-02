import { afterEach, describe, expect, it, vi } from "vitest";
import { act, render } from "@testing-library/react";
import { TopHeader } from "@/shared/ui/TopHeader";
import { notificationApi } from "@/features/notification";

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
    const getUnreadCount = vi
      .spyOn(notificationApi, "getUnreadCount")
      .mockResolvedValue({ success: true, data: { unreadCount: 2 } });
    const view = render(
      <TopHeader
        user={{ fullName: "Minh", email: "minh@example.com", role: "STUDENT" }}
        onLogout={vi.fn()}
      />,
    );
    await act(async () => {
      await Promise.resolve();
    });
    expect(getUnreadCount).toHaveBeenCalledTimes(1);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(15_000);
    });
    expect(getUnreadCount).toHaveBeenCalledTimes(2);
    view.unmount();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(15_000);
    });
    expect(getUnreadCount).toHaveBeenCalledTimes(2);
  });
});

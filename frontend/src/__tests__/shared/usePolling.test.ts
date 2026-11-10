import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { usePolling } from "@/shared/hooks/usePolling";
import { NOTIFICATION_TEST_TIMEOUTS } from "@/__tests__/fixtures/notification-test-fixtures";

describe("usePolling hook", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("gọi ngay khi mount và lặp định kỳ theo interval", async () => {
    const onPoll = vi.fn().mockResolvedValue(undefined);

    renderHook(() =>
      usePolling({
        intervalMs: NOTIFICATION_TEST_TIMEOUTS.POLL_INTERVAL_MS,
        onPoll,
      })
    );

    // Initial immediate call
    expect(onPoll).toHaveBeenCalledTimes(1);

    // Advance by interval
    await act(async () => {
      await vi.advanceTimersByTimeAsync(NOTIFICATION_TEST_TIMEOUTS.POLL_INTERVAL_MS);
    });
    expect(onPoll).toHaveBeenCalledTimes(2);

    // Advance again
    await act(async () => {
      await vi.advanceTimersByTimeAsync(NOTIFICATION_TEST_TIMEOUTS.POLL_INTERVAL_MS);
    });
    expect(onPoll).toHaveBeenCalledTimes(3);
  });

  it("tạm dừng poll khi tab bị ẩn (document.hidden = true)", async () => {
    const onPoll = vi.fn().mockResolvedValue(undefined);

    renderHook(() =>
      usePolling({
        intervalMs: NOTIFICATION_TEST_TIMEOUTS.POLL_INTERVAL_MS,
        onPoll,
      })
    );

    expect(onPoll).toHaveBeenCalledTimes(1);

    // Simulate tab hidden
    Object.defineProperty(document, "hidden", {
      configurable: true,
      get: () => true,
    });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(NOTIFICATION_TEST_TIMEOUTS.POLL_INTERVAL_MS * 2);
    });

    // Should NOT have run while hidden
    expect(onPoll).toHaveBeenCalledTimes(1);

    // Simulate tab visible again
    Object.defineProperty(document, "hidden", {
      configurable: true,
      get: () => false,
    });

    await act(async () => {
      document.dispatchEvent(new Event("visibilitychange"));
    });

    // Should run immediately upon tab becoming visible
    expect(onPoll).toHaveBeenCalledTimes(2);
  });

  it("chống chồng chéo request khi poll trước đó đang in-flight", async () => {
    let resolveSlowPoll: () => void = () => {};
    const slowPollPromise = new Promise<void>((resolve) => {
      resolveSlowPoll = resolve;
    });

    const onPoll = vi.fn().mockImplementation(() => slowPollPromise);

    renderHook(() =>
      usePolling({
        intervalMs: NOTIFICATION_TEST_TIMEOUTS.POLL_INTERVAL_MS,
        onPoll,
      })
    );

    expect(onPoll).toHaveBeenCalledTimes(1);

    // Interval fires while first poll is still pending
    await act(async () => {
      await vi.advanceTimersByTimeAsync(NOTIFICATION_TEST_TIMEOUTS.POLL_INTERVAL_MS);
    });

    // Second call should have been blocked by in-flight guard
    expect(onPoll).toHaveBeenCalledTimes(1);

    // Resolve in-flight request
    await act(async () => {
      resolveSlowPoll();
    });

    // Next interval after resolution succeeds
    onPoll.mockResolvedValueOnce(undefined);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(NOTIFICATION_TEST_TIMEOUTS.POLL_INTERVAL_MS);
    });
    expect(onPoll).toHaveBeenCalledTimes(2);
  });

  it("dọn dẹp sạch khi unmount và không cập nhật sau khi hủy", async () => {
    const onPoll = vi.fn().mockResolvedValue(undefined);

    const { unmount } = renderHook(() =>
      usePolling({
        intervalMs: NOTIFICATION_TEST_TIMEOUTS.POLL_INTERVAL_MS,
        onPoll,
      })
    );

    expect(onPoll).toHaveBeenCalledTimes(1);

    unmount();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(NOTIFICATION_TEST_TIMEOUTS.POLL_INTERVAL_MS * 3);
    });

    expect(onPoll).toHaveBeenCalledTimes(1);
  });

  it("refresh({ force: true }) cho phép xếp hàng và kích hoạt bù ngay khi in-flight hoàn tất", async () => {
    let resolveSlowPoll: () => void = () => {};
    const slowPollPromise = new Promise<void>((resolve) => {
      resolveSlowPoll = resolve;
    });

    const onPoll = vi.fn().mockImplementation(() => slowPollPromise);

    const { result } = renderHook(() =>
      usePolling({
        intervalMs: NOTIFICATION_TEST_TIMEOUTS.POLL_INTERVAL_MS,
        onPoll,
      })
    );

    expect(onPoll).toHaveBeenCalledTimes(1);

    // Khi poll 1 đang bay, gọi refresh({ force: true })
    let refreshPromise: Promise<void> | undefined;
    await act(async () => {
      refreshPromise = result.current.refresh({ force: true });
    });

    // Giải phóng poll 1
    await act(async () => {
      resolveSlowPoll();
      await refreshPromise;
    });

    // onPoll phải được gọi lại lần 2 do force refresh đã xếp hàng/kích hoạt bù
    expect(onPoll).toHaveBeenCalledTimes(2);
  });

  it("C1: bắt lỗi từ onPoll để không làm đứt gãy vòng lặp timer và tiếp tục chu kỳ sau", async () => {
    const onPoll = vi
      .fn()
      .mockRejectedValueOnce(new Error("Network offline"))
      .mockResolvedValue(undefined);

    renderHook(() =>
      usePolling({
        intervalMs: NOTIFICATION_TEST_TIMEOUTS.POLL_INTERVAL_MS,
        onPoll,
      })
    );

    expect(onPoll).toHaveBeenCalledTimes(1);

    // Chu kỳ tiếp theo vẫn được kích hoạt bình thường dù lần trước bị reject
    await act(async () => {
      await vi.advanceTimersByTimeAsync(NOTIFICATION_TEST_TIMEOUTS.POLL_INTERVAL_MS);
    });

    expect(onPoll).toHaveBeenCalledTimes(2);
  });
});


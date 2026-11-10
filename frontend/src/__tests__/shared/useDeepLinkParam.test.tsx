import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useDeepLinkParam, useDeepLinkParams } from "@/shared/hooks/useDeepLinkParam";
import { mockRouter } from "../../../vitest.setup";

describe("useDeepLinkParam & useDeepLinkParams hooks", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockRouter.replace.mockClear();
    window.history.replaceState(null, "", "/tutor/schedule");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("trả về null và không gọi router.replace khi không có tham số", () => {
    const { result } = renderHook(() => useDeepLinkParam("sessionId"));
    expect(result.current).toBeNull();
    expect(mockRouter.replace).not.toHaveBeenCalled();
  });

  it("đọc tham số, trả về giá trị và gọi router.replace để xóa tham số khỏi URL", () => {
    window.history.replaceState(null, "", "/tutor/schedule?sessionId=sess-123&action=outcome");

    const { result } = renderHook(() => useDeepLinkParam("sessionId"));

    expect(result.current).toBe("sess-123");
    expect(mockRouter.replace).toHaveBeenCalledWith("/tutor/schedule?action=outcome", { scroll: false });
  });

  it("A1: sau khi URL bị xóa tham số, hook vẫn duy trì giá trị snapshot cho consumer trong các lần re-render tiếp theo", () => {
    window.history.replaceState(null, "", "/tutor/schedule?sessionId=sess-123");

    const { result, rerender } = renderHook(() => useDeepLinkParam("sessionId"));

    // Lần đầu mount đọc được param và router.replace dọn URL
    expect(mockRouter.replace).toHaveBeenCalledWith("/tutor/schedule", { scroll: false });

    // Khi component cha re-render (hoặc khi async state trong consumer cập nhật)
    act(() => {
      rerender();
    });

    // A1 BUG: Với code cũ, URL đã mất sessionId nên result.current bị reset về null!
    // Snapshot hook đúng: result.current PHẢI GIỮ NGUYÊN "sess-123"!
    expect(result.current).toBe("sess-123");
  });

  it("xóa sạch dấu ? khi tham số được xóa là tham số duy nhất", () => {
    window.history.replaceState(null, "", "/tutor/schedule?sessionId=sess-solo");

    const { result } = renderHook(() => useDeepLinkParam("sessionId"));

    expect(result.current).toBe("sess-solo");
    expect(mockRouter.replace).toHaveBeenCalledWith("/tutor/schedule", { scroll: false });
  });

  it("phản ứng khi query thay đổi ngay cả khi người dùng đang ở sẵn trang đó", () => {
    window.history.replaceState(null, "", "/tutor/schedule");
    const { result } = renderHook(() => useDeepLinkParam("sessionId"));
    expect(result.current).toBeNull();

    // Query changes on same page (user clicks another notification)
    act(() => {
      mockRouter.push("/tutor/schedule?sessionId=sess-new");
    });

    expect(result.current).toBe("sess-new");
    expect(mockRouter.replace).toHaveBeenCalledWith("/tutor/schedule", { scroll: false });
  });

  it("bấm lại cùng một deep link (cùng giá trị) vẫn được nhận diện là điều hướng mới và URL được dọn lại", () => {
    window.history.replaceState(null, "", "/tutor/schedule?sessionId=sess-1");
    const { result } = renderHook(() => useDeepLinkParams(["sessionId", "action"]));
    const firstSnapshot = result.current;
    expect(firstSnapshot.sessionId).toBe("sess-1");
    expect(window.location.search).toBe("");

    act(() => {
      mockRouter.push("/tutor/schedule?sessionId=sess-1");
    });

    // Cùng giá trị nhưng là lần điều hướng mới: snapshot phải đổi định danh để consumer chạy lại effect
    expect(result.current).not.toBe(firstSnapshot);
    expect(result.current.sessionId).toBe("sess-1");
    // URL phải được dọn lại (cleanedUrlRef không được chặn nhầm)
    expect(window.location.search).toBe("");
  });

  it("lần điều hướng sau không có action thì action cũ không còn trong snapshot", () => {
    window.history.replaceState(null, "", "/tutor/schedule?sessionId=sess-1&action=outcome");
    const { result } = renderHook(() => useDeepLinkParams(["sessionId", "action"]));
    expect(result.current.action).toBe("outcome");

    act(() => {
      mockRouter.push("/tutor/schedule?sessionId=sess-1");
    });

    expect(result.current).toEqual({ sessionId: "sess-1", action: null });
  });

  it("useDeepLinkParams: đọc đồng thời nhiều tham số và xóa tất cả trong đúng 1 lần router.replace (chống race condition)", () => {
    window.history.replaceState(null, "", "/tutor/schedule?sessionId=sess-100&action=outcome&filter=active");

    const { result } = renderHook(() => useDeepLinkParams(["sessionId", "action"]));

    expect(result.current).toEqual({
      sessionId: "sess-100",
      action: "outcome",
    });

    // Chỉ gọi router.replace đúng 1 lần và URL mới còn lại đúng filter=active
    expect(mockRouter.replace).toHaveBeenCalledTimes(1);
    expect(mockRouter.replace).toHaveBeenCalledWith("/tutor/schedule?filter=active", { scroll: false });
  });
});

import React, { useEffect } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, render, waitFor } from "@testing-library/react";
import {
  NotificationProvider,
  notificationApi,
  useNotification,
} from "@/features/notification";
import type { NotificationItem } from "@/features/notification";
import type { NotificationContextType } from "@/features/notification/context/NotificationContext";

const PAGE_SIZE = 20;

function makeItem(n: number, over: Partial<NotificationItem> = {}): NotificationItem {
  return {
    notificationId: `n-${n}`,
    type: "SESSION_REMINDER",
    title: `Thông báo ${n}`,
    shortText: `Nội dung ${n}`,
    isRead: false,
    // n lớn hơn = mới hơn
    createdAt: new Date(2026, 0, 1, 0, 0, n).toISOString(),
    ...over,
  };
}

function listResponse(items: NotificationItem[], totalElements = items.length) {
  return {
    success: true,
    data: {
      items,
      page: 0,
      size: PAGE_SIZE,
      totalElements,
      totalPages: Math.max(1, Math.ceil(totalElements / PAGE_SIZE)),
      unreadCount: items.filter((i) => !i.isRead).length,
    },
  };
}

function summary(total: number, byCategory: Record<string, number> = {}) {
  return { success: true, data: { total, byCategory } };
}

const probe = { ctx: undefined as unknown as NotificationContextType };
function Probe() {
  const value = useNotification();
  useEffect(() => {
    probe.ctx = value;
  });
  return null;
}

function mountProvider() {
  return render(
    <NotificationProvider role="STUDENT">
      <Probe />
    </NotificationProvider>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("NotificationContext - đồng bộ dữ liệu (lỗi 2–6 của review)", () => {
  it("lỗi 2: đọc xong thì chấm danh mục tắt theo summary mới của server", async () => {
    const items = [makeItem(1)];
    const summarySpy = vi
      .spyOn(notificationApi, "getUnreadSummary")
      .mockResolvedValue(summary(1, { SCHEDULE: 1 }));
    vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(listResponse(items));
    vi.spyOn(notificationApi, "markAsRead").mockResolvedValue({ success: true } as never);

    mountProvider();
    await waitFor(() => expect(probe.ctx.unreadCategoryMap).toEqual({ SCHEDULE: true }));

    summarySpy.mockResolvedValue(summary(0, {}));
    await act(async () => {
      await probe.ctx.markAsRead("n-1");
    });

    await waitFor(() => expect(probe.ctx.unreadCategoryMap).toEqual({}));
    expect(probe.ctx.unreadCount).toBe(0);
  });

  it("lỗi 3: poll nền lấy cả một trang đầy đủ, không chỉ 5 item mới nhất", async () => {
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue(summary(0));
    const listSpy = vi.spyOn(notificationApi, "getNotifications").mockResolvedValue(listResponse([]));

    mountProvider();
    await waitFor(() => expect(listSpy).toHaveBeenCalled());
    expect(listSpy).toHaveBeenCalledWith(false, 0, PAGE_SIZE);

    await act(async () => {
      await probe.ctx.refresh();
    });
    for (const call of listSpy.mock.calls) {
      expect(call[2]).toBe(PAGE_SIZE);
    }
  });

  it("lỗi 4: 'Xem thêm' tab Chưa đọc không bỏ sót item sau khi người dùng đọc giữa chừng", async () => {
    // Server giả lập: 45 thông báo chưa đọc, mới nhất trước, phân trang theo trang thật
    const serverUnread = new Set<string>();
    const all: NotificationItem[] = [];
    for (let n = 45; n >= 1; n--) {
      all.push(makeItem(n));
      serverUnread.add(`n-${n}`);
    }
    vi.spyOn(notificationApi, "getUnreadSummary").mockImplementation(async () =>
      summary(serverUnread.size),
    );
    vi.spyOn(notificationApi, "getNotifications").mockImplementation(async (unreadOnly, page, size) => {
      const pool = unreadOnly ? all.filter((i) => serverUnread.has(i.notificationId)) : all;
      const start = (page ?? 0) * (size ?? PAGE_SIZE);
      const slice = pool
        .slice(start, start + (size ?? PAGE_SIZE))
        .map((i) => ({ ...i, isRead: !serverUnread.has(i.notificationId) }));
      return listResponse(slice, pool.length);
    });
    vi.spyOn(notificationApi, "markAsRead").mockImplementation(async (id: string) => {
      serverUnread.delete(id);
      return { success: true } as never;
    });

    mountProvider();
    await waitFor(() => expect(probe.ctx.items.length).toBe(PAGE_SIZE));

    await act(async () => {
      await probe.ctx.loadUnread();
    });
    expect(probe.ctx.unreadItems).toHaveLength(PAGE_SIZE);
    expect(probe.ctx.unreadHasMore).toBe(true);

    // Đọc 5 item trong 20 item đầu
    for (const n of [45, 44, 43, 42, 41]) {
      await act(async () => {
        await probe.ctx.markAsRead(`n-${n}`);
      });
    }

    // Bấm "Xem thêm" cho tới khi hết: phải gom đủ mọi item chưa đọc còn lại
    for (let guard = 0; guard < 5 && probe.ctx.unreadHasMore; guard++) {
      await act(async () => {
        await probe.ctx.loadMoreUnread();
      });
    }

    // Tổng cộng phải còn đủ 40 item chưa đọc (45 - 5 đã đọc), không thiếu, không trùng
    const ids = probe.ctx.unreadItems.map((i) => i.notificationId);
    expect(new Set(ids).size).toBe(ids.length);
    expect(ids).toHaveLength(40);
    expect(probe.ctx.unreadItems.every((i) => !i.isRead)).toBe(true);
    expect(probe.ctx.unreadHasMore).toBe(false);
  });

  it("lỗi 4: phản hồi 'Xem thêm' đến muộn không chèn lại item vừa đọc", async () => {
    const all: NotificationItem[] = [];
    for (let n = 30; n >= 1; n--) all.push(makeItem(n));
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue(summary(30));
    let releasePage1: (() => void) | undefined;
    vi.spyOn(notificationApi, "getNotifications").mockImplementation(async (unreadOnly, page) => {
      const start = (page ?? 0) * PAGE_SIZE;
      const slice = all.slice(start, start + PAGE_SIZE);
      if (unreadOnly && page === 1) {
        await new Promise<void>((resolve) => {
          releasePage1 = resolve;
        });
      }
      return listResponse(slice, all.length);
    });
    vi.spyOn(notificationApi, "markAsRead").mockResolvedValue({ success: true } as never);

    mountProvider();
    await waitFor(() => expect(probe.ctx.items.length).toBe(PAGE_SIZE));
    await act(async () => {
      await probe.ctx.loadUnread();
    });

    let pending!: Promise<void>;
    act(() => {
      pending = probe.ctx.loadMoreUnread();
    });
    await waitFor(() => expect(releasePage1).toBeDefined());

    // Trong lúc request bay, người dùng đọc item n-5 (nằm trong trang trả về muộn)
    await act(async () => {
      await probe.ctx.markAsRead("n-5");
    });
    await act(async () => {
      releasePage1?.();
      await pending;
    });

    expect(probe.ctx.unreadItems.some((i) => i.notificationId === "n-5")).toBe(false);
  });

  it("lỗi 5: poll trả success:false sau khi đã có dữ liệu thì giữ nguyên danh sách và không báo lỗi", async () => {
    const items = [makeItem(1), makeItem(2)];
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue(summary(2));
    const listSpy = vi
      .spyOn(notificationApi, "getNotifications")
      .mockResolvedValue(listResponse(items));

    mountProvider();
    await waitFor(() => expect(probe.ctx.items).toHaveLength(2));

    listSpy.mockResolvedValue({ success: false, message: "Lỗi máy chủ" } as never);
    await act(async () => {
      await probe.ctx.refresh();
    });

    expect(probe.ctx.error).toBeNull();
    expect(probe.ctx.items).toHaveLength(2);
  });

  it("lỗi 6: markAsRead/markAllAsRead giữ nguyên định danh khi danh sách thay đổi", async () => {
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue(summary(1));
    const listSpy = vi
      .spyOn(notificationApi, "getNotifications")
      .mockResolvedValue(listResponse([makeItem(1)]));

    mountProvider();
    await waitFor(() => expect(probe.ctx.items).toHaveLength(1));
    const { markAsRead, markAllAsRead } = probe.ctx;

    listSpy.mockResolvedValue(listResponse([makeItem(2), makeItem(1)]));
    await act(async () => {
      await probe.ctx.refresh();
    });
    await waitFor(() => expect(probe.ctx.items).toHaveLength(2));

    expect(probe.ctx.markAsRead).toBe(markAsRead);
    expect(probe.ctx.markAllAsRead).toBe(markAllAsRead);
  });

  it("lỗi 6: markAllAsRead thất bại hoàn tác đúng và không mất item mới tải trong lúc chờ", async () => {
    const original = [makeItem(2), makeItem(1)];
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue(summary(2));
    vi.spyOn(notificationApi, "getNotifications").mockImplementation(async (unreadOnly) =>
      listResponse(unreadOnly ? original : original),
    );
    let rejectMarkAll: ((e: Error) => void) | undefined;
    vi.spyOn(notificationApi, "markAllAsRead").mockImplementation(
      () =>
        new Promise((_resolve, reject) => {
          rejectMarkAll = reject;
        }),
    );

    mountProvider();
    await waitFor(() => expect(probe.ctx.items).toHaveLength(2));
    await act(async () => {
      await probe.ctx.loadUnread();
    });

    let pending!: Promise<void>;
    act(() => {
      pending = probe.ctx.markAllAsRead();
    });
    expect(probe.ctx.unreadItems).toHaveLength(0);

    await act(async () => {
      rejectMarkAll?.(new Error("network"));
      await pending;
    });

    expect(probe.ctx.unreadItems.map((i) => i.notificationId)).toEqual(["n-2", "n-1"]);
    expect(probe.ctx.items.every((i) => !i.isRead)).toBe(true);
  });

  it("lỗi 3: hơn một trang thông báo mới trong một nhịp poll vẫn vào danh sách đầy đủ", async () => {
    const old = [makeItem(1)];
    vi.spyOn(notificationApi, "getUnreadSummary").mockResolvedValue(summary(1));
    const listSpy = vi
      .spyOn(notificationApi, "getNotifications")
      .mockResolvedValue(listResponse(old));

    mountProvider();
    await waitFor(() => expect(probe.ctx.items).toHaveLength(1));

    // 30 thông báo mới (n-31..n-2) tới giữa hai nhịp: server trả 2 trang, mới nhất trước
    const fresh: NotificationItem[] = [];
    for (let n = 31; n >= 2; n--) fresh.push(makeItem(n));
    const all = [...fresh, ...old];
    listSpy.mockImplementation(async (_unreadOnly, page) => {
      const start = (page ?? 0) * PAGE_SIZE;
      return listResponse(all.slice(start, start + PAGE_SIZE), all.length);
    });

    await act(async () => {
      await probe.ctx.refresh();
    });

    await waitFor(() => expect(probe.ctx.items).toHaveLength(31));
  });
});

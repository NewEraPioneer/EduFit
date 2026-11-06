"use client";

import React, {
  createContext,
  useContext,
  useEffect,
  useState,
  useRef,
  useCallback,
  useMemo,
} from "react";
import { notificationApi } from "../api/notificationApi";
import type { NotificationItem } from "../types";
import type { UserRole } from "@/shared/ui/Sidebar";
import { usePolling } from "@/shared/hooks/usePolling";

export interface NotificationContextType {
  unreadCount: number;
  unreadCategoryMap: Record<string, boolean>;
  categoryCounts: Record<string, number>;
  activeToasts: NotificationItem[];
  items: NotificationItem[];
  unreadItems: NotificationItem[];
  isLoading: boolean;
  error: string | null;
  hasMore: boolean;
  unreadHasMore: boolean;
  unreadLoading: boolean;
  unreadError: string | null;
  currentPage: number;
  loadMore: () => Promise<void>;
  loadUnread: () => Promise<void>;
  loadMoreUnread: () => Promise<void>;
  refresh: () => Promise<void>;
  dismissToast: (notificationId: string) => void;
  markAsRead: (notificationId: string) => Promise<void>;
  markAllAsRead: () => Promise<void>;
  refreshSummary: () => Promise<void>;
}

const defaultContext: NotificationContextType = {
  unreadCount: 0,
  unreadCategoryMap: {},
  categoryCounts: {},
  activeToasts: [],
  items: [],
  unreadItems: [],
  isLoading: false,
  error: null,
  hasMore: false,
  unreadHasMore: false,
  unreadLoading: false,
  unreadError: null,
  currentPage: 0,
  loadMore: async () => {},
  loadUnread: async () => {},
  loadMoreUnread: async () => {},
  refresh: async () => {},
  dismissToast: () => {},
  markAsRead: async () => {},
  markAllAsRead: async () => {},
  refreshSummary: async () => {},
};

const POLL_PAGE_SIZE = 20;
const MAX_TOASTS_PER_POLL = 5;
const MAX_GAP_PAGES = 3;

const NotificationContext = createContext<NotificationContextType>(defaultContext);

export interface NotificationProviderProps {
  children: React.ReactNode;
  role?: UserRole;
}

export function NotificationProvider({
  children,
}: NotificationProviderProps) {
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [categoryCounts, setCategoryCounts] = useState<Record<string, number>>({});
  const [unreadCategoryMap, setUnreadCategoryMap] = useState<Record<string, boolean>>({});
  const [activeToasts, setActiveToasts] = useState<NotificationItem[]>([]);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [unreadItems, setUnreadItems] = useState<NotificationItem[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState<boolean>(false);
  const [currentPage, setCurrentPage] = useState<number>(0);

  // Trạng thái phân trang tab UNREAD (B3: quản lý tập trung tại Context)
  const [unreadHasMore, setUnreadHasMore] = useState<boolean>(false);
  const [unreadLoading, setUnreadLoading] = useState<boolean>(false);
  const [unreadError, setUnreadError] = useState<string | null>(null);

  // Bản sao mới nhất của state để callback ổn định định danh vẫn đọc được dữ liệu hiện tại
  const itemsRef = useRef<NotificationItem[]>([]);
  const unreadItemsRef = useRef<NotificationItem[]>([]);
  const unreadCountRef = useRef<number>(0);
  const unreadRequestRef = useRef<number>(0);
  const unreadLoadingMoreRef = useRef<boolean>(false);
  useEffect(() => {
    itemsRef.current = items;
    unreadItemsRef.current = unreadItems;
    unreadCountRef.current = unreadCount;
  }, [items, unreadItems, unreadCount]);

  const mutationVersionRef = useRef<number>(0);
  const pendingMutationsRef = useRef<number>(0);
  const needsRevalidateAfterMutationRef = useRef<boolean>(false);
  const pendingReadIdsRef = useRef<Set<string>>(new Set());
  const readStatusRef = useRef<Map<string, boolean>>(new Map());
  const seenIdsRef = useRef<Set<string>>(new Set());
  // Mốc createdAt (ms) của thông báo mới nhất đã thấy; null = chưa có lần tải đầu.
  const lastSeenAtRef = useRef<number | null>(null);
  const aliveRef = useRef<boolean>(true);
  const hasLoadedRef = useRef<boolean>(false);
  const autoDismissTimersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(
    new Map()
  );

  const dismissToast = useCallback((id: string) => {
    setActiveToasts((prev) => prev.filter((t) => t.notificationId !== id));
    const timer = autoDismissTimersRef.current.get(id);
    if (timer) {
      clearTimeout(timer);
      autoDismissTimersRef.current.delete(id);
    }
  }, []);

  const queueToast = useCallback(
    (item: NotificationItem) => {
      setActiveToasts((prev) => {
        if (prev.some((t) => t.notificationId === item.notificationId)) return prev;
        return [...prev, item];
      });

      // Auto-dismiss after 4.5s
      const timer = setTimeout(() => {
        dismissToast(item.notificationId);
      }, 4500);
      autoDismissTimersRef.current.set(item.notificationId, timer);
    },
    [dismissToast]
  );

  const applySummary = useCallback(
    (data: { total: number; byCategory?: Record<string, number> | null }) => {
      setUnreadCount(data.total);
      setCategoryCounts(data.byCategory || {});
      const catMap: Record<string, boolean> = {};
      if (data.byCategory) {
        for (const [cat, count] of Object.entries(data.byCategory)) {
          if (count > 0) catMap[cat] = true;
        }
      }
      setUnreadCategoryMap(catMap);
    },
    []
  );

  // Đồng bộ lại số chưa đọc + chấm danh mục từ server sau khi mutation kết thúc
  const syncSummary = useCallback(async () => {
    const versionAtStart = mutationVersionRef.current;
    try {
      const res = await notificationApi.getUnreadSummary();
      if (!aliveRef.current) return;
      if (versionAtStart !== mutationVersionRef.current || pendingMutationsRef.current > 0) return;
      if (res.success && res.data) applySummary(res.data);
    } catch {
      // Poll chu kỳ sau sẽ đồng bộ lại
    }
  }, [applySummary]);

  const pollNotifications = useCallback(async () => {
    // B2: Nếu đang có mutation pending, hoãn poll chu kỳ và xếp hàng revalidate sau
    if (pendingMutationsRef.current > 0) {
      if (hasLoadedRef.current) {
        needsRevalidateAfterMutationRef.current = true;
        return;
      }
    }

    const versionAtStart = mutationVersionRef.current;

    try {
      if (!hasLoadedRef.current) {
        setIsLoading(true);
      }

      // Gộp song song bằng Promise.allSettled để tránh đứt gãy phụ thuộc lẫn nhau
      const [summaryRes, listRes] = await Promise.allSettled([
        notificationApi.getUnreadSummary(),
        notificationApi.getNotifications(false, 0, POLL_PAGE_SIZE),
      ]);

      if (!aliveRef.current) return;

      // Nếu trong lúc request đang bay mà có mutation mới, hủy cập nhật từ kết quả cũ
      if (versionAtStart !== mutationVersionRef.current) {
        return;
      }

      // 1. Xử lý Summary
      if (summaryRes.status === "fulfilled" && summaryRes.value.success && summaryRes.value.data) {
        applySummary(summaryRes.value.data);
      }

      // 2. Xử lý List
      if (listRes.status === "fulfilled") {
        if (listRes.value.success && listRes.value.data?.items) {
          let fetchedItems = listRes.value.data.items;

          // Lấp khoảng hở: nếu trang đầu toàn thông báo mới chưa từng thấy (có thể còn nữa ở trang sau),
          // đọc tiếp các trang kế tới khi gặp item đã biết (giới hạn số trang để không quét vô hạn)
          if (hasLoadedRef.current && lastSeenAtRef.current !== null) {
            const since = lastSeenAtRef.current;
            const totalPages = listRes.value.data.totalPages ?? 1;
            let nextPage = 1;
            while (nextPage < totalPages && nextPage <= MAX_GAP_PAGES) {
              const last = fetchedItems[fetchedItems.length - 1];
              const lastIsNew =
                last &&
                !seenIdsRef.current.has(last.notificationId) &&
                (Date.parse(last.createdAt) || 0) > since;
              if (!lastIsNew) break;
              try {
                const more = await notificationApi.getNotifications(false, nextPage, POLL_PAGE_SIZE);
                if (!aliveRef.current || versionAtStart !== mutationVersionRef.current) return;
                if (!more.success || !more.data?.items?.length) break;
                fetchedItems = [...fetchedItems, ...more.data.items];
              } catch {
                break;
              }
              nextPage += 1;
            }
          }

          if (!hasLoadedRef.current) {
            // Lần nạp đầu tiên: khởi tạo danh sách đầy đủ
            for (const item of fetchedItems) {
              readStatusRef.current.set(item.notificationId, item.isRead);
            }
            setItems(fetchedItems);
            setUnreadItems(fetchedItems.filter((i) => !i.isRead));
            setCurrentPage(0);
            setHasMore(
              (listRes.value.data.totalPages ?? 1) > 1 ||
              (listRes.value.data.totalElements ?? fetchedItems.length) > POLL_PAGE_SIZE
            );
            setError(null);
            hasLoadedRef.current = true;
          } else {
            // B1: Chu kỳ poll nền sau đó:
            // 1. Đồng bộ readStatusRef bên ngoài pure updater
            for (const item of fetchedItems) {
              if (!pendingReadIdsRef.current.has(item.notificationId)) {
                readStatusRef.current.set(item.notificationId, item.isRead);
              }
            }

            // 2. Cập nhật isRead mới nhất cho các item đã có và chèn brandNew lên đầu
            setItems((prev) => {
              const fetchedMap = new Map(fetchedItems.map((i) => [i.notificationId, i]));
              const existingIds = new Set(prev.map((i) => i.notificationId));
              const brandNew = fetchedItems.filter((i) => !existingIds.has(i.notificationId));

              const updatedExisting = prev.map((existing) => {
                const fetched = fetchedMap.get(existing.notificationId);
                if (fetched && !pendingReadIdsRef.current.has(existing.notificationId)) {
                  return { ...existing, isRead: fetched.isRead };
                }
                return existing;
              });

              if (brandNew.length === 0) return updatedExisting;
              return [...brandNew, ...updatedExisting];
            });

            // 3. Đồng bộ unreadItems tập trung tại Context
            setUnreadItems((prev) => {
              const fetchedMap = new Map(fetchedItems.map((i) => [i.notificationId, i]));
              const existingIds = new Set(prev.map((i) => i.notificationId));
              const brandNewUnread = fetchedItems.filter(
                (i) => !i.isRead && !existingIds.has(i.notificationId)
              );

              const updatedExisting = prev
                .map((existing) => {
                  const fetched = fetchedMap.get(existing.notificationId);
                  if (fetched && !pendingReadIdsRef.current.has(existing.notificationId)) {
                    return { ...existing, isRead: fetched.isRead };
                  }
                  return existing;
                })
                .filter((item) => !item.isRead);

              return [...brandNewUnread, ...updatedExisting];
            });

            setError(null);
          }

          const newest = Math.max(0, ...fetchedItems.map((item) => Date.parse(item.createdAt) || 0));

          if (lastSeenAtRef.current === null) {
            // Lần tải đầu: coi toàn bộ thông báo hiện có là đã biết, không toast
            lastSeenAtRef.current = newest;
            for (const item of fetchedItems) seenIdsRef.current.add(item.notificationId);
          } else {
            const since = lastSeenAtRef.current;
            const fresh = fetchedItems
              .filter(
                (item) =>
                  !item.isRead &&
                  !seenIdsRef.current.has(item.notificationId) &&
                  (Date.parse(item.createdAt) || 0) > since,
              )
              .sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt));
            for (const item of fetchedItems) seenIdsRef.current.add(item.notificationId);
            lastSeenAtRef.current = Math.max(since, newest);
            for (const item of fresh.slice(-MAX_TOASTS_PER_POLL)) queueToast(item);
          }
        } else if (!listRes.value.success && !hasLoadedRef.current) {
          // Đã có dữ liệu thì giữ nguyên danh sách, lỗi thoáng qua không được thay thế bằng khối lỗi
          setError(listRes.value.message || "Không thể tải thông báo");
        }
      } else if (listRes.status === "rejected") {
        if (!hasLoadedRef.current) {
          const reason = listRes.reason;
          setError(reason instanceof Error ? reason.message : "Không thể tải thông báo");
        }
      }
    } catch (err: unknown) {
      if (!aliveRef.current) return;
      if (!hasLoadedRef.current) {
        setError(err instanceof Error ? err.message : "Không thể tải thông báo");
      }
    } finally {
      if (aliveRef.current) {
        setIsLoading(false);
      }
    }
  }, [queueToast, applySummary]);

  const { refresh } = usePolling({
    intervalMs: 15_000,
    onPoll: pollNotifications,
  });

  const loadMore = useCallback(async () => {
    if (!hasMore || isLoading) return;

    try {
      const nextPage = currentPage + 1;
      const res = await notificationApi.getNotifications(false, nextPage, POLL_PAGE_SIZE);
      if (res.success && res.data?.items) {
        const newItems = res.data.items;
        for (const item of newItems) {
          readStatusRef.current.set(item.notificationId, item.isRead);
        }
        setItems((prev) => {
          const existingIds = new Set(prev.map((i) => i.notificationId));
          const uniqueNewItems = newItems.filter((i) => !existingIds.has(i.notificationId));
          return [...prev, ...uniqueNewItems];
        });
        setCurrentPage(nextPage);
        setHasMore(
          (res.data.totalPages ?? 1) > nextPage + 1 ||
          (res.data.totalElements ?? 0) > (nextPage + 1) * POLL_PAGE_SIZE
        );
      }
    } catch {
      // Ignore pagination background failure
    }
  }, [currentPage, hasMore, isLoading]);

  // Tải lại tab Chưa đọc từ đầu (server lọc unreadOnly). Kết quả của yêu cầu cũ bị bỏ khi có yêu cầu mới.
  const loadUnread = useCallback(async () => {
    const requestId = ++unreadRequestRef.current;
    try {
      setUnreadLoading(true);
      setUnreadError(null);
      const res = await notificationApi.getNotifications(true, 0, POLL_PAGE_SIZE);
      if (!aliveRef.current || requestId !== unreadRequestRef.current) return;
      if (res.success && res.data?.items) {
        // Bỏ item vừa đọc trong lúc request đang bay để không chèn lại
        const fetched = res.data.items.filter((i) => readStatusRef.current.get(i.notificationId) !== true);
        for (const item of fetched) {
          readStatusRef.current.set(item.notificationId, item.isRead);
        }
        setUnreadItems(fetched);
        setUnreadHasMore((res.data.totalElements ?? 0) > fetched.length);
      } else if (!res.success) {
        setUnreadError(res.message || "Không thể tải thông báo");
      }
    } catch (err: unknown) {
      if (requestId !== unreadRequestRef.current) return;
      setUnreadError(err instanceof Error ? err.message : "Không thể tải thông báo");
    } finally {
      if (requestId === unreadRequestRef.current) setUnreadLoading(false);
    }
  }, []);

  // "Xem thêm" tab Chưa đọc: trang tính theo số item đã tải (không theo chỉ số trang cũ) nên việc đọc
  // item giữa chừng không làm lệch/bỏ sót; phần chồng lấn được loại bằng dedupe.
  const loadMoreUnread = useCallback(async () => {
    if (unreadLoadingMoreRef.current) return;
    unreadLoadingMoreRef.current = true;
    const requestId = unreadRequestRef.current;
    try {
      const loaded = unreadItemsRef.current.length;
      const pageIndex = Math.floor(loaded / POLL_PAGE_SIZE);
      const res = await notificationApi.getNotifications(true, pageIndex, POLL_PAGE_SIZE);
      if (!aliveRef.current || requestId !== unreadRequestRef.current) return;
      if (res.success && res.data?.items) {
        const fetched = res.data.items.filter((i) => readStatusRef.current.get(i.notificationId) !== true);
        for (const item of fetched) {
          readStatusRef.current.set(item.notificationId, item.isRead);
        }
        const existingIds = new Set(unreadItemsRef.current.map((i) => i.notificationId));
        const brandNew = fetched.filter((i) => !existingIds.has(i.notificationId));
        const mergedLength = unreadItemsRef.current.length + brandNew.length;
        setUnreadItems((prev) => {
          const ids = new Set(prev.map((i) => i.notificationId));
          return [...prev, ...brandNew.filter((i) => !ids.has(i.notificationId))];
        });
        setUnreadHasMore((res.data.totalElements ?? 0) > mergedLength);
      } else if (!res.success) {
        setUnreadError(res.message || "Không thể tải thông báo");
      }
    } catch (err: unknown) {
      setUnreadError(err instanceof Error ? err.message : "Không thể tải thông báo");
    } finally {
      unreadLoadingMoreRef.current = false;
    }
  }, []);

  useEffect(() => {
    aliveRef.current = true;
    const timers = autoDismissTimersRef.current;
    return () => {
      aliveRef.current = false;
      for (const timer of timers.values()) {
        clearTimeout(timer);
      }
      timers.clear();
    };
  }, []);

  // Kết thúc một mutation: nếu là mutation cuối thì đồng bộ lại (poll bù hoặc chỉ summary để tắt chấm danh mục)
  const settleMutation = useCallback(() => {
    pendingMutationsRef.current = Math.max(0, pendingMutationsRef.current - 1);
    if (pendingMutationsRef.current !== 0) return;
    if (needsRevalidateAfterMutationRef.current) {
      needsRevalidateAfterMutationRef.current = false;
      void pollNotifications();
    } else {
      void syncSummary();
    }
  }, [pollNotifications, syncSummary]);

  const markAsRead = useCallback(
    async (id: string) => {
      dismissToast(id);

      if (pendingReadIdsRef.current.has(id)) return;
      const isAlreadyRead = readStatusRef.current.get(id) === true;
      if (isAlreadyRead) return;

      const snapshotItem =
        itemsRef.current.find((i) => i.notificationId === id) ??
        unreadItemsRef.current.find((i) => i.notificationId === id);

      pendingReadIdsRef.current.add(id);
      readStatusRef.current.set(id, true);

      setUnreadCount((prev) => Math.max(0, prev - 1));
      setItems((prev) =>
        prev.map((item) =>
          item.notificationId === id ? { ...item, isRead: true } : item,
        ),
      );
      setUnreadItems((prev) => prev.filter((item) => item.notificationId !== id));

      mutationVersionRef.current += 1;
      pendingMutationsRef.current += 1;

      try {
        await notificationApi.markAsRead(id);
      } catch {
        readStatusRef.current.set(id, false);
        setUnreadCount((prev) => prev + 1);
        setItems((prev) =>
          prev.map((item) =>
            item.notificationId === id ? { ...item, isRead: false } : item,
          ),
        );
        setUnreadItems((prev) => {
          if (prev.some((item) => item.notificationId === id)) return prev;
          return snapshotItem ? [{ ...snapshotItem, isRead: false }, ...prev] : prev;
        });
      } finally {
        pendingReadIdsRef.current.delete(id);
        settleMutation();
      }
    },
    [dismissToast, settleMutation]
  );

  const markAllAsRead = useCallback(async () => {
    setActiveToasts([]);

    // Gộp cả danh sách "Tất cả" và "Chưa đọc" vì item chỉ có ở tab Chưa đọc cũng phải được đánh dấu/hoàn tác
    const snapshotUnread = new Map<string, NotificationItem>();
    for (const item of [...unreadItemsRef.current, ...itemsRef.current]) {
      if (!item.isRead && !snapshotUnread.has(item.notificationId)) {
        snapshotUnread.set(item.notificationId, item);
      }
    }
    const countDeducted = unreadCountRef.current;

    for (const id of snapshotUnread.keys()) {
      readStatusRef.current.set(id, true);
    }

    setUnreadCount(0);
    setCategoryCounts({});
    setUnreadCategoryMap({});
    setItems((prev) => prev.map((item) => ({ ...item, isRead: true })));
    setUnreadItems([]);

    mutationVersionRef.current += 1;
    pendingMutationsRef.current += 1;

    try {
      await notificationApi.markAllAsRead();
    } catch {
      for (const id of snapshotUnread.keys()) {
        readStatusRef.current.set(id, false);
      }
      setUnreadCount((prev) => prev + countDeducted);
      setItems((prev) =>
        prev.map((item) =>
          snapshotUnread.has(item.notificationId) ? { ...item, isRead: false } : item
        )
      );
      // Khôi phục theo snapshot nhưng giữ nguyên item mới tải trong lúc chờ
      setUnreadItems((prev) => {
        const present = new Set(prev.map((i) => i.notificationId));
        const restored = [...snapshotUnread.values()]
          .filter((i) => !present.has(i.notificationId))
          .map((i) => ({ ...i, isRead: false }));
        return [...prev, ...restored].sort(
          (a, b) => (Date.parse(b.createdAt) || 0) - (Date.parse(a.createdAt) || 0)
        );
      });
    } finally {
      settleMutation();
    }
  }, [settleMutation]);

  const refreshSummary = useCallback(async () => {
    await refresh({ force: true });
  }, [refresh]);

  const triggerRefresh = useCallback(async () => {
    await refresh({ force: true });
  }, [refresh]);

  // C2: Memoize Context Provider value để ngăn ngừa re-render vô ích cho component con
  const contextValue = useMemo<NotificationContextType>(
    () => ({
      unreadCount,
      unreadCategoryMap,
      categoryCounts,
      activeToasts,
      items,
      unreadItems,
      isLoading,
      error,
      hasMore,
      unreadHasMore,
      unreadLoading,
      unreadError,
      currentPage,
      loadMore,
      loadUnread,
      loadMoreUnread,
      refresh: triggerRefresh,
      dismissToast,
      markAsRead,
      markAllAsRead,
      refreshSummary,
    }),
    [
      unreadCount,
      unreadCategoryMap,
      categoryCounts,
      activeToasts,
      items,
      unreadItems,
      isLoading,
      error,
      hasMore,
      unreadHasMore,
      unreadLoading,
      unreadError,
      currentPage,
      loadMore,
      loadUnread,
      loadMoreUnread,
      triggerRefresh,
      dismissToast,
      markAsRead,
      markAllAsRead,
      refreshSummary,
    ]
  );

  return (
    <NotificationContext.Provider value={contextValue}>
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotification(): NotificationContextType {
  return useContext(NotificationContext);
}

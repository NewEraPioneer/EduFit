"use client";

import React, {
  createContext,
  useContext,
  useEffect,
  useState,
  useRef,
  useCallback,
} from "react";
import { notificationApi } from "../api/notificationApi";
import type { NotificationItem } from "../types";
import type { UserRole } from "@/shared/ui/Sidebar";

export interface NotificationContextType {
  unreadCount: number;
  unreadCategoryMap: Record<string, boolean>;
  categoryCounts: Record<string, number>;
  activeToasts: NotificationItem[];
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
  dismissToast: () => {},
  markAsRead: async () => {},
  markAllAsRead: async () => {},
  refreshSummary: async () => {},
};

const POLL_PAGE_SIZE = 20;
const MAX_TOASTS_PER_POLL = 5;

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

  const seenIdsRef = useRef<Set<string>>(new Set());
  // Mốc createdAt (ms) của thông báo mới nhất đã thấy; null = chưa có lần tải đầu.
  const lastSeenAtRef = useRef<number | null>(null);
  const aliveRef = useRef<boolean>(false);
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

  const refreshSummary = useCallback(async () => {
    try {
      const summaryRes = await notificationApi.getUnreadSummary();
      if (!aliveRef.current) return;
      if (summaryRes.success && summaryRes.data) {
        const { total, byCategory } = summaryRes.data;
        setUnreadCount(total);
        setCategoryCounts(byCategory || {});
        const catMap: Record<string, boolean> = {};
        if (byCategory) {
          for (const [cat, count] of Object.entries(byCategory)) {
            if (count > 0) catMap[cat] = true;
          }
        }
        setUnreadCategoryMap(catMap);
      }
    } catch {
      // Ignore background fetch error
    }
  }, []);

  const pollNotifications = useCallback(async () => {
    if (typeof document !== "undefined" && document.hidden) return;

    try {
      await refreshSummary();

      // Thông báo "mới" là thông báo có createdAt sau mốc đã thấy, không phụ thuộc việc người dùng đã đọc
      // hay chưa đọc thông báo cũ (trước đây dựa vào tập id của 5 mục đầu nên đọc vài cái là toast nhầm).
      const listRes = await notificationApi.getNotifications(true, 0, POLL_PAGE_SIZE);
      if (!aliveRef.current) return;
      if (listRes.success && listRes.data?.items) {
        const items = listRes.data.items;
        const newest = Math.max(0, ...items.map((item) => Date.parse(item.createdAt) || 0));

        if (lastSeenAtRef.current === null) {
          // Lần tải đầu: mọi thứ đang chưa đọc là "đã biết", không toast. Danh sách rỗng thì mốc là 0 (mọi thông
          // báo sau đó đều mới) và không dùng đồng hồ máy khách để khỏi lệch giờ với máy chủ.
          lastSeenAtRef.current = newest;
          for (const item of items) seenIdsRef.current.add(item.notificationId);
        } else {
          const since = lastSeenAtRef.current;
          const fresh = items
            .filter(
              (item) =>
                !seenIdsRef.current.has(item.notificationId) &&
                (Date.parse(item.createdAt) || 0) > since,
            )
            .sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt));
          for (const item of items) seenIdsRef.current.add(item.notificationId);
          lastSeenAtRef.current = Math.max(since, newest);
          // Tối đa MAX_TOASTS_PER_POLL toast mới nhất; phần còn lại đã nằm trong số chưa đọc trên chuông.
          for (const item of fresh.slice(-MAX_TOASTS_PER_POLL)) queueToast(item);
        }
      }
    } catch {
      // Ignore poll error
    }
  }, [refreshSummary, queueToast]);

  useEffect(() => {
    // Mỗi lần gắn (hoặc gắn lại) bắt đầu từ trạng thái sạch; phản hồi muộn sau khi gỡ bị bỏ qua.
    aliveRef.current = true;
    seenIdsRef.current = new Set();
    lastSeenAtRef.current = null;
    void pollNotifications();
    const interval = setInterval(pollNotifications, 15000);
    const handleFocus = () => {
      void pollNotifications();
    };
    window.addEventListener("focus", handleFocus);

    return () => {
      aliveRef.current = false;
      clearInterval(interval);
      window.removeEventListener("focus", handleFocus);
      for (const timer of autoDismissTimersRef.current.values()) {
        clearTimeout(timer);
      }
      autoDismissTimersRef.current.clear();
    };
  }, [pollNotifications]);

  const markAsRead = useCallback(
    async (id: string) => {
      dismissToast(id);
      setUnreadCount((prev) => Math.max(0, prev - 1));
      try {
        await notificationApi.markAsRead(id);
      } catch {
        // Ignore
      }
      void refreshSummary();
    },
    [dismissToast, refreshSummary]
  );

  const markAllAsRead = useCallback(async () => {
    setActiveToasts([]);
    setUnreadCount(0);
    setCategoryCounts({});
    setUnreadCategoryMap({});
    try {
      await notificationApi.markAllAsRead();
    } catch {
      // Ignore
    }
    void refreshSummary();
  }, [refreshSummary]);

  return (
    <NotificationContext.Provider
      value={{
        unreadCount,
        unreadCategoryMap,
        categoryCounts,
        activeToasts,
        dismissToast,
        markAsRead,
        markAllAsRead,
        refreshSummary,
      }}
    >
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotification(): NotificationContextType {
  return useContext(NotificationContext);
}

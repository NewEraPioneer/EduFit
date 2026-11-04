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
  const isInitialFetchRef = useRef<boolean>(true);
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

      // Fetch latest unread notifications to detect new arrivals for floating toast
      const listRes = await notificationApi.getNotifications(true, 0, 5);
      if (listRes.success && listRes.data?.items) {
        const items = listRes.data.items;

        if (isInitialFetchRef.current) {
          for (const item of items) {
            seenIdsRef.current.add(item.notificationId);
          }
          isInitialFetchRef.current = false;
        } else {
          for (const item of items) {
            if (!seenIdsRef.current.has(item.notificationId)) {
              seenIdsRef.current.add(item.notificationId);
              queueToast(item);
            }
          }
        }
      }
    } catch {
      // Ignore poll error
    }
  }, [refreshSummary, queueToast]);

  useEffect(() => {
    void pollNotifications();
    const interval = setInterval(pollNotifications, 15000);
    const handleFocus = () => {
      void pollNotifications();
    };
    window.addEventListener("focus", handleFocus);

    return () => {
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

"use client";

import { useCallback, useMemo } from "react";
import { useNotification } from "../context/NotificationContext";
import { isSessionTargetType } from "../lib/notificationRoute";
import type { NotificationItem } from "../types";

function isSessionTarget(item: NotificationItem): boolean {
  return !item.isRead && !!item.targetId && isSessionTargetType(item.targetType);
}

/**
 * Các thông báo CHƯA ĐỌC gắn với một buổi học, gom theo sessionId (mới nhất trước).
 * Dùng để đánh dấu ngay trên lịch những buổi vừa có thay đổi mà người dùng chưa xem.
 */
export function useSessionChangeNotifications() {
  const { items, unreadItems, markAsRead } = useNotification();

  const bySession = useMemo(() => {
    const seen = new Set<string>();
    const map = new Map<string, NotificationItem[]>();
    for (const item of [...unreadItems, ...items]) {
      if (!isSessionTarget(item) || seen.has(item.notificationId)) continue;
      seen.add(item.notificationId);
      const list = map.get(item.targetId as string) ?? [];
      list.push(item);
      map.set(item.targetId as string, list);
    }
    for (const list of map.values()) {
      list.sort((a, b) => (Date.parse(b.createdAt) || 0) - (Date.parse(a.createdAt) || 0));
    }
    return map;
  }, [items, unreadItems]);

  /** Đánh dấu đã đọc mọi thông báo của buổi học này (gọi khi người dùng đã mở xem buổi học). */
  const markSessionSeen = useCallback(
    (sessionId: string) => {
      for (const item of bySession.get(sessionId) ?? []) {
        void markAsRead(item.notificationId);
      }
    },
    [bySession, markAsRead],
  );

  return { bySession, markSessionSeen };
}

"use client";

import React from "react";
import { useRouter } from "next/navigation";
import {
  Bell,
  Calendar,
  CheckCheck,
  Star,
  UserCheck,
  X,
} from "lucide-react";
import { useNotification } from "../context/NotificationContext";
import { getNotificationRoute } from "./NotificationDropdown";
import type { NotificationItem } from "../types";
import type { UserRole } from "@/shared/ui/Sidebar";

interface NotificationFloatingToastProps {
  role?: UserRole;
}

export function NotificationFloatingToast({
  role = "STUDENT",
}: NotificationFloatingToastProps) {
  const { activeToasts, dismissToast, markAsRead } = useNotification();
  const router = useRouter();

  if (!activeToasts || activeToasts.length === 0) {
    return null;
  }

  const handleToastClick = async (item: NotificationItem) => {
    await markAsRead(item.notificationId);
    const destination = getNotificationRoute(item, role);
    if (destination) {
      router.push(destination);
    }
  };

  const renderIcon = (type: string) => {
    const upper = type?.toUpperCase() || "";
    if (upper.startsWith("LINK_")) {
      return <UserCheck className="size-4 text-blue-500" />;
    }
    if (upper.startsWith("CONNECTION_")) {
      return <UserCheck className="size-4 text-indigo-500" />;
    }
    if (upper.startsWith("SESSION_") || upper.includes("RESCHEDULE")) {
      return <Calendar className="size-4 text-emerald-500" />;
    }
    if (upper.startsWith("REVIEW_")) {
      return <Star className="size-4 text-amber-500 fill-amber-500" />;
    }
    if (upper.startsWith("MILESTONE_") || upper.startsWith("PROGRESS_")) {
      return <CheckCheck className="size-4 text-teal-500" />;
    }
    return <Bell className="size-4 text-amber-500" />;
  };

  return (
    <div
      aria-live="polite"
      className="fixed top-20 right-5 z-50 flex flex-col gap-2.5 max-w-sm w-full pointer-events-none"
    >
      {activeToasts.map((toast) => (
        <div
          key={toast.notificationId}
          role="alert"
          onClick={() => void handleToastClick(toast)}
          className="pointer-events-auto bg-white dark:bg-stone-900 border border-stone-200 dark:border-stone-800 rounded-xl shadow-xl p-3.5 flex items-start gap-3 cursor-pointer hover:border-amber-400 dark:hover:border-amber-600 transition-all animate-in slide-in-from-top-3 fade-in duration-300"
        >
          <div className="size-8 rounded-full bg-amber-50 dark:bg-amber-950/40 flex items-center justify-center shrink-0 mt-0.5">
            {renderIcon(toast.type)}
          </div>

          <div className="flex-1 min-w-0 space-y-0.5">
            <div className="flex items-center justify-between gap-1">
              <h4 className="text-xs font-bold text-neutral-900 dark:text-stone-100 truncate">
                {toast.title}
              </h4>
              <button
                type="button"
                aria-label="Đóng thông báo"
                onClick={(e) => {
                  e.stopPropagation();
                  dismissToast(toast.notificationId);
                }}
                className="text-neutral-400 hover:text-neutral-700 dark:text-stone-500 dark:hover:text-stone-200 p-0.5 rounded cursor-pointer"
              >
                <X className="size-3.5" />
              </button>
            </div>
            <p className="text-[11px] text-neutral-600 dark:text-stone-400 line-clamp-2 leading-relaxed">
              {toast.shortText}
            </p>
          </div>
        </div>
      ))}
    </div>
  );
}

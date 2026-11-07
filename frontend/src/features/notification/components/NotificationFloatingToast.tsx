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
      className="fixed top-20 right-5 z-50 flex flex-col gap-3 max-w-md w-full pointer-events-none"
    >
      {activeToasts.map((toast) => (
        <div
          key={toast.notificationId}
          role="alert"
          onClick={() => void handleToastClick(toast)}
          className="pointer-events-auto bg-red-50 dark:bg-red-950 border-2 border-red-500 rounded-2xl shadow-2xl ring-4 ring-red-500/20 p-4 flex items-start gap-3.5 cursor-pointer hover:bg-red-100 dark:hover:bg-red-900 transition-all animate-in slide-in-from-top-3 fade-in duration-300"
        >
          <div className="size-10 rounded-full bg-red-600 flex items-center justify-center shrink-0 mt-0.5 [&_svg]:size-5 [&_svg]:text-white [&_svg]:fill-none">
            {renderIcon(toast.type)}
          </div>

          <div className="flex-1 min-w-0 space-y-0.5">
            <div className="flex items-center justify-between gap-1">
              <h4 className="text-sm font-extrabold text-red-800 dark:text-red-100 truncate">
                {toast.title}
              </h4>
              <button
                type="button"
                aria-label="Đóng thông báo"
                onClick={(e) => {
                  e.stopPropagation();
                  dismissToast(toast.notificationId);
                }}
                className="text-red-400 hover:text-red-700 dark:text-red-300 dark:hover:text-red-100 p-0.5 rounded cursor-pointer"
              >
                <X className="size-3.5" />
              </button>
            </div>
            <p className="text-xs font-medium text-red-900 dark:text-red-200 line-clamp-3 leading-relaxed">
              {toast.shortText}
            </p>
          </div>
        </div>
      ))}
    </div>
  );
}

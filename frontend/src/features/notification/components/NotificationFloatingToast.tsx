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
      return <UserCheck className="size-4 text-brand-strong" />;
    }
    if (upper.startsWith("CONNECTION_")) {
      return <UserCheck className="size-4 text-brand-strong" />;
    }
    if (upper.startsWith("SESSION_") || upper.includes("RESCHEDULE")) {
      return <Calendar className="size-4 text-success-strong" />;
    }
    if (upper.startsWith("REVIEW_")) {
      return <Star className="size-4 text-action-strong fill-action" />;
    }
    if (upper.startsWith("MILESTONE_") || upper.startsWith("PROGRESS_")) {
      return <CheckCheck className="size-4 text-success-strong" />;
    }
    return <Bell className="size-4 text-action-strong" />;
  };

  return (
    <div
      aria-live="polite"
      className="fixed top-20 right-4 left-4 z-50 flex flex-col gap-3 sm:left-auto sm:w-full sm:max-w-md pointer-events-none"
    >
      {activeToasts.map((toast) => (
        <div
          key={toast.notificationId}
          role="alert"
          onClick={() => void handleToastClick(toast)}
          className="pointer-events-auto bg-danger-soft border-2 border-danger rounded-2xl shadow-2xl ring-4 ring-danger/20 p-4 flex items-start gap-3.5 cursor-pointer hover:bg-danger-soft transition-all animate-in slide-in-from-top-3 fade-in duration-300"
        >
          <div className="size-10 rounded-full bg-danger flex items-center justify-center shrink-0 mt-0.5 [&_svg]:size-5 [&_svg]:text-white [&_svg]:fill-none">
            {renderIcon(toast.type)}
          </div>

          <div className="flex-1 min-w-0 space-y-0.5">
            <div className="flex items-center justify-between gap-1">
              <h4 className="text-sm font-medium text-danger-strong truncate">
                {toast.title}
              </h4>
              <button
                type="button"
                aria-label="Đóng thông báo"
                onClick={(e) => {
                  e.stopPropagation();
                  dismissToast(toast.notificationId);
                }}
                className="text-danger-strong hover:text-danger-strong p-0.5 rounded cursor-pointer"
              >
                <X className="size-3.5" />
              </button>
            </div>
            <p className="text-xs font-medium text-danger-strong line-clamp-3 leading-relaxed">
              {toast.shortText}
            </p>
          </div>
        </div>
      ))}
    </div>
  );
}

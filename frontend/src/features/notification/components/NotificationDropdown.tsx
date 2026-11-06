"use client";

import React, { useState, useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import {
  Bell,
  CheckCheck,
  Calendar,
  UserCheck,
  Star,
  Inbox,
  AlertCircle,
} from "lucide-react";
import { useNotification } from "../context/NotificationContext";
import type { NotificationItem } from "../types";
import type { UserRole } from "@/shared/ui/Sidebar";
import { getNotificationRoute } from "../lib/notificationRoute";

export { getNotificationRoute };

interface NotificationDropdownProps {
  role?: UserRole;
  onClose?: () => void;
}

export function NotificationDropdown({
  role = "STUDENT",
  onClose,
}: NotificationDropdownProps) {
  // Badge unreadCount và danh sách All/Unread dùng chung từ context (AC1, AC4, B3)
  const {
    unreadCount,
    markAsRead,
    markAllAsRead,
    items: contextItems,
    unreadItems: contextUnreadItems,
    isLoading: contextLoading,
    error: contextError,
    hasMore: contextHasMore,
    loadMore: contextLoadMore,
    unreadHasMore,
    unreadLoading,
    unreadError,
    loadUnread,
    loadMoreUnread,
  } = useNotification();

  const [activeTab, setActiveTab] = useState<"ALL" | "UNREAD">("ALL");

  const dropdownRef = useRef<HTMLDivElement>(null);
  const router = useRouter();

  // B3 & D1: Khi chuyển sang tab UNREAD thì nạp trang 0 từ server qua Context
  useEffect(() => {
    if (activeTab === "UNREAD") {
      void loadUnread();
    }
  }, [activeTab, loadUnread]);

  const itemsToDisplay = activeTab === "ALL" ? contextItems : contextUnreadItems;
  const isLoading = activeTab === "ALL" ? contextLoading : unreadLoading;
  const error = activeTab === "ALL" ? contextError : unreadError;
  const hasMore = activeTab === "ALL" ? contextHasMore : unreadHasMore;

  // Click outside listener
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Element | null;
      // Bấm chính nút chuông để đóng: nút tự đóng/mở, không để mousedown đóng rồi click mở lại
      if (target?.closest?.("[data-notification-trigger]")) return;
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(event.target as Node)
      ) {
        onClose?.();
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [onClose]);

  // AC4 & AC9: router.push trước, markAsRead chạy ngầm không chặn luồng
  const handleNotificationClick = (item: NotificationItem) => {
    const destination = getNotificationRoute(item, role);
    if (destination) {
      router.push(destination);
    }
    onClose?.();
    if (!item.isRead) {
      void markAsRead(item.notificationId);
    }
  };

  const handleMarkAllRead = () => {
    void markAllAsRead();
  };

  const handleLoadMore = () => {
    if (hasMore && !isLoading) {
      if (activeTab === "ALL") {
        void contextLoadMore();
      } else {
        void loadMoreUnread();
      }
    }
  };

  // Helper relative time
  const formatRelativeTime = (isoString: string) => {
    try {
      const date = new Date(isoString);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffMins = Math.floor(diffMs / (1000 * 60));
      const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

      if (diffMins < 1) return "Vừa xong";
      if (diffMins < 60) return `${diffMins} phút trước`;
      if (diffHours < 24) return `${diffHours} giờ trước`;
      if (diffDays < 7) return `${diffDays} ngày trước`;
      return date.toLocaleDateString("vi-VN");
    } catch {
      return "";
    }
  };

  // Notification icon by type
  const renderNotificationIcon = (type: string) => {
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
    return <Bell className="size-4 text-neutral-400" />;
  };

  return (
    <div
      ref={dropdownRef}
      className="absolute right-0 top-full mt-2 w-[min(26rem,calc(100vw-1.5rem))] bg-white dark:bg-stone-900 rounded-2xl shadow-2xl border border-stone-200 dark:border-stone-800 overflow-hidden z-50 animate-in fade-in slide-in-from-top-2 duration-150"
    >
      {/* Header */}
      <div className="p-4 border-b border-stone-100 dark:border-stone-800 flex items-center justify-between bg-stone-50/50 dark:bg-stone-800/40">
        <div className="flex items-center gap-2">
          <Bell className="size-4.5 text-neutral-700 dark:text-stone-300" />
          <h3 className="font-heading font-bold text-sm text-neutral-900 dark:text-stone-100">
            Thông báo
          </h3>
          {unreadCount > 0 && (
            <span className="px-1.5 py-0.5 rounded-full text-[10px] font-bold bg-red-600 text-white">
              {unreadCount}
            </span>
          )}
        </div>

        {unreadCount > 0 && (
          <button
            type="button"
            onClick={handleMarkAllRead}
            className="inline-flex items-center gap-1 text-[11px] font-semibold text-amber-600 hover:text-amber-700 dark:text-amber-400 dark:hover:text-amber-300 transition-colors cursor-pointer"
          >
            <CheckCheck className="size-3.5" />
            <span>Đọc tất cả</span>
          </button>
        )}
      </div>

      {/* Filter Tabs */}
      <div className="flex border-b border-stone-100 dark:border-stone-800 text-xs px-3 pt-2 gap-2 bg-stone-50/30 dark:bg-stone-800/20">
        <button
          type="button"
          onClick={() => setActiveTab("ALL")}
          className={`pb-2 px-2 font-semibold border-b-2 transition-colors cursor-pointer ${
            activeTab === "ALL"
              ? "border-amber-500 text-amber-600 dark:text-amber-400"
              : "border-transparent text-neutral-500 dark:text-stone-400 hover:text-neutral-800"
          }`}
        >
          Tất cả
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("UNREAD")}
          className={`pb-2 px-2 font-semibold border-b-2 transition-colors cursor-pointer ${
            activeTab === "UNREAD"
              ? "border-amber-500 text-amber-600 dark:text-amber-400"
              : "border-transparent text-neutral-500 dark:text-stone-400 hover:text-neutral-800"
          }`}
        >
          Chưa đọc ({unreadCount})
        </button>
      </div>

      {/* Notification List */}
      <div className="max-h-[min(32rem,70vh)] overflow-y-auto divide-y divide-stone-100 dark:divide-stone-800">
        {isLoading ? (
          <div className="py-12 flex flex-col items-center justify-center gap-2 text-neutral-400">
            <div className="size-6 border-2 border-amber-500/30 border-t-amber-500 rounded-full animate-spin" />
            <span className="text-xs">Đang tải thông báo...</span>
          </div>
        ) : error ? (
          <div className="py-8 px-4 text-center text-xs text-rose-500 flex flex-col items-center gap-1">
            <AlertCircle className="size-5 text-rose-500" />
            <span>{error}</span>
          </div>
        ) : itemsToDisplay.length === 0 ? (
          <div className="py-12 px-4 text-center flex flex-col items-center justify-center gap-2 text-neutral-400 dark:text-stone-500">
            <Inbox className="size-8 stroke-1" />
            <span className="text-xs">
              {activeTab === "UNREAD"
                ? "Không có thông báo chưa đọc"
                : "Chưa có thông báo nào"}
            </span>
          </div>
        ) : (
          itemsToDisplay.map((item) => (
            <button
              type="button"
              key={item.notificationId}
              onClick={() => handleNotificationClick(item)}
              className={`w-full p-3.5 flex gap-3 text-left transition-colors cursor-pointer ${
                item.isRead
                  ? "bg-white dark:bg-stone-900 opacity-80 hover:bg-stone-50 dark:hover:bg-stone-800/60"
                  : "bg-red-50 dark:bg-red-950/40 border-l-4 border-red-500 hover:bg-red-100 dark:hover:bg-red-950/60"
              }`}
            >
              <div className="size-8 rounded-full bg-stone-100 dark:bg-stone-800 flex items-center justify-center shrink-0 mt-0.5">
                {renderNotificationIcon(item.type)}
              </div>

              <div className="flex-1 min-w-0 space-y-0.5">
                <div className="flex items-start justify-between gap-2">
                  <h4
                    className={`text-[13px] leading-snug line-clamp-2 ${
                      item.isRead
                        ? "font-medium text-neutral-800 dark:text-stone-200"
                        : "text-sm font-extrabold text-red-800 dark:text-red-100"
                    }`}
                  >
                    {item.title}
                  </h4>
                  <span className="text-[10px] text-neutral-400 dark:text-stone-500 whitespace-nowrap">
                    {formatRelativeTime(item.createdAt)}
                  </span>
                </div>

                <p className="text-xs text-neutral-600 dark:text-stone-400 line-clamp-4 leading-relaxed">
                  {item.shortText}
                </p>
              </div>

              {!item.isRead && (
                <div className="flex items-center shrink-0">
                  <span className="size-3 rounded-full bg-red-600 ring-4 ring-red-500/20" />
                </div>
              )}
            </button>
          ))
        )}
        {!isLoading && !error && hasMore && (
          <button
            type="button"
            onClick={handleLoadMore}
            className="w-full py-3 text-xs font-semibold text-amber-600 hover:bg-stone-50 dark:text-amber-400 dark:hover:bg-stone-800/60 cursor-pointer"
          >
            Xem thêm
          </button>
        )}
      </div>
    </div>
  );
}

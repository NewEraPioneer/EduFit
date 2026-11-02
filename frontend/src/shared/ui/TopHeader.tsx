"use client";

import React, { useState } from "react";
import Link from "next/link";
import {
  Bell,
  Search,
  LogOut,
  User as UserIcon,
  PanelLeft,
  ChevronDown,
  ShieldCheck,
  Settings,
  SunMoon,
} from "lucide-react";
import { UserRole } from "./Sidebar";
import { useTheme } from "@/context/ThemeContext";
import { NotificationDropdown, notificationApi } from "@/features/notification";

interface TopHeaderProps {
  role?: UserRole;
  user: {
    fullName: string;
    email: string;
    role: string;
    avatarUrl?: string | null;
  };
  title?: string;
  onLogout: () => void;
  onToggleSidebar?: () => void;
}

const ROLE_LABEL_MAP: Record<string, string> = {
  STUDENT: "Học sinh",
  TUTOR: "Gia sư",
  PARENT: "Phụ huynh",
  ADMIN: "Quản trị viên",
};

export function TopHeader({
  role = "STUDENT",
  user,
  title = "Dashboard",
  onLogout,
  onToggleSidebar,
}: TopHeaderProps) {
  const [profileMenuOpen, setProfileMenuOpen] = useState(false);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [unreadNotifications, setUnreadNotifications] = useState(0);
  const { theme, setTheme } = useTheme();

  React.useEffect(() => {
    let active = true;
    const refreshCount = () => {
      if (document.hidden) return;
      void notificationApi.getUnreadCount().then((res) => {
        if (active && res.success && res.data) setUnreadNotifications(res.data.unreadCount);
      }).catch(() => {});
    };
    refreshCount();
    const interval = window.setInterval(refreshCount, 15_000);
    window.addEventListener("focus", refreshCount);
    return () => { active = false; window.clearInterval(interval); window.removeEventListener("focus", refreshCount); };
  }, [user.email]);

  const roleLabel = ROLE_LABEL_MAP[role] || "Thành viên";
  const roleBase = role.toLowerCase();

  return (
    <header className="h-16 px-6 sm:px-8 bg-white dark:bg-stone-900 border-b border-stone-200/80 dark:border-stone-800 flex items-center justify-between sticky top-0 z-30 transition-colors duration-200">
      {/* Topbar Left */}
      <div className="flex items-center gap-3">
        {onToggleSidebar && (
          <button
            type="button"
            aria-label="Mở menu điều hướng"
            aria-haspopup="dialog"
            aria-controls="dashboard-mobile-navigation"
            onClick={onToggleSidebar}
            className="lg:hidden p-2 rounded-xl hover:bg-stone-100 dark:hover:bg-stone-800 text-neutral-600 dark:text-stone-300 transition-colors"
          >
            <PanelLeft className="size-5" />
          </button>
        )}
        <h1 className="font-heading text-lg font-bold text-neutral-900 dark:text-stone-100 tracking-tight">
          {title}
        </h1>
        <span className="hidden sm:inline text-stone-300 dark:text-stone-700">/</span>
        <span className="hidden sm:inline text-xs text-neutral-500 dark:text-stone-400 font-medium">{roleLabel}</span>
      </div>

      {/* Topbar Right */}
      <div className="flex items-center gap-3 sm:gap-4">
        {/* Search Bar */}
        <div className="hidden md:flex items-center gap-2 px-3 py-1.5 bg-stone-50 dark:bg-stone-800/80 border border-stone-200 dark:border-stone-700/80 rounded-xl text-xs text-neutral-400 dark:text-stone-400 focus-within:border-amber-400 focus-within:bg-white dark:focus-within:bg-stone-800 transition-all w-56">
          <Search className="size-3.5 text-neutral-400 dark:text-stone-400" />
          <input
            type="text"
            placeholder="Tìm kiếm nội dung..."
            className="bg-transparent border-none outline-hidden text-neutral-800 dark:text-stone-100 placeholder:text-neutral-400 dark:placeholder:text-stone-500 w-full text-xs"
          />
        </div>

        {/* Notifications */}
        <div className="relative">
          <button
            type="button"
            aria-label="Thông báo"
            onClick={() => setNotificationOpen(!notificationOpen)}
            className="relative p-2 rounded-xl border border-stone-200/60 dark:border-stone-700/60 hover:bg-stone-50 dark:hover:bg-stone-800 text-neutral-600 dark:text-stone-300 transition-colors cursor-pointer"
          >
            <Bell className="size-4.5" />
            {unreadNotifications > 0 && (
              <span className="absolute top-1 right-1 size-2 rounded-full bg-amber-500 animate-pulse ring-2 ring-white dark:ring-stone-900" />
            )}
          </button>

          {notificationOpen && (
            <NotificationDropdown
              role={role}
              onClose={() => setNotificationOpen(false)}
              onUnreadCountChange={(cnt) => setUnreadNotifications(cnt)}
            />
          )}
        </div>

        {/* Profile Dropdown */}
        <div className="relative">
          <button
            type="button"
            onClick={() => setProfileMenuOpen(!profileMenuOpen)}
            className="flex items-center gap-2.5 p-1.5 pr-2.5 rounded-xl border border-stone-200/60 dark:border-stone-700/60 hover:bg-stone-50 dark:hover:bg-stone-800 transition-colors group cursor-pointer"
          >
            {user.avatarUrl ? (
              <div className="size-8 rounded-lg overflow-hidden border border-stone-200/80 dark:border-stone-700 shadow-xs shrink-0 bg-stone-100 dark:bg-stone-800">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={user.avatarUrl}
                  alt={user.fullName || "Avatar"}
                  className="size-full object-cover"
                />
              </div>
            ) : (
              <div className="size-8 rounded-lg bg-gradient-to-tr from-amber-500 to-orange-400 text-white font-bold text-xs flex items-center justify-center shadow-xs shrink-0">
                {user.fullName ? user.fullName.charAt(0).toUpperCase() : "U"}
              </div>
            )}
            <div className="hidden sm:flex flex-col text-left">
              <span className="text-xs font-semibold text-neutral-800 dark:text-stone-100 leading-tight">
                {user.fullName || "Tài khoản EduFit"}
              </span>
              <span className="text-[10px] text-neutral-400 dark:text-stone-400 leading-tight">{roleLabel}</span>
            </div>
            <ChevronDown className="size-3.5 text-neutral-400 dark:text-stone-400 group-hover:text-neutral-700 dark:group-hover:text-stone-200 transition-colors" />
          </button>

          {profileMenuOpen && (
            <>
              <div
                className="fixed inset-0 z-40"
                onClick={() => setProfileMenuOpen(false)}
              />
              <div className="absolute right-0 mt-2 w-64 bg-white dark:bg-stone-900 border border-stone-200 dark:border-stone-800 rounded-2xl shadow-xl p-2 z-50 animate-in fade-in slide-in-from-top-2 duration-150">
                {/* 1. Header Profile Info */}
                <div className="px-3 py-2.5 border-b border-stone-100 dark:border-stone-800 mb-1.5 bg-stone-50/60 dark:bg-stone-800/60 rounded-xl flex items-center gap-2.5">
                  <div className="size-9 rounded-lg overflow-hidden border border-stone-200 dark:border-stone-700 bg-stone-100 dark:bg-stone-800 shrink-0 flex items-center justify-center">
                    {user.avatarUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={user.avatarUrl} alt={user.fullName || "Avatar"} className="size-full object-cover" />
                    ) : (
                      <span className="font-bold text-xs text-amber-600 dark:text-amber-400">{user.fullName ? user.fullName.charAt(0).toUpperCase() : "U"}</span>
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-1">
                      <p className="text-xs font-bold text-neutral-900 dark:text-stone-100 truncate">{user.fullName}</p>
                      <span className="px-1.5 py-0.5 text-[9px] font-bold rounded-md bg-amber-100 dark:bg-amber-950/60 text-amber-800 dark:text-amber-400 shrink-0">
                        {roleLabel}
                      </span>
                    </div>
                    <p className="text-[11px] text-neutral-400 dark:text-stone-400 truncate mt-0.5">{user.email}</p>
                  </div>
                </div>

                {/* 2. Navigation items */}
                <div className="space-y-0.5">
                  <Link
                    href={`/${roleBase}/settings?tab=profile`}
                    onClick={() => setProfileMenuOpen(false)}
                    className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs text-neutral-700 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 hover:text-neutral-900 dark:hover:text-stone-100 transition-colors"
                  >
                    <UserIcon className="size-4 text-neutral-400 dark:text-stone-500" />
                    <span>Hồ sơ cá nhân</span>
                  </Link>

                  <Link
                    href={`/${roleBase}/settings?tab=account`}
                    onClick={() => setProfileMenuOpen(false)}
                    className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs text-neutral-700 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 hover:text-neutral-900 dark:hover:text-stone-100 transition-colors"
                  >
                    <ShieldCheck className="size-4 text-neutral-400 dark:text-stone-500" />
                    <span>Tài khoản & Bảo mật</span>
                  </Link>

                  <Link
                    href={`/${roleBase}/settings`}
                    onClick={() => setProfileMenuOpen(false)}
                    className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs text-neutral-700 dark:text-stone-300 hover:bg-stone-50 dark:hover:bg-stone-800 hover:text-neutral-900 dark:hover:text-stone-100 transition-colors"
                  >
                    <Settings className="size-4 text-neutral-400 dark:text-stone-500" />
                    <span>Cài đặt</span>
                  </Link>
                </div>

                {/* 3. Appearance / Giao diện Quick Switcher (Ảnh 2) */}
                <div className="my-2 pt-2 border-t border-stone-100 dark:border-stone-800 px-1">
                  <div className="flex items-center gap-2 text-xs font-medium text-neutral-700 dark:text-stone-300 mb-2 px-2">
                    <SunMoon className="size-4 text-neutral-400 dark:text-stone-500" />
                    <span>Giao diện</span>
                  </div>
                  <div className="grid grid-cols-3 p-1 bg-stone-100 dark:bg-stone-800 rounded-xl gap-1 text-[11px] text-neutral-600 dark:text-stone-400">
                    <button
                      type="button"
                      onClick={() => setTheme("light")}
                      className={`py-1.5 rounded-lg font-semibold transition-all text-center cursor-pointer ${
                        theme === "light"
                          ? "bg-white dark:bg-stone-700 text-neutral-900 dark:text-stone-100 shadow-xs"
                          : "text-neutral-600 dark:text-stone-400 hover:text-neutral-900 dark:hover:text-stone-100 hover:bg-stone-200/50 dark:hover:bg-stone-700/50"
                      }`}
                    >
                      Sáng
                    </button>
                    <button
                      type="button"
                      onClick={() => setTheme("dark")}
                      className={`py-1.5 rounded-lg font-semibold transition-all text-center cursor-pointer ${
                        theme === "dark"
                          ? "bg-white dark:bg-stone-700 text-neutral-900 dark:text-stone-100 shadow-xs"
                          : "text-neutral-600 dark:text-stone-400 hover:text-neutral-900 dark:hover:text-stone-100 hover:bg-stone-200/50 dark:hover:bg-stone-700/50"
                      }`}
                    >
                      Tối
                    </button>
                    <button
                      type="button"
                      onClick={() => setTheme("system")}
                      className={`py-1.5 rounded-lg font-semibold transition-all text-center cursor-pointer ${
                        theme === "system"
                          ? "bg-white dark:bg-stone-700 text-neutral-900 dark:text-stone-100 shadow-xs"
                          : "text-neutral-600 dark:text-stone-400 hover:text-neutral-900 dark:hover:text-stone-100 hover:bg-stone-200/50 dark:hover:bg-stone-700/50"
                      }`}
                    >
                      Hệ thống
                    </button>
                  </div>
                </div>

                {/* 4. Logout Action */}
                <div className="mt-1 pt-1.5 border-t border-stone-100 dark:border-stone-800">
                  <button
                    type="button"
                    onClick={() => {
                      setProfileMenuOpen(false);
                      onLogout();
                    }}
                    className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/40 transition-colors font-semibold cursor-pointer"
                  >
                    <LogOut className="size-4 text-rose-500" />
                    <span>Đăng xuất</span>
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </header>
  );
}

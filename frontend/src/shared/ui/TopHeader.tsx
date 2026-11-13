"use client";

import React, { useState } from "react";
import Link from "next/link";
import {
  Bell,
  LogOut,
  User as UserIcon,
  PanelLeft,
  ChevronDown,
  ShieldCheck,
  Settings,
} from "lucide-react";
import { usePathname } from "next/navigation";
import { DayNightToggle } from "@/shared/motion/DayNightToggle";
import { pageTitle, type UserRole } from "./navConfig";
import { useTheme } from "@/context/ThemeContext";
import { NotificationDropdown, useNotification } from "@/features/notification";

interface TopHeaderProps {
  role?: UserRole;
  user: {
    fullName: string;
    email: string;
    role: string;
    avatarUrl?: string | null;
  };
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
  onLogout,
  onToggleSidebar,
}: TopHeaderProps) {
  const [profileMenuOpen, setProfileMenuOpen] = useState(false);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const { unreadCount } = useNotification();
  const { theme, setTheme } = useTheme();
  const pathname = usePathname();

  const roleLabel = ROLE_LABEL_MAP[role] || "Thành viên";
  const roleBase = role.toLowerCase();

  return (
    <header className="h-16 px-4 sm:px-8 bg-surface border-b border-line/80 flex items-center justify-between sticky top-0 z-30 transition-colors duration-200">
      {/* Topbar Left */}
      <div className="flex items-center gap-3">
        {onToggleSidebar && (
          <button
            type="button"
            aria-label="Mở menu điều hướng"
            aria-haspopup="dialog"
            aria-controls="dashboard-mobile-navigation"
            onClick={onToggleSidebar}
            className="lg:hidden p-2 rounded-xl hover:bg-sunken text-ink-2 transition-colors"
          >
            <PanelLeft className="size-5" />
          </button>
        )}
        <h1 className="font-heading text-lg font-bold text-ink tracking-tight">
          {pageTitle(role, pathname)}
        </h1>
        <span className="hidden sm:inline text-white/75">/</span>
        <span className="hidden sm:inline text-xs text-muted font-medium">{roleLabel}</span>
      </div>

      {/* Topbar Right */}
      <div className="flex items-center gap-3 sm:gap-4">
        {/* Notifications */}
        <div className="relative">
          <button
            type="button"
            aria-label="Thông báo"
            data-notification-trigger
            onClick={() => setNotificationOpen(!notificationOpen)}
            className="relative p-2 rounded-xl border border-line/60 hover:bg-paper text-ink-2 transition-colors cursor-pointer"
          >
            <Bell className="size-4.5" />
            {unreadCount > 0 && (
              <span className="absolute top-1 right-1 size-3 rounded-full bg-danger animate-pulse ring-2 ring-white" />
            )}
          </button>

          {notificationOpen && (
            <NotificationDropdown role={role} onClose={() => setNotificationOpen(false)} />
          )}
        </div>

        {/* Profile Dropdown */}
        <div className="relative">
          <button
            type="button"
            onClick={() => setProfileMenuOpen(!profileMenuOpen)}
            className="flex items-center gap-2.5 p-1.5 pr-2.5 rounded-xl border border-line/60 hover:bg-paper transition-colors group cursor-pointer"
          >
            {user.avatarUrl ? (
              <div className="size-8 rounded-lg overflow-hidden border border-line/80 shadow-xs shrink-0 bg-sunken">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={user.avatarUrl}
                  alt={user.fullName || "Avatar"}
                  className="size-full object-cover"
                />
              </div>
            ) : (
              <div className="size-8 rounded-lg bg-action text-on-action font-medium text-xs flex items-center justify-center shadow-xs shrink-0">
                {user.fullName ? user.fullName.charAt(0).toUpperCase() : "U"}
              </div>
            )}
            <div className="hidden sm:flex flex-col text-left">
              <span className="text-xs font-medium text-ink leading-tight">
                {user.fullName || "Tài khoản EduFit"}
              </span>
              <span className="text-xs text-muted leading-tight">{roleLabel}</span>
            </div>
            <ChevronDown className="size-3.5 text-muted group-hover:text-ink-2 transition-colors" />
          </button>

          {profileMenuOpen && (
            <>
              <div
                className="fixed inset-0 z-40"
                onClick={() => setProfileMenuOpen(false)}
              />
              <div className="absolute right-0 mt-2 w-64 bg-surface border border-line rounded-2xl shadow-xl p-2 z-50 animate-in fade-in slide-in-from-top-2 duration-150">
                {/* 1. Header Profile Info */}
                <div className="px-3 py-2.5 border-b border-line mb-1.5 bg-paper/60 rounded-xl flex items-center gap-2.5">
                  <div className="size-9 rounded-lg overflow-hidden border border-line bg-sunken shrink-0 flex items-center justify-center">
                    {user.avatarUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={user.avatarUrl} alt={user.fullName || "Avatar"} className="size-full object-cover" />
                    ) : (
                      <span className="font-medium text-xs text-action-strong">{user.fullName ? user.fullName.charAt(0).toUpperCase() : "U"}</span>
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-1">
                      <p className="text-xs font-medium text-ink truncate">{user.fullName}</p>
                      <span className="px-1.5 py-0.5 text-xs font-medium rounded-md bg-action-soft text-action-strong shrink-0">
                        {roleLabel}
                      </span>
                    </div>
                    <p className="text-xs text-muted truncate mt-0.5">{user.email}</p>
                  </div>
                </div>

                {/* 2. Navigation items */}
                <div className="space-y-0.5">
                  <Link
                    href={`/${roleBase}/settings?tab=profile`}
                    onClick={() => setProfileMenuOpen(false)}
                    className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs text-ink-2 hover:bg-paper hover:text-ink transition-colors"
                  >
                    <UserIcon className="size-4 text-muted" />
                    <span>Hồ sơ cá nhân</span>
                  </Link>

                  <Link
                    href={`/${roleBase}/settings?tab=account`}
                    onClick={() => setProfileMenuOpen(false)}
                    className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs text-ink-2 hover:bg-paper hover:text-ink transition-colors"
                  >
                    <ShieldCheck className="size-4 text-muted" />
                    <span>Tài khoản và bảo mật</span>
                  </Link>

                  <Link
                    href={`/${roleBase}/settings`}
                    onClick={() => setProfileMenuOpen(false)}
                    className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs text-ink-2 hover:bg-paper hover:text-ink transition-colors"
                  >
                    <Settings className="size-4 text-muted" />
                    <span>Cài đặt</span>
                  </Link>
                </div>

                {/* 3. Giao diện */}
                <div className="my-2 border-t border-line px-3 pt-3">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-sm text-ink-2">Giao diện tối</span>
                    <DayNightToggle />
                  </div>
                  <button
                    type="button"
                    data-compact
                    onClick={() => setTheme("system")}
                    aria-pressed={theme === "system"}
                    className={`mt-1 py-1 text-xs ${theme === "system" ? "font-medium text-ink" : "text-muted hover:text-ink"}`}
                  >
                    Theo thiết bị
                  </button>
                </div>

                {/* 4. Logout Action */}
                <div className="mt-1 pt-1.5 border-t border-line">
                  <button
                    type="button"
                    onClick={() => {
                      setProfileMenuOpen(false);
                      onLogout();
                    }}
                    className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs text-danger-strong hover:bg-danger-soft transition-colors font-medium cursor-pointer"
                  >
                    <LogOut className="size-4 text-danger-strong" />
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

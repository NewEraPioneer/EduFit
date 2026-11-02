"use client";

import React, { useState, Suspense } from "react";
import { useSearchParams, useRouter, usePathname } from "next/navigation";
import { User, KeyRound, Palette, Bell, Tv, LucideIcon } from "lucide-react";
import { UserRole } from "@/shared/ui/Sidebar";
import { SettingsTabId } from "../types";
import { ProfileSettingsTab } from "./ProfileSettingsTab";
import { AccountSettingsTab } from "./AccountSettingsTab";
import { AppearanceSettingsTab } from "./AppearanceSettingsTab";
import { NotificationsSettingsTab } from "./NotificationsSettingsTab";
import { DisplaySettingsTab } from "./DisplaySettingsTab";

interface SettingsTabConfig {
  id: SettingsTabId;
  label: string;
  icon: LucideIcon;
}

const TABS: SettingsTabConfig[] = [
  { id: "profile", label: "Hồ sơ cá nhân", icon: User },
  { id: "account", label: "Tài khoản", icon: KeyRound },
  { id: "appearance", label: "Giao diện", icon: Palette },
  { id: "notifications", label: "Thông báo", icon: Bell },
  { id: "display", label: "Hiển thị", icon: Tv },
];

interface SettingsViewProps {
  role?: UserRole;
  defaultTab?: SettingsTabId;
}

function SettingsContent({ role = "STUDENT", defaultTab = "profile" }: SettingsViewProps) {
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const queryTab = searchParams.get("tab") as SettingsTabId | null;
  const [selectedTab, setSelectedTab] = useState<SettingsTabId | null>(null);
  const [prevQueryTab, setPrevQueryTab] = useState<SettingsTabId | null>(queryTab);

  if (queryTab !== prevQueryTab) {
    setPrevQueryTab(queryTab);
    setSelectedTab(null);
  }

  const validQueryTab = queryTab && TABS.some((t) => t.id === queryTab) ? queryTab : null;
  const activeTab = selectedTab ?? validQueryTab ?? defaultTab;

  const handleSelectTab = (tabId: SettingsTabId) => {
    setSelectedTab(tabId);
    router.replace(`${pathname}?tab=${tabId}`, { scroll: false });
  };

  return (
    <div className="space-y-6 max-w-6xl mx-auto">
      {/* 1. Header chuẩn phong cách Shadcn-Admin */}
      <div className="space-y-1">
        <h1 className="font-heading text-2xl sm:text-3xl font-bold text-neutral-900 dark:text-stone-100 tracking-tight">
          Cài đặt
        </h1>
        <p className="text-xs sm:text-sm text-neutral-500 dark:text-stone-400">
          Quản lý cài đặt tài khoản, hồ sơ cá nhân và tùy chọn hệ thống của bạn.
        </p>
      </div>

      <div className="border-b border-stone-200/80 dark:border-stone-800" />

      {/* 2. Layout 2 Cột (Sub-Nav Tabs bên trái + Content Panel bên phải) */}
      <div className="flex flex-col md:flex-row gap-8 lg:gap-10">
        {/* Left Sub-nav */}
        <aside className="w-full md:w-56 shrink-0">
          <nav className="flex md:flex-col gap-1 overflow-x-auto md:overflow-x-visible pb-2 md:pb-0 -mx-2 px-2 md:mx-0 md:px-0">
            {TABS.map((tab) => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.id;

              return (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => handleSelectTab(tab.id)}
                  className={`flex items-center gap-2.5 px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all whitespace-nowrap cursor-pointer text-left ${
                    isActive
                      ? "bg-stone-100 dark:bg-stone-800 text-neutral-900 dark:text-stone-100 font-bold shadow-2xs"
                      : "text-neutral-600 dark:text-stone-400 hover:text-neutral-900 dark:hover:text-stone-100 hover:bg-stone-50 dark:hover:bg-stone-800/60"
                  }`}
                >
                  <Icon
                    className={`size-4 shrink-0 transition-colors ${
                      isActive ? "text-amber-600" : "text-neutral-400"
                    }`}
                  />
                  <span>{tab.label}</span>
                </button>
              );
            })}
          </nav>
        </aside>

        {/* Right Content Panel */}
        <div className="flex-1 min-w-0">
          {activeTab === "profile" && <ProfileSettingsTab role={role} />}
          {activeTab === "account" && <AccountSettingsTab />}
          {activeTab === "appearance" && <AppearanceSettingsTab />}
          {activeTab === "notifications" && <NotificationsSettingsTab />}
          {activeTab === "display" && <DisplaySettingsTab />}
        </div>
      </div>
    </div>
  );
}

export function SettingsView(props: SettingsViewProps) {
  return (
    <Suspense fallback={<div className="p-8 text-center text-xs text-neutral-400">Đang tải cài đặt...</div>}>
      <SettingsContent {...props} />
    </Suspense>
  );
}

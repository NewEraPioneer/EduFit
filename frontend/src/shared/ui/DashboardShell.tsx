"use client";

import React, { useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import { NotificationFloatingToast, NotificationProvider } from "@/features/notification";
import { MobileBottomNav } from "./MobileBottomNav";
import { Sidebar, UserRole } from "./Sidebar";
import { TopHeader } from "./TopHeader";

interface DashboardShellProps {
  user: {
    fullName: string;
    email: string;
    role: string;
    avatarUrl?: string | null;
  };
  role: UserRole;
  onLogout: () => void;
  children: React.ReactNode;
}

export function DashboardShell({ user, role, onLogout, children }: DashboardShellProps) {
  const [collapsed, setCollapsed] = useState(false);
  const currentPath = usePathname();
  const mobileMenu = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    mobileMenu.current?.close();
  }, [currentPath]);

  return (
    <NotificationProvider role={role}>
      <NotificationFloatingToast role={role} />
    <div className="flex min-h-dvh bg-paper font-sans text-ink">
      <div className="hidden lg:block">
        <Sidebar role={role} collapsed={collapsed} onToggleCollapse={() => setCollapsed((prev) => !prev)} currentPath={currentPath} user={user} />
      </div>

      <dialog
        ref={mobileMenu}
        id="dashboard-mobile-navigation"
        aria-label="Menu điều hướng"
        className="fixed inset-y-0 left-0 m-0 h-dvh max-h-dvh w-64 max-w-[85vw] border-0 p-0 backdrop:bg-black/40"
        onClick={(event) => {
          if (event.target === event.currentTarget || (event.target instanceof HTMLElement && event.target.closest("a"))) {
            mobileMenu.current?.close();
          }
        }}
      >
        <Sidebar role={role} collapsed={false} onToggleCollapse={() => mobileMenu.current?.close()} currentPath={currentPath} user={user} />
      </dialog>

      <div className="flex min-w-0 flex-1 flex-col">
        <TopHeader role={role} user={user} onLogout={onLogout} onToggleSidebar={() => mobileMenu.current?.showModal()} />
        <main id="dashboard-top" className="mx-auto w-full max-w-7xl flex-1 scroll-mt-20 p-4 pb-24 sm:p-8 lg:pb-8">
          {children}
        </main>
      </div>

      <MobileBottomNav role={role} />
    </div>
    </NotificationProvider>
  );
}

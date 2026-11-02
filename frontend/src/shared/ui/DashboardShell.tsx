"use client";

import React, { useState, useRef, useEffect } from "react";
import { usePathname } from "next/navigation";
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

export function DashboardShell({
  user,
  role,
  onLogout,
  children,
}: DashboardShellProps) {
  const [collapsed, setCollapsed] = useState(false);
  const currentPath = usePathname();
  const mobileMenu = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    mobileMenu.current?.close();
  }, [currentPath]);
  const openMobileMenu = () => mobileMenu.current?.showModal();

  const toggleCollapse = () => setCollapsed((prev) => !prev);

  return (
    <div className="min-h-screen bg-stone-50 dark:bg-stone-950 flex font-sans text-neutral-900 dark:text-stone-100 selection:bg-amber-200 dark:selection:bg-amber-900/40 transition-colors duration-200">
      {/* 1. Unified Dynamic Sidebar */}
      <div className="hidden lg:block">
        <Sidebar
          role={role}
          collapsed={collapsed}
          onToggleCollapse={toggleCollapse}
          currentPath={currentPath}
          user={user}
        />
      </div>
      <dialog
        ref={mobileMenu}
        id="dashboard-mobile-navigation"
        aria-label="Menu điều hướng"
        className="fixed inset-y-0 left-0 m-0 h-dvh max-h-dvh w-64 max-w-[85vw] border-0 p-0 backdrop:bg-black/40"
        onClick={(event) => {
          if (
            event.target === event.currentTarget ||
            (event.target instanceof HTMLElement && event.target.closest("a"))
          )
            mobileMenu.current?.close();
        }}
      >
        <Sidebar
          role={role}
          collapsed={false}
          onToggleCollapse={() => mobileMenu.current?.close()}
          currentPath={currentPath}
          user={user}
        />
      </dialog>

      {/* 2. Main Right Pane */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Unified Dynamic TopHeader */}
        <TopHeader
          role={role}
          user={user}
          onLogout={onLogout}
          onToggleSidebar={openMobileMenu}
        />

        {/* Scrollable Dashboard Workspace Content */}
        <main
          id="dashboard-top"
          className="flex-1 scroll-mt-20 p-6 sm:p-8 max-w-7xl w-full mx-auto"
        >
          {children}
        </main>
      </div>
    </div>
  );
}

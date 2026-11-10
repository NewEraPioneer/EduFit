"use client";

import React, { useRef } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { PanelLeft, PanelLeftClose } from "lucide-react";
import { EduFitLogo } from "@/shared/ui/EduFitLogo";
import { useHop } from "@/shared/motion/useHop";
import { cn } from "@/lib/utils";
import { useNotification } from "@/features/notification";
import { isNavActive, NAV_NOTIFICATION_CATEGORY, ROLE_NAV, type UserRole } from "./navConfig";

export type { UserRole } from "./navConfig";

interface SidebarProps {
  role?: UserRole;
  collapsed: boolean;
  onToggleCollapse: () => void;
  currentPath?: string;
  user?: {
    fullName: string;
    email: string;
    avatarUrl?: string | null;
  };
}

export function Sidebar({ role = "STUDENT", collapsed, onToggleCollapse, currentPath, user }: SidebarProps) {
  const config = ROLE_NAV[role] ?? ROLE_NAV.STUDENT;
  const roleBase = role.toLowerCase();
  const pathname = usePathname() || currentPath || `/${roleBase}/dashboard`;
  const allItems = config.sections.flatMap((section) => section.items);
  const active = allItems.find((entry) => isNavActive(entry.href, pathname))?.href ?? null;

  const { unreadCategoryMap } = useNotification();
  const navRef = useRef<HTMLElement>(null);
  const indicatorRef = useRef<HTMLSpanElement>(null);
  useHop(navRef, indicatorRef, active, "y");

  const hiddenWhenCollapsed = cn("transition-opacity duration-200", collapsed && "opacity-0");

  return (
    <aside className={cn("sticky top-0 z-40 flex h-dvh shrink-0 flex-col justify-between border-r border-line bg-surface transition-[width] duration-300 select-none", collapsed ? "w-20" : "w-64")}>
      <div className="flex flex-col gap-5 overflow-x-hidden overflow-y-auto p-4">
        <div className={cn("flex items-center border-b border-line pb-3", collapsed ? "flex-col gap-2.5" : "justify-between px-1")}>
          <EduFitLogo href={`/${roleBase}/dashboard#dashboard-top`} showText={!collapsed} roleBadge={config.roleTitle} />
          <button
            type="button"
            onClick={onToggleCollapse}
            title={collapsed ? "Mở rộng thanh menu" : "Thu gọn thanh menu"}
            aria-label={collapsed ? "Mở rộng thanh menu" : "Thu gọn thanh menu"}
            className="flex size-10 shrink-0 items-center justify-center rounded-xl text-muted hover:bg-sunken hover:text-ink"
          >
            {collapsed ? <PanelLeft className="size-4.5" /> : <PanelLeftClose className="size-4.5" />}
          </button>
        </div>

        <nav ref={navRef} className="relative flex flex-col gap-5">
          <span ref={indicatorRef} aria-hidden="true" className="absolute top-0 left-0 rounded-xl bg-action-soft opacity-0" />
          {config.sections.map((section) => (
            <div key={section.title} className="relative flex flex-col gap-1">
              <span aria-hidden={collapsed} className={cn("h-5 px-2.5 text-xs whitespace-nowrap text-muted", hiddenWhenCollapsed)}>
                {section.title}
              </span>
              {section.items.map((entry) => {
                const Icon = entry.icon;
                const isActive = entry.href === active;
                const category = NAV_NOTIFICATION_CATEGORY[entry.href];
                const hasDot = Boolean(category && unreadCategoryMap?.[category]);
                return (
                  <Link
                    key={entry.href}
                    href={entry.href}
                    data-hop={entry.href}
                    aria-current={isActive ? "page" : undefined}
                    title={collapsed ? (entry.badge ? `${entry.label} (${entry.badge})` : entry.label) : undefined}
                    className={cn("relative flex h-11 items-center justify-between rounded-xl px-2.5 text-sm transition-colors", isActive ? "font-medium text-ink" : "text-ink-2 hover:bg-sunken/70 hover:text-ink")}
                  >
                    <span className="flex min-w-0 items-center gap-2.5">
                      <span className="relative shrink-0">
                        <Icon className={cn("size-4.5", isActive ? "text-action-strong" : "text-muted")} />
                        {collapsed && hasDot && <span data-testid="sidebar-notification-dot-collapsed" className="absolute -top-1 -right-1 size-2 rounded-full bg-danger" />}
                      </span>
                      <span aria-hidden={collapsed} className={cn("truncate whitespace-nowrap transition-[max-width,opacity] duration-200", collapsed ? "max-w-0 opacity-0" : "max-w-44 opacity-100")}>
                        {entry.label}
                      </span>
                    </span>
                    <span className="flex shrink-0 items-center gap-1.5">
                      {!collapsed && hasDot && <span data-testid="sidebar-notification-dot" className="size-2.5 rounded-full bg-danger" />}
                      {!collapsed && entry.badge && <span className="rounded-md bg-sunken px-1.5 text-xs text-ink-2">{entry.badge}</span>}
                    </span>
                  </Link>
                );
              })}
            </div>
          ))}
        </nav>
      </div>

      {user && (
        <div className="border-t border-line p-3">
          <Link
            href={`/${roleBase}/settings`}
            title={collapsed ? "Cài đặt tài khoản" : undefined}
            className={cn("flex h-12 items-center rounded-xl px-2 hover:bg-sunken", collapsed ? "justify-center" : "gap-2.5")}
          >
            {user.avatarUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={user.avatarUrl} alt={user.fullName} className="size-8 shrink-0 rounded-lg object-cover" />
            ) : (
              <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-action-soft text-xs font-medium text-action-strong">{user.fullName.charAt(0).toUpperCase()}</span>
            )}
            <span aria-hidden={collapsed} className={cn("flex flex-col overflow-hidden whitespace-nowrap transition-[max-width,opacity] duration-300", collapsed ? "max-w-0 opacity-0" : "max-w-44 opacity-100")}>
              <span className="truncate text-sm font-medium text-ink">{user.fullName}</span>
              <span className="truncate text-xs text-muted">{user.email}</span>
            </span>
          </Link>
        </div>
      )}
    </aside>
  );
}

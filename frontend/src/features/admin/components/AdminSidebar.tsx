"use client";

import React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  ShieldCheck,
  ScrollText,
  BookOpen,
  HeartHandshake,
  Settings,
} from "lucide-react";

export interface AdminNavItem {
  label: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  badge?: string;
}

export interface AdminNavSection {
  title: string;
  items: AdminNavItem[];
}

export const ADMIN_NAV_SECTIONS: AdminNavSection[] = [
  {
    title: "HỆ THỐNG",
    items: [
      { label: "Tổng quan", href: "/admin/dashboard", icon: LayoutDashboard },
      { label: "Duyệt bằng cấp KYC", href: "/admin/verification-queue", icon: ShieldCheck },
      { label: "Nhật ký kiểm toán", href: "/admin/audit-logs", icon: ScrollText },
    ],
  },
  {
    title: "QUẢN LÝ DỮ LIỆU",
    items: [
      { label: "Danh mục môn học", href: "/admin/catalogs", icon: BookOpen },
      { label: "Liên kết hệ thống (UC3.3)", href: "/admin/links", icon: HeartHandshake },
    ],
  },
  {
    title: "CẤU HÌNH",
    items: [
      { label: "Cài đặt hệ thống", href: "/admin/settings", icon: Settings },
    ],
  },
];

interface AdminSidebarProps {
  collapsed?: boolean;
}

export function AdminSidebar({ collapsed = false }: AdminSidebarProps) {
  const pathname = usePathname();

  return (
    <nav className="flex flex-col gap-5 p-3" aria-label="Admin Navigation">
      {ADMIN_NAV_SECTIONS.map((section, idx) => (
        <div key={idx} className="flex flex-col gap-1">
          <span
            aria-hidden={collapsed}
            className={`h-4 px-2.5 text-[10px] font-bold uppercase tracking-wider text-neutral-400 dark:text-stone-500 font-sans whitespace-nowrap transition-opacity duration-200 ${
              collapsed ? "opacity-0" : "opacity-100"
            }`}
          >
            {section.title}
          </span>
          {section.items.map((item, itemIdx) => {
            const Icon = item.icon;
            const isActive = pathname === item.href || pathname?.startsWith(`${item.href}/`);

            return (
              <Link
                key={itemIdx}
                href={item.href}
                className={`h-10 flex items-center justify-between px-2.5 rounded-xl text-xs font-medium transition-colors group ${
                  isActive
                    ? "bg-purple-100 dark:bg-purple-950/60 text-purple-950 dark:text-purple-200 font-semibold"
                    : "text-neutral-600 dark:text-stone-400 hover:bg-stone-100 dark:hover:bg-stone-800 hover:text-neutral-900 dark:hover:text-stone-100"
                }`}
              >
                <div className="flex items-center gap-2.5 truncate">
                  <Icon
                    className={`size-4 shrink-0 transition-colors ${
                      isActive
                        ? "text-purple-700 dark:text-purple-400"
                        : "text-neutral-400 dark:text-stone-500 group-hover:text-neutral-700 dark:group-hover:text-stone-200"
                    }`}
                  />
                  {!collapsed && <span className="truncate">{item.label}</span>}
                </div>
                {!collapsed && item.badge && (
                  <span className="px-1.5 py-0.5 text-[10px] font-bold rounded-md bg-purple-200 dark:bg-purple-900/60 text-purple-800 dark:text-purple-300">
                    {item.badge}
                  </span>
                )}
              </Link>
            );
          })}
        </div>
      ))}
    </nav>
  );
}

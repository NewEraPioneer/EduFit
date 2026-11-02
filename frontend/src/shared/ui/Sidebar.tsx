"use client";

import React, { Suspense } from "react";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { EduFitLogo } from "@/shared/ui/EduFitLogo";
import {
  LayoutDashboard,
  Search,
  Sparkles,
  UserCheck,
  BookOpen,
  Calendar,
  TrendingUp,
  CreditCard,
  PanelLeftClose,
  PanelLeft,
  GraduationCap,
  Users,
  ShieldCheck,
  AlertTriangle,
  Settings,
  DollarSign,
  FileCheck2,
  HeartHandshake,
  Star,
  BarChart3,
  ScrollText,
  LucideIcon,
} from "lucide-react";

export type UserRole = "STUDENT" | "TUTOR" | "PARENT" | "ADMIN";

function isNavItemActive(
  itemHref: string,
  pathname: string,
  searchParams: ReturnType<typeof useSearchParams> | null
): boolean {
  const [itemPath, itemQueryString] = itemHref.split("?");

  if (pathname !== itemPath) {
    return false;
  }

  const itemParams = new URLSearchParams(itemQueryString || "");
  const currentTab = searchParams?.get("tab");
  const itemTab = itemParams.get("tab");

  // Nếu mục menu có chỉ định tab cụ thể (vd: ?tab=matches hoặc ?tab=requests)
  if (itemTab) {
    return currentTab === itemTab;
  }

  // Nếu mục menu không có tab query (vd: /student/tutors)
  // Chỉ active khi currentTab không có hoặc là tab mặc định "browse" / "discover"
  if (currentTab && currentTab !== "browse" && currentTab !== "discover") {
    return false;
  }

  return true;
}

interface NavItem {
  label: string;
  href: string;
  icon: LucideIcon;
  badge?: string | null;
}

interface NavSection {
  title: string;
  items: NavItem[];
}

const ROLE_NAV_CONFIG: Record<UserRole, { roleTitle: string; sections: NavSection[] }> = {
  STUDENT: {
    roleTitle: "Cổng Học Sinh",
    sections: [
      {
        title: "TỔNG QUAN",
        items: [
          { label: "Dashboard", href: "/student/dashboard", icon: LayoutDashboard },
          { label: "Liên kết phụ huynh", href: "/student/family", icon: HeartHandshake },
          { label: "Tiến độ học tập", href: "/student/progress", icon: TrendingUp },
        ],
      },
      {
        title: "TÌM KIẾM",
        items: [
          { label: "Tìm gia sư", href: "/student/tutors", icon: Search },
          { label: "Gợi ý AI Match", href: "/student/tutors?tab=matches", icon: Sparkles, badge: "AI" },
          { label: "Yêu cầu kết nối", href: "/student/tutors?tab=requests", icon: UserCheck },
        ],
      },
      {
        title: "HỌC TẬP",
        items: [
          { label: "Lớp của tôi", href: "/student/classes", icon: BookOpen },
          { label: "Lịch học tuần", href: "/student/schedule", icon: Calendar },
        ],
      },
      {
        title: "TÀI CHÍNH",
        items: [
          { label: "Ví & Học phí", href: "/student/payments", icon: CreditCard },
        ],
      },
      {
        title: "CÀI ĐẶT",
        items: [
          { label: "Cài đặt", href: "/student/settings", icon: Settings },
        ],
      },
    ],
  },
  TUTOR: {
    roleTitle: "Cổng Gia Sư",
    sections: [
      {
        title: "TỔNG QUAN",
        items: [
          { label: "Dashboard", href: "/tutor/dashboard", icon: LayoutDashboard },
          { label: "Xác minh bằng cấp", href: "/tutor/verification", icon: FileCheck2, badge: "KYC" },
        ],
      },
      {
        title: "GIẢNG DẠY",
        items: [
          { label: "Lịch dạy", href: "/tutor/schedule", icon: Calendar },
          { label: "Lớp học 1-1", href: "/tutor/classes", icon: BookOpen },
          { label: "Lịch sử giảng dạy", href: "/tutor/history", icon: GraduationCap },
          { label: "Yêu cầu học viên", href: "/tutor/requests", icon: UserCheck },
          { label: "Đánh giá", href: "/tutor/reviews", icon: Star },
        ],
      },
      {
        title: "THU NHẬP",
        items: [
          { label: "Thu nhập & Rút tiền", href: "/tutor/earnings", icon: DollarSign },
        ],
      },
      {
        title: "CÀI ĐẶT",
        items: [
          { label: "Cài đặt", href: "/tutor/settings", icon: Settings },
        ],
      },
    ],
  },
  PARENT: {
    roleTitle: "Cổng Phụ Huynh",
    sections: [
      {
        title: "TỔNG QUAN",
        items: [
          { label: "Dashboard", href: "/parent/dashboard", icon: LayoutDashboard },
          { label: "Liên kết con em", href: "/parent/children", icon: HeartHandshake },
        ],
      },
      {
        title: "THEO DÕI",
        items: [
          { label: "Tiến độ học tập", href: "/parent/progress", icon: BarChart3 },
          { label: "Lịch học gia đình", href: "/parent/schedule", icon: Calendar },
          { label: "Gia sư đồng hành", href: "/parent/tutors", icon: GraduationCap },
        ],
      },
      {
        title: "TÀI CHÍNH",
        items: [
          { label: "Ví & Học phí", href: "/parent/billing", icon: CreditCard },
        ],
      },
      {
        title: "CÀI ĐẶT",
        items: [
          { label: "Cài đặt", href: "/parent/settings", icon: Settings },
        ],
      },
    ],
  },
  ADMIN: {
    roleTitle: "Quản Trị Hệ Thống",
    sections: [
      {
        title: "HỆ THỐNG",
        items: [
          { label: "Tổng quan", href: "/admin/dashboard", icon: LayoutDashboard },
          { label: "Duyệt bằng cấp KYC", href: "/admin/verification-queue", icon: ShieldCheck },
          { label: "Đối soát thanh toán", href: "/admin/payments", icon: CreditCard },
          { label: "Nhật ký kiểm toán", href: "/admin/audit-logs", icon: ScrollText },
        ],
      },
      {
        title: "QUẢN LÝ DỮ LIỆU",
        items: [
          { label: "Học sinh & Phụ huynh", href: "/admin/learners", icon: Users },
          { label: "Gia sư", href: "/admin/tutors-management", icon: GraduationCap },
          { label: "Liên kết Phụ huynh - Con", href: "/admin/family-links", icon: HeartHandshake },
          { label: "Danh mục môn học", href: "/admin/catalogs", icon: BookOpen },
          { label: "Khiếu nại & Tranh chấp", href: "/admin/disputes", icon: AlertTriangle },
        ],
      },
      {
        title: "CẤU HÌNH",
        items: [
          { label: "Cài đặt hệ thống", href: "/admin/settings", icon: Settings },
        ],
      },
    ],
  },
};

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

function SidebarNav({
  sections,
  collapsed,
  currentPath,
}: {
  sections: NavSection[];
  collapsed: boolean;
  currentPath: string;
}) {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const effectivePathname = pathname || currentPath;

  return (
    <nav className="flex flex-col gap-5">
      {sections.map((section, idx) => (
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
            const isActive = isNavItemActive(item.href, effectivePathname, searchParams);

            return (
              <Link
                key={itemIdx}
                href={item.href}
                className={`h-10 flex items-center justify-between px-2.5 rounded-xl text-xs font-medium transition-colors group ${
                  isActive
                    ? "bg-amber-500/10 dark:bg-amber-500/20 text-amber-900 dark:text-amber-400 font-semibold shadow-xs"
                    : "text-neutral-600 dark:text-stone-400 hover:bg-stone-100 dark:hover:bg-stone-800 hover:text-neutral-900 dark:hover:text-stone-100"
                }`}
                title={collapsed ? (item.badge ? `${item.label} (${item.badge})` : item.label) : undefined}
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <Icon
                    className={`size-4.5 shrink-0 transition-transform group-hover:scale-105 ${
                      isActive
                        ? "text-amber-600 dark:text-amber-400"
                        : "text-neutral-400 dark:text-stone-500 group-hover:text-neutral-700 dark:group-hover:text-stone-300"
                    }`}
                  />
                  <span
                    aria-hidden={collapsed}
                    className={`truncate whitespace-nowrap transition-[max-width,opacity] duration-200 ${
                      collapsed ? "max-w-0 opacity-0" : "max-w-40 opacity-100"
                    }`}
                  >
                    {item.label}
                  </span>
                </div>

                {!collapsed && item.badge && (
                  <span className="px-1.5 py-0.5 text-[10px] font-bold rounded-md bg-amber-500 text-white leading-none shadow-xs shrink-0">
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

export function Sidebar({
  role = "STUDENT",
  collapsed,
  onToggleCollapse,
  currentPath,
  user,
}: SidebarProps) {
  const currentConfig = ROLE_NAV_CONFIG[role] || ROLE_NAV_CONFIG.STUDENT;
  const roleBase = role.toLowerCase();
  const effectiveCurrentPath = currentPath || `/${roleBase}/dashboard`;

  return (
    <aside
      className={`${
        collapsed ? "w-20" : "w-64"
      } bg-white dark:bg-stone-900 border-r border-stone-200/80 dark:border-stone-800 transition-[width] duration-300 ease-in-out flex flex-col justify-between shrink-0 h-screen sticky top-0 z-40 select-none`}
    >
      <div className="p-4 flex flex-col gap-5 overflow-y-auto overflow-x-hidden">
        {/* Top Header: Logo EduFit chuẩn thương hiệu + Thu gọn menu ở trên */}
        <div
          className={`flex items-center pb-3 border-b border-stone-100 dark:border-stone-800/80 transition-all ${
            collapsed ? "flex-col gap-2.5 px-0" : "justify-between px-1"
          }`}
        >
          <EduFitLogo
            href={`/${roleBase}/dashboard#dashboard-top`}
            showText={!collapsed}
            roleBadge={currentConfig.roleTitle}
          />
          <button
            type="button"
            onClick={onToggleCollapse}
            className={`p-1.5 rounded-xl hover:bg-stone-100 dark:hover:bg-stone-800 text-neutral-400 hover:text-neutral-700 dark:text-stone-400 dark:hover:text-stone-200 transition-colors cursor-pointer shrink-0 ${
              collapsed ? "w-9 h-9 flex items-center justify-center bg-stone-50 dark:bg-stone-800" : ""
            }`}
            title={collapsed ? "Mở rộng thanh menu" : "Thu gọn thanh menu"}
            aria-label={collapsed ? "Mở rộng thanh menu" : "Thu gọn thanh menu"}
          >
            {collapsed ? (
              <PanelLeft className="size-4.5" />
            ) : (
              <PanelLeftClose className="size-4.5" />
            )}
          </button>
        </div>

        {/* Navigation Sections */}
        <Suspense
          fallback={
            <nav className="flex flex-col gap-5">
              {currentConfig.sections.map((section, idx) => (
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
                    const isActive = isNavItemActive(item.href, effectiveCurrentPath, null);
                    return (
                      <Link
                        key={itemIdx}
                        href={item.href}
                        className={`h-10 flex items-center justify-between px-2.5 rounded-xl text-xs font-medium transition-colors group ${
                          isActive
                            ? "bg-amber-500/10 dark:bg-amber-500/20 text-amber-900 dark:text-amber-400 font-semibold shadow-xs"
                            : "text-neutral-600 dark:text-stone-400 hover:bg-stone-100 dark:hover:bg-stone-800 hover:text-neutral-900 dark:hover:text-stone-100"
                        }`}
                        title={collapsed ? (item.badge ? `${item.label} (${item.badge})` : item.label) : undefined}
                      >
                        <div className="flex items-center gap-2.5 min-w-0">
                          <Icon
                            className={`size-4.5 shrink-0 transition-transform group-hover:scale-105 ${
                              isActive
                                ? "text-amber-600 dark:text-amber-400"
                                : "text-neutral-400 dark:text-stone-500 group-hover:text-neutral-700 dark:group-hover:text-stone-300"
                            }`}
                          />
                          <span
                            aria-hidden={collapsed}
                            className={`truncate whitespace-nowrap transition-[max-width,opacity] duration-200 ${
                              collapsed ? "max-w-0 opacity-0" : "max-w-40 opacity-100"
                            }`}
                          >
                            {item.label}
                          </span>
                        </div>
                        {!collapsed && item.badge && (
                          <span className="px-1.5 py-0.5 text-[10px] font-bold rounded-md bg-amber-500 text-white leading-none shadow-xs shrink-0">
                            {item.badge}
                          </span>
                        )}
                      </Link>
                    );
                  })}
                </div>
              ))}
            </nav>
          }
        >
          <SidebarNav
            sections={currentConfig.sections}
            collapsed={collapsed}
            currentPath={effectiveCurrentPath}
          />
        </Suspense>
      </div>

      {/* Footer / User pill (Đã chuyển nút thu gọn lên trên) */}
      <div className="p-3 border-t border-stone-100 dark:border-stone-800 flex flex-col gap-2 bg-stone-50/50 dark:bg-stone-900/50">
        {user && (
          <Link
            href={`/${roleBase}/settings`}
            title={collapsed ? "Cài đặt tài khoản" : undefined}
            className={`h-11 px-2 flex items-center bg-white dark:bg-stone-800 hover:bg-stone-100 dark:hover:bg-stone-750 rounded-xl border border-stone-200/60 dark:border-stone-700/60 hover:border-amber-300 dark:hover:border-amber-500/60 transition-[gap,border-color,background-color] duration-300 group cursor-pointer ${
              collapsed ? "justify-center gap-0" : "gap-2"
            }`}
          >
            {user.avatarUrl ? (
              <div className="size-7 rounded-lg overflow-hidden border border-stone-200/80 dark:border-stone-700 shrink-0 bg-stone-100 dark:bg-stone-800">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={user.avatarUrl}
                  alt={user.fullName}
                  className="size-full object-cover"
                />
              </div>
            ) : (
              <div className="size-7 rounded-lg bg-amber-100 dark:bg-amber-950/60 text-amber-700 dark:text-amber-400 font-bold text-xs flex items-center justify-center shrink-0 group-hover:bg-amber-200 dark:group-hover:bg-amber-900/60 transition-colors">
                {user.fullName.charAt(0).toUpperCase()}
              </div>
            )}
            <div
              aria-hidden={collapsed}
              className={`flex flex-col overflow-hidden whitespace-nowrap transition-[max-width,opacity] duration-300 ${
                collapsed ? "max-w-0 opacity-0" : "max-w-44 opacity-100"
              }`}
            >
              <span className="text-xs font-semibold text-neutral-900 dark:text-stone-100 group-hover:text-amber-900 dark:group-hover:text-amber-400 transition-colors truncate">
                {user.fullName}
              </span>
              <span className="text-[10px] text-neutral-400 dark:text-stone-400 truncate">{user.email}</span>
            </div>
          </Link>
        )}
      </div>
    </aside>
  );
}

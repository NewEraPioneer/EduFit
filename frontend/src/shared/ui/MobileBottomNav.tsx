"use client";

import Link from "next/link";
import { useRef } from "react";
import { usePathname } from "next/navigation";
import { useHop } from "@/shared/motion/useHop";
import { cn } from "@/lib/utils";
import { useNotification } from "@/features/notification";
import { isNavActive, NAV_NOTIFICATION_CATEGORY, ROLE_NAV, type UserRole } from "./navConfig";

/** Tên ngắn cho thanh dưới: bỏ phần sau dấu "&" và các từ bổ nghĩa để vừa một dòng. */
const SHORT: Record<string, string> = {
  "Tiến độ học tập": "Tiến độ",
  "Ví & Học phí": "Học phí",
  "Lịch học gia đình": "Lịch học",
  "Gia sư của con": "Gia sư",
  "Yêu cầu học viên": "Yêu cầu",
  "Thu nhập & Rút tiền": "Thu nhập",
  "Duyệt bằng cấp KYC": "KYC",
  "Đối soát thanh toán": "Thanh toán",
  "Khiếu nại & Tranh chấp": "Khiếu nại",
};

export function MobileBottomNav({ role }: { role: UserRole }) {
  const pathname = usePathname() ?? "";
  const items = ROLE_NAV[role].bottom;
  const active = items.find((entry) => isNavActive(entry.href, pathname))?.href ?? null;
  const { unreadCategoryMap } = useNotification();
  const navRef = useRef<HTMLElement>(null);
  const indicatorRef = useRef<HTMLSpanElement>(null);
  useHop(navRef, indicatorRef, active);

  return (
    <nav ref={navRef} aria-label="Điều hướng chính" className="fixed inset-x-0 bottom-0 z-30 flex border-t border-line bg-surface pb-[env(safe-area-inset-bottom)] lg:hidden">
      <span ref={indicatorRef} aria-hidden="true" className="absolute top-0 left-0 opacity-0">
        <span className="absolute inset-x-3 top-1 bottom-1 rounded-2xl bg-action-soft" />
      </span>
      {items.map((entry) => {
        const Icon = entry.icon;
        const isActive = entry.href === active;
        return (
          <Link
            key={entry.href}
            href={entry.href}
            data-hop={entry.href}
            aria-current={isActive ? "page" : undefined}
            className={cn("relative flex h-16 flex-1 flex-col items-center justify-center gap-0.5 text-xs", isActive ? "font-medium text-ink" : "text-muted")}
          >
            <span className="relative">
              <Icon className={cn("size-5", isActive && "text-action-strong")} />
              {unreadCategoryMap?.[NAV_NOTIFICATION_CATEGORY[entry.href]] && <span data-testid="bottom-nav-notification-dot" className="absolute -top-0.5 -right-1 size-2 rounded-full bg-danger" />}
            </span>
            <span className="max-w-full truncate px-1">{SHORT[entry.label] ?? entry.label}</span>
          </Link>
        );
      })}
    </nav>
  );
}

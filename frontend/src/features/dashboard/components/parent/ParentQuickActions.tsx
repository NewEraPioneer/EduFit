"use client";

import React from "react";
import Link from "next/link";
import { MessageSquare, Calendar, CreditCard, ChevronRight } from "lucide-react";

export function ParentQuickActions() {
  return (
    <div className="bg-surface p-5 rounded-2xl border border-line/80 shadow-xs space-y-2">
      <h4 className="text-xs font-medium text-muted mb-2">
        Thao tác nhanh
      </h4>
      <Link
        href="/parent/schedule"
        className="w-full p-2.5 rounded-xl hover:bg-paper flex items-center justify-between text-xs font-medium text-ink-2 transition-colors"
      >
        <div className="flex items-center gap-2.5">
          <Calendar className="size-4 text-success-strong" />
          <span>Xem lịch học của các con</span>
        </div>
        <ChevronRight className="size-4 text-muted" />
      </Link>
      <Link
        href="/parent/billing"
        className="w-full p-2.5 rounded-xl hover:bg-paper flex items-center justify-between text-xs font-medium text-ink-2 transition-colors"
      >
        <div className="flex items-center gap-2.5">
          <CreditCard className="size-4 text-action-strong" />
          <div className="flex items-center gap-2">
            <span>Thông tin học phí & gói học</span>
            <span className="px-1.5 py-0.5 text-xs font-medium rounded bg-action-soft text-action-strong">
              Ví & học phí
            </span>
          </div>
        </div>
        <ChevronRight className="size-4 text-muted" />
      </Link>
      <button
        type="button"
        disabled
        className="w-full p-2.5 rounded-xl flex items-center justify-between text-xs font-medium text-muted text-left cursor-not-allowed"
      >
        <div className="flex items-center gap-2.5">
          <MessageSquare className="size-4 text-brand-strong" />
          <span>Nhóm trò chuyện cùng gia sư · Ví & học phí</span>
        </div>
        <ChevronRight className="size-4 text-muted" />
      </button>
    </div>
  );
}

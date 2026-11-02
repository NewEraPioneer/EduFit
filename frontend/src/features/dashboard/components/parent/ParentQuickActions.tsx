"use client";

import React from "react";
import Link from "next/link";
import { MessageSquare, Calendar, CreditCard, ChevronRight } from "lucide-react";

export function ParentQuickActions() {
  return (
    <div className="bg-white p-5 rounded-2xl border border-stone-200/80 shadow-xs space-y-2">
      <h4 className="text-xs font-bold text-neutral-400 uppercase tracking-wider mb-2">
        Thao tác nhanh
      </h4>
      <Link
        href="/parent/schedule"
        className="w-full p-2.5 rounded-xl hover:bg-stone-50 flex items-center justify-between text-xs font-semibold text-neutral-700 transition-colors"
      >
        <div className="flex items-center gap-2.5">
          <Calendar className="size-4 text-emerald-600" />
          <span>Xem lịch học của các con</span>
        </div>
        <ChevronRight className="size-4 text-neutral-400" />
      </Link>
      <Link
        href="/parent/billing"
        className="w-full p-2.5 rounded-xl hover:bg-stone-50 flex items-center justify-between text-xs font-semibold text-neutral-700 transition-colors"
      >
        <div className="flex items-center gap-2.5">
          <CreditCard className="size-4 text-amber-600" />
          <div className="flex items-center gap-2">
            <span>Thông tin học phí & gói học</span>
            <span className="px-1.5 py-0.5 text-[9px] font-bold rounded bg-amber-100 text-amber-800">
              Ví & học phí
            </span>
          </div>
        </div>
        <ChevronRight className="size-4 text-neutral-400" />
      </Link>
      <button
        type="button"
        disabled
        className="w-full p-2.5 rounded-xl flex items-center justify-between text-xs font-semibold text-neutral-500 text-left cursor-not-allowed"
      >
        <div className="flex items-center gap-2.5">
          <MessageSquare className="size-4 text-sky-600" />
          <span>Nhóm trò chuyện cùng gia sư · Ví & học phí</span>
        </div>
        <ChevronRight className="size-4 text-neutral-400" />
      </button>
    </div>
  );
}

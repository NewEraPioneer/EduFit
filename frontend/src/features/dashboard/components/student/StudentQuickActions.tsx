"use client";

import React from "react";
import Link from "next/link";
import { Search, CreditCard, MessageSquare, ChevronRight } from "lucide-react";

export function StudentQuickActions() {
  return (
    <div className="bg-white dark:bg-stone-900 p-5 rounded-2xl border border-stone-200/80 dark:border-stone-800 shadow-xs space-y-2">
      <h4 className="text-xs font-bold text-neutral-400 dark:text-stone-400 uppercase tracking-wider mb-2">
        Thao tác nhanh
      </h4>
      <Link
        href="/student/tutors"
        className="w-full p-2.5 rounded-xl hover:bg-stone-50 dark:hover:bg-stone-800 flex items-center justify-between text-xs font-semibold text-neutral-700 dark:text-stone-200 transition-colors"
      >
        <div className="flex items-center gap-2.5">
          <Search className="size-4 text-neutral-500 dark:text-stone-400" />
          <span>Tìm gia sư môn mới</span>
        </div>
        <ChevronRight className="size-4 text-neutral-400 dark:text-stone-400" />
      </Link>
      <Link
        href="/student/payments"
        className="w-full p-2.5 rounded-xl hover:bg-stone-50 dark:hover:bg-stone-800 flex items-center justify-between text-xs font-semibold text-neutral-700 dark:text-stone-200 transition-colors"
      >
        <div className="flex items-center gap-2.5">
          <CreditCard className="size-4 text-neutral-500 dark:text-stone-400" />
          <div className="flex items-center gap-2">
            <span>Thông tin học phí</span>
            <span className="px-1.5 py-0.5 text-[9px] font-bold rounded bg-amber-100 text-amber-800 dark:bg-amber-900/50 dark:text-amber-300">
              Ví & học phí
            </span>
          </div>
        </div>
        <ChevronRight className="size-4 text-neutral-400 dark:text-stone-400" />
      </Link>
      <button
        type="button"
        disabled
        className="w-full p-2.5 rounded-xl flex items-center justify-between text-xs font-semibold text-neutral-500 dark:text-stone-400 text-left cursor-not-allowed"
      >
        <div className="flex items-center gap-2.5">
          <MessageSquare className="size-4 text-neutral-500 dark:text-stone-400" />
          <span>Nhắn tin cho gia sư · Ví & học phí</span>
        </div>
        <ChevronRight className="size-4 text-neutral-400 dark:text-stone-400" />
      </button>
    </div>
  );
}

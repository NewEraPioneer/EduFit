"use client";

import React from "react";
import Link from "next/link";
import { Calendar, TrendingUp, CreditCard, Clock } from "lucide-react";
import { ChildInfo } from "./ParentChildSwitcher";

interface ParentKpiGridProps {
  child: ChildInfo;
}

export function ParentKpiGrid({ child }: ParentKpiGridProps) {
  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <div className="bg-white p-5 rounded-2xl border border-stone-200/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-neutral-400">
          <span className="text-xs font-bold uppercase tracking-wider">Số buổi đã học</span>
          <Calendar className="size-4 text-emerald-600" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-extrabold font-heading text-neutral-900">{child.completedLessons}</div>
          <div className="text-[11px] text-emerald-600 font-semibold mt-0.5">Tham gia đầy đủ 100%</div>
        </div>
      </div>

      <div className="bg-white p-5 rounded-2xl border border-stone-200/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-neutral-400">
          <span className="text-xs font-bold uppercase tracking-wider">Điểm số trung bình</span>
          <TrendingUp className="size-4 text-sky-600" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-extrabold font-heading text-neutral-900">{child.avgScore} / 10</div>
          <div className="text-[11px] text-sky-700 font-semibold mt-0.5">+0.8 điểm so với tháng trước</div>
        </div>
      </div>

      <div className="bg-white p-5 rounded-2xl border border-stone-200/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-neutral-400">
          <span className="text-xs font-bold uppercase tracking-wider">Học phí tháng này</span>
          <CreditCard className="size-4 text-amber-600" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-extrabold font-heading text-neutral-900"><Link href="/parent/billing" className="hover:underline">Xem học phí</Link></div>
          <div className="text-[11px] text-emerald-600 font-semibold mt-0.5">Chi tiết trong ví học phí</div>
        </div>
      </div>

      <div className="bg-white p-5 rounded-2xl border border-stone-200/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-neutral-400">
          <span className="text-xs font-bold uppercase tracking-wider">Buổi học kế tiếp</span>
          <Clock className="size-4 text-emerald-600" />
        </div>
        <div className="mt-3">
          <div className="text-sm font-extrabold text-neutral-900 truncate">{child.nextSession}</div>
          <div className="text-[11px] text-neutral-400 font-medium mt-0.5">Gia sư đã xác nhận</div>
        </div>
      </div>
    </div>
  );
}

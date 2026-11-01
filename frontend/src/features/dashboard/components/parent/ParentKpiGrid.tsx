"use client";

import React from "react";
import Link from "next/link";
import { Calendar, TrendingUp, CreditCard, Clock } from "lucide-react";
import { ChildInfo } from "./ParentChildSwitcher";

interface ParentKpiGridProps {
  child: ChildInfo;
}

function formatNextSession(value: string | null): string | null {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleString("vi-VN", {
    weekday: "short",
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function ParentKpiGrid({ child }: ParentKpiGridProps) {
  const nextSession = formatNextSession(child.nextSessionStart);

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <div className="bg-white p-5 rounded-2xl border border-stone-200/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-neutral-400">
          <span className="text-xs font-bold uppercase tracking-wider">Số buổi đã học</span>
          <Calendar className="size-4 text-emerald-600" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-extrabold font-heading text-neutral-900">{child.completedLessons}</div>
          <div className="text-[11px] text-neutral-500 font-semibold mt-0.5">
            {child.attendanceRate !== null
              ? `Chuyên cần ${child.attendanceRate}%`
              : "Chưa có buổi học kết thúc"}
          </div>
        </div>
      </div>

      <div className="bg-white p-5 rounded-2xl border border-stone-200/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-neutral-400">
          <span className="text-xs font-bold uppercase tracking-wider">Tiến độ mục tiêu</span>
          <TrendingUp className="size-4 text-sky-600" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-extrabold font-heading text-neutral-900">
            {child.progressPercent !== null ? `${child.progressPercent}%` : "—"}
          </div>
          <div className="text-[11px] text-neutral-500 font-semibold mt-0.5 truncate">
            {child.progressPercent !== null
              ? child.goalTitle || "Theo lộ trình của gia sư"
              : "Gia sư chưa lập lộ trình học"}
          </div>
        </div>
      </div>

      <div className="bg-white p-5 rounded-2xl border border-stone-200/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-neutral-400">
          <span className="text-xs font-bold uppercase tracking-wider">Học phí</span>
          <CreditCard className="size-4 text-amber-600" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-extrabold font-heading text-neutral-900"><Link href="/parent/billing" className="hover:underline">Xem học phí</Link></div>
          <div className="text-[11px] text-neutral-500 font-semibold mt-0.5">Chi tiết trong ví học phí</div>
        </div>
      </div>

      <div className="bg-white p-5 rounded-2xl border border-stone-200/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-neutral-400">
          <span className="text-xs font-bold uppercase tracking-wider">Buổi học kế tiếp</span>
          <Clock className="size-4 text-emerald-600" />
        </div>
        <div className="mt-3">
          <div className="text-sm font-extrabold text-neutral-900 truncate">
            {nextSession ?? "Chưa có buổi sắp tới"}
          </div>
          {nextSession && (
            <div className="text-[11px] text-neutral-400 font-medium mt-0.5">Đã được hai bên xác nhận</div>
          )}
        </div>
      </div>
    </div>
  );
}

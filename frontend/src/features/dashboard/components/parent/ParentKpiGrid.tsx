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
      <div className="card-lift bg-surface p-5 rounded-2xl border border-line/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-muted">
          <span className="text-xs font-medium">Số buổi đã học</span>
          <Calendar className="size-4 text-success-strong" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-bold font-heading text-ink">{child.completedLessons}</div>
          <div className="text-xs text-muted font-medium mt-0.5">
            {child.attendanceRate !== null
              ? `Chuyên cần ${child.attendanceRate}%`
              : "Chưa có buổi học kết thúc"}
          </div>
        </div>
      </div>

      <div className="card-lift bg-surface p-5 rounded-2xl border border-line/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-muted">
          <span className="text-xs font-medium">Tiến độ mục tiêu</span>
          <TrendingUp className="size-4 text-brand-strong" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-bold font-heading text-ink">
            {child.progressPercent !== null ? `${child.progressPercent}%` : "—"}
          </div>
          <div className="text-xs text-muted font-medium mt-0.5 truncate">
            {child.progressPercent !== null
              ? child.goalTitle || "Theo lộ trình của gia sư"
              : "Gia sư chưa lập lộ trình học"}
          </div>
        </div>
      </div>

      <div className="card-lift bg-surface p-5 rounded-2xl border border-line/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-muted">
          <span className="text-xs font-medium">Học phí</span>
          <CreditCard className="size-4 text-action-strong" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-bold font-heading text-ink"><Link href="/parent/billing" className="hover:underline">Xem học phí</Link></div>
          <div className="text-xs text-muted font-medium mt-0.5">Chi tiết trong ví học phí</div>
        </div>
      </div>

      <div className="card-lift bg-surface p-5 rounded-2xl border border-line/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-muted">
          <span className="text-xs font-medium">Buổi học kế tiếp</span>
          <Clock className="size-4 text-success-strong" />
        </div>
        <div className="mt-3">
          <div className="text-sm font-medium text-ink truncate">
            {nextSession ?? "Chưa có buổi sắp tới"}
          </div>
          {nextSession && (
            <div className="text-xs text-muted font-medium mt-0.5">Đã được hai bên xác nhận</div>
          )}
        </div>
      </div>
    </div>
  );
}

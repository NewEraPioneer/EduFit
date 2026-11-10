"use client";

import React from "react";
import Link from "next/link";
import { Calendar, Users, DollarSign, Star } from "lucide-react";

interface TutorKpiGridProps {
  completedSessions: number;
  plannedSessions: number;
  activeStudents: number;
  ratingAverage: number;
  reviewCount: number;
}

export function TutorKpiGrid({
  completedSessions,
  plannedSessions,
  activeStudents,
  ratingAverage,
  reviewCount,
}: TutorKpiGridProps) {
  const completionRate = plannedSessions > 0
    ? Math.round((completedSessions / plannedSessions) * 100)
    : null;

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <div className="card-lift bg-surface p-5 rounded-2xl border border-line/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-muted">
          <span className="text-xs font-medium">Buổi đã dạy</span>
          <Calendar className="size-4 text-brand-strong" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-bold font-heading text-ink">
            {completedSessions} / {plannedSessions}
          </div>
          <div className="text-xs text-muted font-medium mt-0.5">
            {completionRate !== null
              ? `${completionRate}% tổng số buổi của các lớp`
              : "Chưa có buổi học nào"}
          </div>
        </div>
      </div>

      <div className="card-lift bg-surface p-5 rounded-2xl border border-line/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-muted">
          <span className="text-xs font-medium">Học sinh đang dạy</span>
          <Users className="size-4 text-brand-strong" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-bold font-heading text-ink">{activeStudents} học sinh</div>
          <div className="text-xs text-muted font-medium mt-0.5">
            {activeStudents > 0 ? "Đang theo học" : "Chưa có lớp đang hoạt động"}
          </div>
        </div>
      </div>

      <div className="card-lift bg-surface p-5 rounded-2xl border border-line/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-muted">
          <span className="text-xs font-medium">Thu nhập theo ví</span>
          <DollarSign className="size-4 text-success-strong" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-bold font-heading text-ink">
            <Link href="/tutor/earnings" className="hover:underline">Xem thu nhập</Link>
          </div>
          <div className="text-xs text-muted font-medium mt-0.5">
            Đối chiếu tiền đã tất toán trong ví
          </div>
        </div>
      </div>

      <div className="card-lift bg-surface p-5 rounded-2xl border border-line/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-muted">
          <span className="text-xs font-medium">Đánh giá trung bình</span>
          <Star className="size-4 text-action-strong fill-action" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-bold font-heading text-ink">
            {reviewCount > 0 ? `${ratingAverage.toFixed(1)} / 5.0` : "—"}
          </div>
          <div className="text-xs text-muted font-medium mt-0.5">
            {reviewCount > 0
              ? `Từ ${reviewCount} đánh giá phụ huynh & học sinh`
              : "Chưa có đánh giá nào"}
          </div>
        </div>
      </div>
    </div>
  );
}

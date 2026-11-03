"use client";

import React from "react";
import Link from "next/link";
import { Sparkles, ArrowRight, Target, PlusCircle } from "lucide-react";
import { EmptyState } from "@/shared/ui/EmptyState";

export interface StudentGoalItem {
  id: string;
  grade: string;
  title: string;
  roadmapDesc: string;
  /**
   * true: các bước lấy từ lộ trình (milestone) gia sư lập, % do backend tính.
   * false: chưa có lộ trình, chỉ hiện các bước khởi đầu thực tế (có mục tiêu, có lớp, đã học buổi đầu).
   */
  hasPlan: boolean;
  /** % tiến độ theo trọng số milestone từ backend; null khi chưa có lộ trình. */
  progressPercent: number | null;
  completedMilestones: number;
  totalMilestones: number;
  milestones?: Array<{ label: string; completed: boolean }>;
  nextSessionText?: string;
}

interface StudentGoalProgressCardProps {
  goal?: StudentGoalItem | null;
}

export function StudentGoalProgressCard({ goal }: StudentGoalProgressCardProps) {
  // Khi học viên mới chưa thiết lập mục tiêu nào trong database
  if (!goal) {
    return (
      <div className="bg-white dark:bg-stone-900 p-6 sm:p-7 rounded-3xl border border-stone-200/80 dark:border-stone-800 shadow-xs space-y-4">
        <div className="flex justify-between items-center">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-stone-100 dark:bg-stone-800 rounded-full text-xs font-semibold text-neutral-700 dark:text-stone-300">
            <Sparkles className="size-3.5 text-amber-500" />
            <span>Mục tiêu đang tập trung</span>
          </div>
          <span className="text-xs text-neutral-400 dark:text-stone-400 font-medium">Chưa kích hoạt</span>
        </div>

        <EmptyState
          icon={Target}
          theme="amber"
          compact
          title="Bạn chưa thiết lập mục tiêu học tập"
          description="Thiết lập môn học trọng điểm và mục tiêu điểm số (ví dụ: Thi vào 10, Ôn chuyên, Lấy lại căn bản) để hệ thống AI Match gợi ý gia sư và lộ trình chính xác nhất."
          action={{
            label: "Cập nhật hồ sơ & Mục tiêu học",
            href: "/student/profile",
            icon: PlusCircle,
          }}
          secondaryAction={{
            label: "Tìm kiếm gia sư trước",
            href: "/student/tutors",
          }}
        />
      </div>
    );
  }

  const remaining = Math.max(0, goal.totalMilestones - goal.completedMilestones);
  const unit = goal.hasPlan ? "cột mốc" : "bước";

  return (
    <div className="bg-white dark:bg-stone-900 p-6 sm:p-7 rounded-3xl border border-stone-200/80 dark:border-stone-800 shadow-xs flex flex-col justify-between gap-5">
      <div>
        <div className="flex justify-between items-center mb-3">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-stone-100 dark:bg-stone-800 rounded-full text-xs font-semibold text-neutral-700 dark:text-stone-300">
            <Sparkles className="size-3.5 text-amber-500" />
            <span>Mục tiêu đang tập trung</span>
          </div>
          {goal.grade && (
            <span className="px-2.5 py-0.5 bg-amber-200 dark:bg-amber-950/80 text-amber-950 dark:text-amber-300 rounded-full text-xs font-bold">
              {goal.grade}
            </span>
          )}
        </div>

        <h3 className="font-heading text-lg sm:text-xl font-bold text-neutral-900 dark:text-stone-100 mb-1">
          {goal.title}
        </h3>
        <p className="text-xs text-neutral-500 dark:text-stone-300 font-medium font-sans">
          {goal.roadmapDesc}
        </p>
      </div>

      <div className="space-y-3">
        <div className="space-y-1.5">
          <div className="flex justify-between items-center text-xs font-semibold">
            <span className="text-neutral-600">
              {goal.hasPlan ? "Tiến độ theo lộ trình của gia sư" : "Các bước khởi đầu"}
            </span>
            {goal.progressPercent !== null && (
              <span className="text-emerald-600 font-bold text-sm">{goal.progressPercent}%</span>
            )}
          </div>
          {goal.progressPercent !== null && (
            <div className="w-full h-2.5 bg-stone-100 rounded-full overflow-hidden">
              <div
                className="h-full bg-emerald-500 rounded-full transition-all duration-700"
                style={{ width: `${goal.progressPercent}%` }}
              />
            </div>
          )}
          <div className="flex justify-between items-center text-[11px] text-neutral-400 font-medium">
            <span>{goal.completedMilestones} / {goal.totalMilestones} {unit} đã hoàn thành</span>
            <span>
              {remaining > 0
                ? `Còn ${remaining} ${unit}`
                : goal.hasPlan
                  ? "Đã đạt toàn bộ cột mốc"
                  : "Chờ gia sư lập lộ trình học"}
            </span>
          </div>
        </div>

        {/* Danh sách các bước cụ thể */}
        {goal.milestones && goal.milestones.length > 0 && (
          <div className="pt-2 border-t border-stone-100 grid grid-cols-1 sm:grid-cols-3 gap-2">
            {goal.milestones.map((m, idx) => (
              <div
                key={idx}
                className={`p-2 rounded-xl text-[11px] flex items-center gap-1.5 ${
                  m.completed
                    ? "bg-emerald-50 text-emerald-800 font-medium"
                    : "bg-stone-50 text-neutral-500"
                }`}
              >
                <span
                  className={`size-4 rounded-full flex items-center justify-center text-[10px] shrink-0 font-bold ${
                    m.completed
                      ? "bg-emerald-600 text-white"
                      : "bg-stone-200 text-neutral-600"
                  }`}
                >
                  {m.completed ? "✓" : idx + 1}
                </span>
                <span className="truncate">{m.label}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="pt-3 flex items-center justify-between border-t border-stone-100 text-xs">
        <span className="text-neutral-500 font-medium">
          {goal.nextSessionText || "Chưa có buổi học tiếp theo"}
        </span>
        <Link
          href="/student/progress"
          className="text-amber-600 hover:text-amber-700 font-bold flex items-center gap-1 transition-transform hover:translate-x-0.5"
        >
          Xem chi tiết lộ trình <ArrowRight className="size-3.5" />
        </Link>
      </div>
    </div>
  );
}

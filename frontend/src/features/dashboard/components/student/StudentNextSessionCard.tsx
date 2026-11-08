"use client";

import React from "react";
import Link from "next/link";
import { Clock, User, CalendarPlus, Search } from "lucide-react";
import { EmptyState } from "@/shared/ui/EmptyState";

export interface SessionInfo {
  id: string;
  startAt: string;
  dayShort: string;
  dateNum: string;
  lessonTitle: string;
  timeSlot: string;
  tutorName: string;
  status: string;
}

interface StudentNextSessionCardProps {
  session?: SessionInfo | null;
}

export function StudentNextSessionCard({ session }: StudentNextSessionCardProps) {
  // Nếu chưa có buổi học nào được xếp lịch
  if (!session) {
    return (
      <div className="bg-white dark:bg-stone-900 p-6 sm:p-7 rounded-3xl border border-stone-200/80 dark:border-stone-800 shadow-xs space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="font-heading text-base sm:text-lg font-bold text-neutral-900 dark:text-stone-100">
            Buổi học sắp tới
          </h3>
          <span className="text-xs text-neutral-400 dark:text-stone-400 font-medium">Lịch tuần này</span>
        </div>

        <EmptyState
          icon={CalendarPlus}
          theme="amber"
          compact
          title="Chưa có buổi học nào sắp diễn ra"
          description="Bạn chưa đăng ký lịch học nào trong tuần này. Hãy tìm gia sư môn học bạn cần và gửi yêu cầu để bắt đầu buổi học đầu tiên."
          action={{
            label: "Tìm gia sư & Đặt lịch học",
            href: "/student/tutors",
            icon: Search,
          }}
        />
      </div>
    );
  }

  return (
    <div className="bg-white dark:bg-stone-900 p-4 sm:p-5 rounded-3xl border border-stone-200/80 dark:border-stone-800 shadow-xs">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-heading text-base sm:text-lg font-bold text-neutral-900 dark:text-stone-100">
          Buổi học sắp tới
        </h3>
        <Link href="/student/schedule" className="text-xs text-amber-600 dark:text-amber-400 font-bold hover:underline">
          Xem toàn bộ lịch
        </Link>
      </div>

      <div className="p-3 bg-stone-50 dark:bg-stone-850 border border-stone-200/70 dark:border-stone-800 rounded-2xl space-y-3">
        <div className="flex items-start gap-3">
          <div className="size-12 rounded-2xl bg-amber-400 text-neutral-900 flex flex-col items-center justify-center shrink-0 font-bold">
            <span className="text-[10px] uppercase tracking-wider">{session.dayShort}</span>
            <span className="text-base leading-none">{session.dateNum}</span>
          </div>
          <div className="min-w-0 wrap-anywhere">
            <div className="text-xs sm:text-sm font-bold text-neutral-900 dark:text-stone-100">
              {session.lessonTitle}
            </div>
            <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs font-medium text-neutral-500 dark:text-stone-300">
              <span className="flex min-w-0 items-start gap-1">
                <Clock className="size-3.5 shrink-0" /> <span>{session.timeSlot}</span>
              </span>
              <span className="flex min-w-0 items-start gap-1">
                <User className="size-3.5 shrink-0" /> <span>{session.tutorName}</span>
              </span>
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <span className="px-2.5 py-1 bg-emerald-50 text-emerald-800 rounded-full text-[11px] font-bold border border-emerald-200/50">
            {session.status}
          </span>
          <Link
            href={{ pathname: "/student/schedule", query: { sessionId: session.id, startAt: session.startAt } }}
            className="px-4 py-2 bg-sky-700 hover:bg-sky-800 text-white text-xs font-bold rounded-xl shadow-xs transition-colors"
          >
            Xem buổi học
          </Link>
        </div>
      </div>
    </div>
  );
}

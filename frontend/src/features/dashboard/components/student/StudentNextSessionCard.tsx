"use client";

import React from "react";
import Link from "next/link";
import { Clock, User, CalendarPlus, Search } from "lucide-react";
import { Button } from "@/shared/ui/Button";
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
      <div className="bg-surface p-6 sm:p-7 rounded-3xl border border-line/80 shadow-xs space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="font-heading text-base sm:text-lg font-bold text-ink">
            Buổi học sắp tới
          </h3>
          <span className="text-xs text-muted font-medium">Lịch tuần này</span>
        </div>

        <EmptyState
          icon={CalendarPlus}
          compact
          title="Chưa có buổi học nào sắp diễn ra"
          description="Tuần này bạn chưa có lịch học. Tìm gia sư và gửi yêu cầu để bắt đầu."
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
    <div className="card-lift bg-surface p-4 sm:p-5 rounded-3xl border border-line/80 shadow-xs">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-heading text-base sm:text-lg font-bold text-ink">
          Buổi học sắp tới
        </h3>
        <Link href="/student/schedule" className="text-xs text-action-strong font-medium hover:underline">
          Xem toàn bộ lịch
        </Link>
      </div>

      <div className="p-3 bg-paper border border-line/70 rounded-2xl space-y-3">
        <div className="flex items-start gap-3">
          <div className="size-12 rounded-2xl bg-action text-on-action flex flex-col items-center justify-center shrink-0 font-medium">
            <span className="text-xs">{session.dayShort}</span>
            <span className="text-base leading-none">{session.dateNum}</span>
          </div>
          <div className="min-w-0 wrap-anywhere">
            <div className="text-xs sm:text-sm font-medium text-ink">
              {session.lessonTitle}
            </div>
            <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs font-medium text-muted">
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
          <span className="px-2.5 py-1 bg-success-soft text-success-strong rounded-full text-xs font-medium border border-success-line/50">
            {session.status}
          </span>
          <Button href={`/student/schedule?${new URLSearchParams({ sessionId: session.id, startAt: session.startAt })}`} size="sm">
            Xem buổi học
          </Button>
        </div>
      </div>
    </div>
  );
}

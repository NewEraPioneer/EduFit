"use client";

import React from "react";
import Link from "next/link";
import { Clock, CalendarPlus } from "lucide-react";
import { EmptyState } from "@/shared/ui/EmptyState";

export interface UpcomingClassItem {
  time: string;
  title: string;
  topic: string;
  statusBadge: string;
  actionText: string;
  isPrimary: boolean;
}

interface TutorUpcomingClassesProps {
  classes?: UpcomingClassItem[];
}

export function TutorUpcomingClasses({ classes = [] }: TutorUpcomingClassesProps) {
  if (classes.length === 0) {
    return (
      <div className="bg-surface p-6 rounded-3xl border border-line/80 shadow-xs space-y-4">
        <div className="flex justify-between items-center">
          <h3 className="font-heading text-base sm:text-lg font-bold text-ink">
            Lịch dạy hôm nay
          </h3>
          <span className="text-xs text-muted font-medium">0 buổi</span>
        </div>

        <EmptyState
          icon={CalendarPlus}
          compact
          title="Chưa có lịch dạy nào được xếp hôm nay"
          description="Cập nhật ma trận khung giờ rảnh trong tuần để hệ thống tự động gợi ý lịch học tương thích tới học sinh & phụ huynh."
          action={{
            label: "Cập nhật ma trận lịch rảnh",
            href: "/tutor/profile",
            icon: Clock,
          }}
        />
      </div>
    );
  }

  return (
    <div className="bg-surface p-6 rounded-3xl border border-line/80 shadow-xs">
      <div className="flex justify-between items-center mb-4">
        <h3 className="font-heading text-base sm:text-lg font-bold text-ink">
          Lịch dạy hôm nay
        </h3>
        <Link href="/tutor/schedule" className="text-xs text-brand-strong font-medium hover:underline">
          Xem toàn bộ tuần
        </Link>
      </div>

      <div className="space-y-3">
        {classes.map((cls, idx) => (
          <div
            key={idx}
            className="p-4 rounded-2xl bg-paper border border-line/70 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
          >
            <div className="flex items-center gap-3.5">
              <div
                className={`size-11 rounded-xl font-medium text-xs flex flex-col items-center justify-center shrink-0 ${
                  cls.isPrimary ? "bg-brand-soft text-brand-strong" : "bg-line text-ink"
                }`}
              >
                <span>{cls.time}</span>
              </div>
              <div>
                <h4 className="text-xs sm:text-sm font-medium text-ink">{cls.title}</h4>
                <p className="text-xs text-muted mt-0.5">{cls.topic}</p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <span
                className={`px-2.5 py-1 rounded-full text-xs font-medium ${
                  cls.isPrimary
                    ? "bg-brand-soft text-brand-strong border border-brand-line"
                    : "bg-sunken text-ink-2"
                }`}
              >
                {cls.statusBadge}
              </span>
              <button
                type="button"
                className={`px-3 py-1.5 rounded-xl text-xs font-medium transition-colors cursor-pointer ${
                  cls.isPrimary
                    ? "bg-night hover:bg-night text-white shadow-xs"
                    : "border border-line hover:bg-paper text-ink-2"
                }`}
              >
                {cls.actionText}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

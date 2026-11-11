"use client";

import React from "react";
import Link from "next/link";
import { MessageSquare, Sparkles, GraduationCap, Search } from "lucide-react";
import { EmptyState } from "@/shared/ui/EmptyState";

interface ActiveTutorItem {
  name: string;
  subject: string;
  scheduleStatus: string;
  progress: string;
}

interface ParentActiveTutorsProps {
  tutors?: ActiveTutorItem[];
}

export function ParentActiveTutors({ tutors = [] }: ParentActiveTutorsProps) {
  if (tutors.length === 0) {
    return (
      <div className="bg-surface p-6 rounded-3xl border border-line/80 shadow-xs space-y-4">
        <div className="flex justify-between items-center">
          <h3 className="font-heading text-base sm:text-lg font-bold text-ink">
            Gia sư đang dạy
          </h3>
          <span className="text-xs text-muted font-medium">0 gia sư</span>
        </div>

        <EmptyState
          icon={GraduationCap}
          compact
          title="Chưa có gia sư nào đang dạy kèm"
          description="Xem gia sư đã được EduFit xác minh chứng chỉ và lý lịch."
          action={{
            label: "Tìm kiếm gia sư cho con",
            href: "/parent/tutors",
            icon: Search,
          }}
          secondaryAction={{
            label: "Xem gợi ý AI Match",
            href: "/parent/tutors?tab=matches",
            icon: Sparkles,
          }}
        />
      </div>
    );
  }

  return (
    <div className="bg-surface p-6 rounded-3xl border border-line/80 shadow-xs">
      <div className="flex justify-between items-center mb-4">
        <h3 className="font-heading text-base sm:text-lg font-bold text-ink">
          Gia sư đang dạy
        </h3>
        <Link href="/parent/tutors" className="text-xs text-success-strong font-medium hover:underline">
          Tìm thêm gia sư
        </Link>
      </div>

      <div className="space-y-4">
        {tutors.map((tutor, idx) => (
          <div
            key={idx}
            className="p-4 rounded-2xl border border-line/80 hover:border-success-line transition-colors space-y-3"
          >
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <div className="flex items-center gap-3">
                <div className="size-10 rounded-xl bg-success-soft text-success-strong font-medium text-sm flex items-center justify-center shrink-0">
                  {tutor.name.charAt(0)}
                </div>
                <div>
                  <h4 className="text-xs sm:text-sm font-medium text-ink">{tutor.name}</h4>
                  <div className="flex items-center gap-2 text-xs text-muted">
                    <span className="font-medium text-ink-2">{tutor.subject}</span>
                    {tutor.progress && (
                      <>
                        <span>·</span>
                        <span className="font-medium text-success-strong">{tutor.progress}</span>
                      </>
                    )}
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2 self-end sm:self-auto">
                <button
                  type="button"
                  disabled
                  className="px-3 py-1.5 border border-line text-muted rounded-xl text-xs font-medium flex items-center gap-1.5 cursor-not-allowed"
                >
                  <MessageSquare className="size-3.5 text-muted" />
                  <span>Nhắn tin · Sắp ra mắt</span>
                </button>
              </div>
            </div>

            <div className="p-3 bg-paper rounded-xl border border-line/60 text-xs text-ink-2 space-y-1">
              <span className="text-xs font-medium text-muted">
                Lịch học
              </span>
              <p>{tutor.scheduleStatus}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

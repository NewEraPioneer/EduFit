"use client";

import React from "react";
import Link from "next/link";
import { ChevronRight, BookOpenCheck, Search, Sparkles } from "lucide-react";
import { EmptyState } from "@/shared/ui/EmptyState";

interface ActiveClassItem {
  subject: string;
  badgeColor: string;
  borderColor: string;
  frequency: string;
  title: string;
  tutor: string;
  progress: string;
}

interface StudentActiveClassesProps {
  classes?: ActiveClassItem[];
}

export function StudentActiveClasses({ classes = [] }: StudentActiveClassesProps) {
  if (classes.length === 0) {
    return (
      <div className="bg-surface p-6 sm:p-7 rounded-3xl border border-line/80 shadow-xs space-y-4">
        <div className="flex justify-between items-center">
          <h3 className="font-heading text-base sm:text-lg font-bold text-ink">
            Lớp học đang diễn ra
          </h3>
          <span className="text-xs text-muted font-medium">0 lớp</span>
        </div>

        <EmptyState
          icon={BookOpenCheck}
          compact
          title="Bạn chưa đăng ký lớp học nào"
          description="Tìm gia sư Toán, Tiếng Anh, Vật lý... đã được xác minh năng lực để bắt đầu lớp học."
          action={{
            label: "Xem danh sách gia sư",
            href: "/student/tutors",
            icon: Search,
          }}
          secondaryAction={{
            label: "Nhận gợi ý AI Match",
            href: "/student/tutors?tab=matches",
            icon: Sparkles,
          }}
        />
      </div>
    );
  }

  return (
    <div className="bg-surface p-6 sm:p-7 rounded-3xl border border-line/80 shadow-xs">
      <div className="flex justify-between items-center mb-4">
        <h3 className="font-heading text-base sm:text-lg font-bold text-ink">
          Lớp học đang diễn ra
        </h3>
        <Link href="/student/classes" className="text-xs text-action-strong font-medium hover:underline">
          Xem tất cả ({classes.length})
        </Link>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {classes.map((cls, idx) => (
          <div
            key={idx}
            className={`p-4 rounded-2xl border border-line ${cls.borderColor} transition-colors flex flex-col justify-between gap-3`}
          >
            <div className="flex items-center justify-between">
              <span className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${cls.badgeColor}`}>
                {cls.subject}
              </span>
              <span className="text-xs text-muted font-medium">{cls.frequency}</span>
            </div>
            <div>
              <h4 className="text-sm font-medium text-ink">{cls.title}</h4>
              <p className="text-xs text-muted mt-0.5">Gia sư: {cls.tutor}</p>
            </div>
            <div className="pt-2 border-t border-line flex items-center justify-between text-xs text-muted">
              <span>{cls.progress}</span>
              <ChevronRight className="size-4 text-muted" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

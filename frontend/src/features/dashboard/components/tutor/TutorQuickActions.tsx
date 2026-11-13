"use client";

import React from "react";
import Link from "next/link";
import { Compass, Clock, BookOpen, ChevronRight } from "lucide-react";

export function TutorQuickActions() {
  return (
    <div className="bg-surface p-5 rounded-2xl border border-line/80 shadow-xs space-y-2">
      <h4 className="text-xs font-medium text-muted mb-2">
        Thao tác giảng dạy
      </h4>
      <Link
        href="/tutor/schedule"
        className="w-full p-2.5 rounded-xl hover:bg-paper flex items-center justify-between text-xs font-medium text-ink-2 transition-colors"
      >
        <div className="flex items-center gap-2.5">
          <Clock className="size-4 text-brand-strong" />
          <span>Cập nhật lịch rảnh & bận</span>
        </div>
        <ChevronRight className="size-4 text-muted" />
      </Link>
      <Link
        href="/tutor/classes"
        className="w-full p-2.5 rounded-xl hover:bg-paper flex items-center justify-between text-xs font-medium text-ink-2 transition-colors"
      >
        <div className="flex items-center gap-2.5">
          <BookOpen className="size-4 text-brand-strong" />
          <span>Quản lý danh sách lớp học</span>
        </div>
        <ChevronRight className="size-4 text-muted" />
      </Link>
      <Link
        href="/tutor/profile"
        className="w-full p-2.5 rounded-xl hover:bg-paper flex items-center justify-between text-xs font-medium text-ink-2 transition-colors"
      >
        <div className="flex items-center gap-2.5">
          <Compass className="size-4 text-action-strong" />
          <span>Chỉnh sửa hồ sơ & học phí/giờ</span>
        </div>
        <ChevronRight className="size-4 text-muted" />
      </Link>
    </div>
  );
}

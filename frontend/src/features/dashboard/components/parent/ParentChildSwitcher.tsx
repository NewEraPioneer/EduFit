"use client";

import React from "react";
import Link from "next/link";
import { Plus } from "lucide-react";

export interface ChildInfo {
  id: number;
  studentId?: string;
  name: string;
  grade: string | null;
  tutorsCount: number;
  completedLessons: string;
  /** Tỉ lệ chuyên cần (%) = buổi hoàn thành / (hoàn thành + vắng), null khi chưa có buổi nào kết thúc. */
  attendanceRate: number | null;
  progressPercent: number | null;
  goalTitle: string | null;
  nextSessionStart: string | null;
}

interface ParentChildSwitcherProps {
  childrenList: ChildInfo[];
  selectedChild: number;
  onSelect: (index: number) => void;
}

export function ParentChildSwitcher({
  childrenList,
  selectedChild,
  onSelect,
}: ParentChildSwitcherProps) {
  return (
    <div className="flex items-center gap-2 overflow-x-auto pb-1">
      {childrenList.map((child, idx) => {
        const isActive = selectedChild === idx;
        return (
          <button
            key={child.id}
            type="button"
            onClick={() => onSelect(idx)}
            className={`px-4 py-2.5 rounded-2xl flex items-center gap-2 text-xs font-medium transition-all shrink-0 ${
              isActive
                ? "bg-success text-white shadow-xs"
                : "bg-surface hover:bg-sunken text-ink-2 border border-line"
            }`}
          >
            <span className={`size-2 rounded-full ${isActive ? "bg-surface" : "bg-success"}`} />
            <span>
              {child.name}
              {child.grade ? ` (${child.grade})` : ""}
            </span>
            <span className={`text-xs ${isActive ? "text-success-tint" : "text-muted"}`}>
              · {child.tutorsCount} gia sư
            </span>
          </button>
        );
      })}
      <Link
        href="/parent/children"
        className="px-3.5 py-2.5 rounded-2xl border border-dashed border-line-strong hover:border-success text-muted hover:text-success-strong text-xs font-medium flex items-center gap-1.5 transition-colors shrink-0"
      >
        <Plus className="size-3.5" />
        <span>Thêm con</span>
      </Link>
    </div>
  );
}

"use client";

import React from "react";
import Link from "next/link";
import { Plus } from "lucide-react";

export interface ChildInfo {
  id: number;
  studentId?: string;
  name: string;
  grade: string;
  tutorsCount: number;
  completedLessons: string;
  avgScore: string;
  nextSession: string;
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
            className={`px-4 py-2.5 rounded-2xl flex items-center gap-2 text-xs font-bold transition-all shrink-0 ${
              isActive
                ? "bg-emerald-600 text-white shadow-xs"
                : "bg-white hover:bg-stone-100 text-neutral-700 border border-stone-200"
            }`}
          >
            <span className={`size-2 rounded-full ${isActive ? "bg-white" : "bg-emerald-500"}`} />
            <span>
              {child.name} ({child.grade})
            </span>
            <span className={`text-[10px] ${isActive ? "text-emerald-100" : "text-neutral-400"}`}>
              · {child.tutorsCount} gia sư
            </span>
          </button>
        );
      })}
      <Link
        href="/parent/children"
        className="px-3.5 py-2.5 rounded-2xl border border-dashed border-stone-300 hover:border-emerald-400 text-neutral-500 hover:text-emerald-700 text-xs font-semibold flex items-center gap-1.5 transition-colors shrink-0"
      >
        <Plus className="size-3.5" />
        <span>Thêm con</span>
      </Link>
    </div>
  );
}

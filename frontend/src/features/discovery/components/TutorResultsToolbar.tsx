"use client";

import React from "react";
import { ChevronDown, LayoutGrid, List } from "lucide-react";

export type TutorViewMode = "list" | "grid";

export interface TutorResultsToolbarProps {
  totalResults?: number;
  currentSortVal: string;
  onSortChange: (e: React.ChangeEvent<HTMLSelectElement>) => void;
  viewMode: TutorViewMode;
  onViewModeChange: (mode: TutorViewMode) => void;
}

const VIEW_OPTIONS: Array<{ mode: TutorViewMode; label: string; Icon: typeof List }> = [
  { mode: "list", label: "Xem dạng danh sách", Icon: List },
  { mode: "grid", label: "Xem dạng lưới", Icon: LayoutGrid },
];

/** Số kết quả, cách sắp xếp và nút đổi giữa dạng danh sách / dạng lưới. */
export function TutorResultsToolbar({
  totalResults,
  currentSortVal,
  onSortChange,
  viewMode,
  onViewModeChange,
}: TutorResultsToolbarProps) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="font-heading text-sm font-bold text-ink">
        {totalResults ?? 0} gia sư phù hợp
      </span>

      <div className="flex items-center gap-2">
        <div className="relative">
          <select
            value={currentSortVal}
            onChange={onSortChange}
            aria-label="Sắp xếp kết quả"
            className="appearance-none cursor-pointer rounded-xl border border-line bg-card py-2 pl-3 pr-8 text-xs font-medium text-card-foreground focus:border-brand focus:outline-hidden"
          >
            <option value="relevance">Liên quan nhất</option>
            <option value="rating">Đánh giá cao nhất</option>
            <option value="price_asc">Học phí thấp đến cao</option>
            <option value="price_desc">Học phí cao đến thấp</option>
            <option value="experience">Nhiều kinh nghiệm nhất</option>
          </select>
          <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted" />
        </div>

        <div role="group" aria-label="Kiểu hiển thị" className="flex rounded-xl border border-line bg-card p-0.5">
          {VIEW_OPTIONS.map(({ mode, label, Icon }) => (
            <button
              key={mode}
              type="button"
              aria-label={label}
              aria-pressed={viewMode === mode}
              onClick={() => onViewModeChange(mode)}
              className={`inline-flex size-8 items-center justify-center rounded-lg transition-colors cursor-pointer ${
                viewMode === mode ? "bg-brand text-white" : "text-muted hover:text-ink"
              }`}
            >
              <Icon className="size-4" />
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

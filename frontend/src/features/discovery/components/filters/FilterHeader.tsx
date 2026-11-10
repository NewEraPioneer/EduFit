"use client";

import React from "react";
import { Search, ChevronDown, X } from "lucide-react";

export interface FilterHeaderProps {
  keywordInput: string;
  onKeywordChange: (val: string) => void;
  onClearKeyword: () => void;
  currentSortVal: string;
  onSortChange: (e: React.ChangeEvent<HTMLSelectElement>) => void;
}

export function FilterHeader({
  keywordInput,
  onKeywordChange,
  onClearKeyword,
  currentSortVal,
  onSortChange,
}: FilterHeaderProps) {
  return (
    <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
      {/* Search input field */}
      <div className="relative flex-1">
        <Search className="size-4 text-muted absolute left-4 top-1/2 -translate-y-1/2" />
        <input
          type="text"
          value={keywordInput}
          onChange={(e) => onKeywordChange(e.target.value)}
          placeholder="Tìm theo tên, môn học hoặc khu vực..."
          className="w-full pl-11 pr-4 py-2.5 bg-card border border-line/90 rounded-2xl text-xs text-card-foreground placeholder:text-muted focus:outline-hidden focus:border-brand focus:ring-2 focus:ring-brand/20 transition-all font-medium shadow-2xs"
        />
        {keywordInput && (
          <button
            type="button"
            onClick={onClearKeyword}
            className="absolute right-3.5 top-1/2 -translate-y-1/2 text-muted hover:text-ink-2"
          >
            <X className="size-3.5" />
          </button>
        )}
      </div>

      {/* Sort Select */}
      <div className="relative shrink-0">
        <select
          value={currentSortVal}
          onChange={onSortChange}
          aria-label="Sắp xếp kết quả tìm kiếm"
          className="w-full sm:w-auto appearance-none pl-4 pr-9 py-2.5 bg-card border border-line/90 rounded-2xl text-xs font-medium text-card-foreground focus:outline-hidden focus:border-brand shadow-2xs transition-colors cursor-pointer"
        >
          <option value="relevance">Liên quan nhất</option>
          <option value="rating">Đánh giá cao nhất</option>
          <option value="price_asc">Học phí: Thấp đến cao</option>
          <option value="price_desc">Học phí: Cao đến thấp</option>
          <option value="experience">Nhiều kinh nghiệm nhất</option>
        </select>
        <ChevronDown className="size-3.5 text-muted absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
      </div>
    </div>
  );
}

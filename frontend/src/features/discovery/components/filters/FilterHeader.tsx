"use client";

import React from "react";
import { Search, X } from "lucide-react";

export interface FilterHeaderProps {
  keywordInput: string;
  onKeywordChange: (val: string) => void;
  onClearKeyword: () => void;
  onSubmit: () => void;
}

/** Hàng nhập từ khóa: ô gõ lớn và nút tìm, nằm trên cùng của khung tìm kiếm. */
export function FilterHeader({ keywordInput, onKeywordChange, onClearKeyword, onSubmit }: FilterHeaderProps) {
  return (
    <form
      role="search"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
      className="flex items-center gap-3 px-4 py-3.5 sm:px-5"
    >
      <Search className="size-5 text-brand-strong shrink-0" aria-hidden="true" />
      <input
        type="text"
        value={keywordInput}
        onChange={(e) => onKeywordChange(e.target.value)}
        placeholder="Tìm theo tên, môn học hoặc khu vực"
        aria-label="Tìm gia sư"
        className="min-w-0 flex-1 bg-transparent text-sm text-ink placeholder:text-muted focus:outline-hidden"
      />
      {keywordInput && (
        <button
          type="button"
          onClick={onClearKeyword}
          aria-label="Xóa từ khóa"
          className="p-1 text-muted hover:text-ink-2 cursor-pointer"
        >
          <X className="size-4" />
        </button>
      )}
      <button
        type="submit"
        aria-label="Tìm kiếm"
        className="inline-flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand text-white hover:bg-brand-deep transition-colors cursor-pointer"
      >
        <Search className="size-4" />
      </button>
    </form>
  );
}

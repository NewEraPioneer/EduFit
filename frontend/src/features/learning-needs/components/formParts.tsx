import React from "react";

/** Nút chọn dạng chip dùng cho bộ chọn danh mục (Lớp, Môn, Mục tiêu...). */
export const chip = (active: boolean) =>
  `px-3 py-1.5 rounded-full text-xs font-medium border transition-colors ${
    active
      ? "bg-brand border-brand text-white shadow-2xs"
      : "bg-surface border-line text-ink-2 hover:bg-paper"
  }`;

export const inputClass =
  "mt-1.5 w-full px-3 py-2 bg-paper border border-line rounded-xl text-sm font-medium text-ink placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-brand-line";

export function FieldError({ message }: { message?: string }) {
  if (!message) return null;
  return <p role="alert" className="mt-1 text-xs font-medium text-danger-strong">{message}</p>;
}

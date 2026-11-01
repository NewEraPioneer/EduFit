import React from "react";

/** Nút chọn dạng chip dùng cho bộ chọn danh mục (Lớp, Môn, Mục tiêu...). */
export const chip = (active: boolean) =>
  `px-3 py-1.5 rounded-full text-xs font-semibold border transition-colors ${
    active ? "bg-[#006DCC] border-[#006DCC] text-white" : "bg-white border-stone-200 text-neutral-700 hover:bg-stone-50"
  }`;

export const inputClass =
  "mt-1.5 w-full px-3 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm font-medium focus:outline-none focus:ring-2 focus:ring-sky-200";

export function FieldError({ message }: { message?: string }) {
  if (!message) return null;
  return <p role="alert" className="mt-1 text-xs font-medium text-rose-600">{message}</p>;
}

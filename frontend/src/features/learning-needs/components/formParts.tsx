import React from "react";

/** Nút chọn dạng chip dùng cho bộ chọn danh mục (Lớp, Môn, Mục tiêu...). */
export const chip = (active: boolean) =>
  `px-3 py-1.5 rounded-full text-xs font-semibold border transition-colors ${
    active
      ? "bg-[#006DCC] dark:bg-sky-600 border-[#006DCC] dark:border-sky-500 text-white shadow-2xs"
      : "bg-white dark:bg-stone-800 border-stone-200 dark:border-stone-700 text-neutral-700 dark:text-stone-200 hover:bg-stone-50 dark:hover:bg-stone-700"
  }`;

export const inputClass =
  "mt-1.5 w-full px-3 py-2 bg-stone-50 dark:bg-stone-800/80 border border-stone-200 dark:border-stone-700 rounded-xl text-sm font-medium text-neutral-900 dark:text-stone-100 placeholder:text-neutral-400 dark:placeholder:text-stone-500 focus:outline-none focus:ring-2 focus:ring-sky-200 dark:focus:ring-sky-700";

export function FieldError({ message }: { message?: string }) {
  if (!message) return null;
  return <p role="alert" className="mt-1 text-xs font-medium text-rose-600 dark:text-rose-400">{message}</p>;
}

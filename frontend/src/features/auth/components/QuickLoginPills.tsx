"use client";

import React from "react";
import { Sparkles } from "lucide-react";

interface QuickLoginPillsProps {
  onSelect: (email: string, password: string) => void;
}

export function QuickLoginPills({ onSelect }: QuickLoginPillsProps) {
  // Tài khoản demo chỉ tồn tại khi backend chạy profile dev (db/seed) nên chỉ hiện khi bật cờ dev tương ứng
  if (process.env.NEXT_PUBLIC_ENABLE_DEMO_LOGIN !== "true") return null;

  const accounts = [
    {
      role: "Học sinh",
      email: "student.demo@edufit.vn",
      password: "Password123@",
      color: "bg-sky-50 text-sky-800 border-sky-200 hover:bg-sky-100",
    },
    {
      role: "Gia sư",
      email: "tutor.demo@edufit.vn",
      password: "Password123@",
      color: "bg-amber-50 text-amber-900 border-amber-200 hover:bg-amber-100",
    },
    {
      role: "Phụ huynh",
      email: "parent.demo@edufit.vn",
      password: "Password123@",
      color: "bg-emerald-50 text-emerald-800 border-emerald-200 hover:bg-emerald-100",
    },
    {
      role: "Admin",
      email: "admin.demo@edufit.vn",
      password: "Password123@",
      color: "bg-purple-50 text-purple-900 border-purple-200 hover:bg-purple-100",
    },
  ];

  return (
    <div className="p-3 bg-stone-50 border border-stone-200/80 rounded-2xl space-y-2">
      <div className="flex items-center gap-1.5 text-xs font-semibold text-neutral-600">
        <Sparkles className="size-3.5 text-amber-500" />
        <span>Tài khoản Demo (1-Click điền nhanh):</span>
      </div>
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
        {accounts.map((acc, idx) => (
          <button
            key={idx}
            type="button"
            onClick={() => onSelect(acc.email, acc.password)}
            className={`py-1.5 px-2 rounded-xl text-[11px] font-bold border transition-colors cursor-pointer text-center ${acc.color}`}
          >
            {acc.role}
          </button>
        ))}
      </div>
    </div>
  );
}

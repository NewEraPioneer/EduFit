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
      color: "bg-brand-soft text-brand-strong border-brand-line hover:bg-brand-soft",
    },
    {
      role: "Gia sư",
      email: "tutor.demo@edufit.vn",
      password: "Password123@",
      color: "bg-action-soft text-action-strong border-action-line hover:bg-action-soft",
    },
    {
      role: "Phụ huynh",
      email: "parent.demo@edufit.vn",
      password: "Password123@",
      color: "bg-success-soft text-success-strong border-success-line hover:bg-success-soft",
    },
    {
      role: "Admin",
      email: "admin.demo@edufit.vn",
      password: "Password123@",
      color: "bg-brand-soft text-brand-strong border-brand-line hover:bg-brand-soft",
    },
  ];

  return (
    <div className="p-3 bg-paper border border-line/80 rounded-2xl space-y-2">
      <div className="flex items-center gap-1.5 text-xs font-medium text-ink-2">
        <Sparkles className="size-3.5 text-action-strong" />
        <span>Tài khoản Demo (1-Click điền nhanh):</span>
      </div>
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
        {accounts.map((acc, idx) => (
          <button
            key={idx}
            type="button"
            onClick={() => onSelect(acc.email, acc.password)}
            className={`py-1.5 px-2 rounded-xl text-xs font-medium border transition-colors cursor-pointer text-center ${acc.color}`}
          >
            {acc.role}
          </button>
        ))}
      </div>
    </div>
  );
}

"use client";

import React from "react";
import { Sun, Moon, Laptop, Check, Palette } from "lucide-react";
import { useTheme, Theme } from "@/context/ThemeContext";

export function AppearanceSettingsTab() {
  const { theme, setTheme } = useTheme();

  const themes: Array<{
    id: Theme;
    title: string;
    desc: string;
    icon: typeof Sun;
    preview: {
      bg: string;
      card: string;
      sidebar: string;
      accent: string;
      border: string;
    };
  }> = [
    {
      id: "light",
      title: "Giao diện Sáng (Light)",
      desc: "Nền trắng kem ấm áp, độ tương phản cao, dễ đọc khi học ban ngày.",
      icon: Sun,
      preview: {
        bg: "bg-sunken",
        card: "bg-surface",
        sidebar: "bg-surface",
        accent: "bg-action",
        border: "border-line",
      },
    },
    {
      id: "dark",
      title: "Giao diện Tối (Dark)",
      desc: "Tone đá đen ấm (Warm stone) êm mắt, giảm mỏi mắt khi học vào ban đêm.",
      icon: Moon,
      preview: {
        bg: "bg-night",
        card: "bg-night",
        sidebar: "bg-night",
        accent: "bg-action",
        border: "border-line-strong",
      },
    },
    {
      id: "system",
      title: "Theo Hệ Thống (System)",
      desc: "Tự động điều chỉnh theo chế độ Sáng/Tối của hệ điều hành máy tính.",
      icon: Laptop,
      preview: {
        bg: "bg-sunken",
        card: "bg-surface",
        sidebar: "bg-paper",
        accent: "bg-action",
        border: "border-line-strong",
      },
    },
  ];

  return (
    <div className="space-y-6">
      <div className="pb-4 border-b border-line">
        <h2 className="font-heading text-xl font-bold text-ink">
          Giao diện và chủ đề
        </h2>
        <p className="text-xs text-muted mt-1">
          Tùy chỉnh diện mạo của EduFit để phù hợp với sở thích và môi trường học tập của bạn.
        </p>
      </div>

      <div className="space-y-4">
        <div className="flex items-center gap-2 text-xs font-medium text-ink">
          <Palette className="size-4 text-action-strong" />
          <span>Lựa chọn chủ đề hiển thị</span>
        </div>

        {/* Theme Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {themes.map((t) => {
            const isSelected = theme === t.id;
            const Icon = t.icon;

            return (
              <button
                key={t.id}
                type="button"
                onClick={() => setTheme(t.id)}
                className={`flex flex-col text-left p-4 rounded-3xl border transition-all cursor-pointer relative group ${
                  isSelected
                    ? "border-action ring-2 ring-action/20 bg-action-soft/20 shadow-xs"
                    : "border-line/90 bg-surface hover:border-line-strong hover:shadow-xs"
                }`}
              >
                {/* Visual Mini Mockup */}
                <div
                  className={`w-full h-28 rounded-2xl p-2.5 mb-3.5 border ${t.preview.border} ${t.preview.bg} flex gap-2 overflow-hidden shadow-inner`}
                >
                  {/* Mock Sidebar */}
                  <div className={`w-8 h-full rounded-lg ${t.preview.sidebar} p-1 flex flex-col gap-1 border border-line/20`}>
                    <div className="size-2 rounded-full bg-action" />
                    <div className="w-full h-1 rounded-sm bg-line-strong/40" />
                    <div className="w-3/4 h-1 rounded-sm bg-line-strong/30" />
                  </div>
                  {/* Mock Content */}
                  <div className="flex-1 flex flex-col gap-1.5">
                    <div className="w-1/2 h-2 rounded-sm bg-line-strong/50" />
                    <div className={`flex-1 rounded-lg ${t.preview.card} p-1.5 flex flex-col justify-between shadow-2xs`}>
                      <div className="space-y-1">
                        <div className="w-3/4 h-1.5 rounded-sm bg-line-strong/40" />
                        <div className="w-1/2 h-1 rounded-sm bg-line-strong/30" />
                      </div>
                      <div className={`w-6 h-2 rounded-md ${t.preview.accent} self-end`} />
                    </div>
                  </div>
                </div>

                {/* Card Title & Description */}
                <div className="flex items-center justify-between w-full">
                  <div className="flex items-center gap-1.5 font-medium text-xs text-ink">
                    <Icon className="size-3.5 text-muted" />
                    <span>{t.title}</span>
                  </div>
                  {isSelected && (
                    <span className="size-5 rounded-full bg-action text-on-action flex items-center justify-center shrink-0 shadow-xs">
                      <Check className="size-3 stroke-[3]" />
                    </span>
                  )}
                </div>

                <p className="text-xs text-muted mt-1 leading-relaxed">
                  {t.desc}
                </p>
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}

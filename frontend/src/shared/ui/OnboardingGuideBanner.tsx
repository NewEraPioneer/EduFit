"use client";

import React, { useState } from "react";
import Link from "next/link";
import { Sparkles, ArrowRight, CheckCircle2, ChevronRight, X } from "lucide-react";

export interface OnboardingGuideStep {
  title: string;
  description: string;
  href: string;
  actionText: string;
  completed?: boolean;
}

interface OnboardingGuideBannerProps {
  role: "STUDENT" | "TUTOR" | "PARENT" | "ADMIN";
  steps: OnboardingGuideStep[];
  userName: string;
  userId?: string;
  autoHideOnComplete?: boolean;
}

export function OnboardingGuideBanner({
  role,
  steps,
  userName,
  userId,
  autoHideOnComplete = true,
}: OnboardingGuideBannerProps) {
  const storageKey = userId
    ? `edufit_onboarding_guide_dismissed_${userId}`
    : `edufit_onboarding_guide_dismissed_${role.toLowerCase()}`;

  const [closed, setClosed] = useState(() => {
    if (typeof window !== "undefined") {
      return localStorage.getItem(storageKey) === "true";
    }
    return false;
  });

  React.useEffect(() => {
    if (typeof window !== "undefined") {
      setClosed(localStorage.getItem(storageKey) === "true");
    }
  }, [storageKey]);

  const allCompleted = steps.length > 0 && steps.every((s) => s.completed);

  if (closed || (autoHideOnComplete && allCompleted)) return null;

  const handleClose = () => {
    setClosed(true);
    if (typeof window !== "undefined") {
      localStorage.setItem(storageKey, "true");
    }
  };

  const roleMeta = {
    STUDENT: {
      badge: "Học Sinh Mới",
      title: `Chào ${userName}, sẵn sàng nâng cao thành tích học tập?`,
      subtitle: "Chỉ mất 2 phút để hoàn tất 3 bước thiết lập ban đầu và nhận gia sư AI Match tối ưu:",
      borderColor: "border-amber-200/90 dark:border-amber-800/60",
      bgGradient: "from-amber-500/10 via-amber-100/40 dark:via-amber-950/20 to-white dark:to-stone-900",
      badgeColor: "bg-amber-100 dark:bg-amber-950/80 text-amber-900 dark:text-amber-300 border-amber-200 dark:border-amber-800/60",
      accentBtn: "bg-amber-500 hover:bg-amber-600 text-neutral-950",
    },
    TUTOR: {
      badge: "Gia Sư Mới",
      title: `Chào Thầy/Cô ${userName}, khởi đầu giảng dạy cùng EduFit!`,
      subtitle: "Hoàn thiện các bước dưới đây để hồ sơ của bạn được hiển thị ưu tiên tới hàng ngàn học sinh:",
      borderColor: "border-sky-200/90 dark:border-sky-800/60",
      bgGradient: "from-sky-500/10 via-sky-100/40 dark:via-sky-950/20 to-white dark:to-stone-900",
      badgeColor: "bg-sky-100 dark:bg-sky-950/80 text-sky-900 dark:text-sky-300 border-sky-200 dark:border-sky-800/60",
      accentBtn: "bg-sky-600 hover:bg-sky-700 text-white",
    },
    PARENT: {
      badge: "Phụ Huynh Mới",
      title: `Chào ${userName}, đồng hành cùng việc học của con!`,
      subtitle: "Liên kết tài khoản học sinh để theo dõi lịch học, kết quả và thanh toán học phí minh bạch:",
      borderColor: "border-emerald-200/90 dark:border-emerald-800/60",
      bgGradient: "from-emerald-500/10 via-emerald-100/40 dark:via-emerald-950/20 to-white dark:to-stone-900",
      badgeColor: "bg-emerald-100 dark:bg-emerald-950/80 text-emerald-900 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800/60",
      accentBtn: "bg-emerald-600 hover:bg-emerald-700 text-white",
    },
    ADMIN: {
      badge: "Quản Trị Viên",
      title: `Bảng điều hành hệ thống EduFit`,
      subtitle: "Quản trị danh mục, người dùng và theo dõi quy trình kiểm duyệt hồ sơ KYC:",
      borderColor: "border-purple-200/90 dark:border-purple-800/60",
      bgGradient: "from-purple-500/10 via-purple-100/40 dark:via-purple-950/20 to-white dark:to-stone-900",
      badgeColor: "bg-purple-100 dark:bg-purple-950/80 text-purple-900 dark:text-purple-300 border-purple-200 dark:border-purple-800/60",
      accentBtn: "bg-purple-600 hover:bg-purple-700 text-white",
    },
  }[role];

  return (
    <div
      className={`relative overflow-hidden rounded-3xl border ${roleMeta.borderColor} bg-gradient-to-br ${roleMeta.bgGradient} p-5 sm:p-6 shadow-xs animate-in fade-in`}
    >
      <button
        type="button"
        onClick={handleClose}
        className="absolute top-4 right-4 p-1.5 rounded-full text-neutral-400 hover:text-neutral-700 hover:bg-white/60 transition-colors cursor-pointer"
        title="Đóng hướng dẫn"
      >
        <X className="size-4" />
      </button>

      <div className="space-y-4 max-w-4xl">
        <div className="flex flex-wrap items-center gap-2">
          <span
            className={`px-3 py-1 rounded-full text-[11px] font-bold border flex items-center gap-1.5 ${roleMeta.badgeColor}`}
          >
            <Sparkles className="size-3" />
            <span>{roleMeta.badge}</span>
          </span>
          <span className="text-xs text-neutral-400 font-medium">Lộ trình khởi tạo nhanh</span>
        </div>

        <div>
          <h3 className="font-heading text-lg sm:text-xl font-bold text-neutral-900 dark:text-stone-100">
            {roleMeta.title}
          </h3>
          <p className="text-xs text-neutral-600 dark:text-stone-300 mt-1">{roleMeta.subtitle}</p>
        </div>

        {/* 3 Step Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-1">
          {steps.map((st, idx) => (
            <div
              key={idx}
              className={`p-3.5 rounded-2xl border transition-all flex flex-col justify-between space-y-3 bg-white/80 dark:bg-stone-850/90 backdrop-blur-xs ${
                st.completed ? "border-emerald-200 dark:border-emerald-800 bg-emerald-50/40 dark:bg-emerald-950/40" : "border-stone-200/80 dark:border-stone-800 hover:border-amber-300 dark:hover:border-amber-600"
              }`}
            >
              <div className="space-y-1">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] font-bold text-neutral-400 dark:text-stone-400 tracking-wider">
                    BƯỚC {idx + 1}
                  </span>
                  {st.completed ? (
                    <span className="text-[10px] text-emerald-600 dark:text-emerald-400 font-bold flex items-center gap-1">
                      <CheckCircle2 className="size-3" /> Đã xong
                    </span>
                  ) : null}
                </div>
                <h4 className="text-xs font-bold text-neutral-900 dark:text-stone-100">{st.title}</h4>
                <p className="text-[11px] text-neutral-500 dark:text-stone-300 leading-snug">{st.description}</p>
              </div>

              <Link
                href={st.href}
                className={`w-full py-2 px-3 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5 cursor-pointer ${
                  st.completed
                    ? "bg-emerald-100 hover:bg-emerald-200 text-emerald-900"
                    : `${roleMeta.accentBtn} shadow-xs`
                }`}
              >
                <span>{st.actionText}</span>
                <ChevronRight className="size-3" />
              </Link>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

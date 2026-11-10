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

  // null = chưa đọc localStorage (tránh lệch hydration và nháy banner đã đóng)
  const [closed, setClosed] = useState<boolean | null>(null);

  React.useEffect(() => {
    let dismissed = false;
    try {
      dismissed = localStorage.getItem(storageKey) === "true";
    } catch {
      // localStorage không khả dụng: coi như chưa đóng
    }
    setClosed(dismissed);
  }, [storageKey]);

  const allCompleted = steps.length > 0 && steps.every((s) => s.completed);

  if (closed === null || closed || (autoHideOnComplete && allCompleted)) return null;

  const handleClose = () => {
    setClosed(true);
    try {
      localStorage.setItem(storageKey, "true");
    } catch {
      // bỏ qua
    }
  };

  const roleMeta = {
    STUDENT: {
      badge: "Học Sinh Mới",
      title: `Chào ${userName}, sẵn sàng nâng cao thành tích học tập?`,
      subtitle: "Chỉ mất 2 phút để hoàn tất 3 bước thiết lập ban đầu và nhận gợi ý gia sư AI Match phù hợp:",
      borderColor: "border-action-line/90",
      bgGradient: "from-action/10 via-action-soft/40 to-surface",
      badgeColor: "bg-action-soft text-action-strong border-action-line",
      accentBtn: "bg-action hover:bg-action-deep text-on-action",
    },
    TUTOR: {
      badge: "Gia Sư Mới",
      title: `Chào Thầy/Cô ${userName}, khởi đầu giảng dạy cùng EduFit!`,
      subtitle: "Hoàn thiện các bước dưới đây để hồ sơ của bạn được hiển thị ưu tiên tới hàng ngàn học sinh:",
      borderColor: "border-brand-line/90",
      bgGradient: "from-brand/10 via-brand-soft/40 to-surface",
      badgeColor: "bg-brand-soft text-brand-strong border-brand-line",
      accentBtn: "bg-brand hover:bg-brand-deep text-white",
    },
    PARENT: {
      badge: "Phụ Huynh Mới",
      title: `Chào ${userName}, theo dõi việc học của con.`,
      subtitle: "Liên kết tài khoản học sinh để theo dõi lịch học, kết quả và thanh toán học phí minh bạch:",
      borderColor: "border-success-line/90",
      bgGradient: "from-success/10 via-success-soft/40 to-surface",
      badgeColor: "bg-success-soft text-success-strong border-success-line",
      accentBtn: "bg-success hover:bg-success-deep text-white",
    },
    ADMIN: {
      badge: "Quản Trị Viên",
      title: `Bảng điều hành hệ thống EduFit`,
      subtitle: "Quản trị danh mục, người dùng và theo dõi quy trình kiểm duyệt hồ sơ KYC:",
      borderColor: "border-brand-line/90",
      bgGradient: "from-brand/10 via-brand-soft/40 to-surface",
      badgeColor: "bg-brand-soft text-brand-strong border-brand-line",
      accentBtn: "bg-brand hover:bg-brand-deep text-white",
    },
  }[role];

  return (
    <div
      className={`relative overflow-hidden rounded-3xl border ${roleMeta.borderColor} bg-gradient-to-br ${roleMeta.bgGradient} p-5 sm:p-6 shadow-xs animate-in fade-in`}
    >
      <button
        type="button"
        onClick={handleClose}
        className="absolute top-4 right-4 p-1.5 rounded-full text-muted hover:text-ink-2 hover:bg-surface/60 transition-colors cursor-pointer"
        title="Đóng hướng dẫn"
      >
        <X className="size-4" />
      </button>

      <div className="space-y-4 max-w-4xl">
        <div className="flex flex-wrap items-center gap-2">
          <span
            className={`px-3 py-1 rounded-full text-xs font-medium border flex items-center gap-1.5 ${roleMeta.badgeColor}`}
          >
            <Sparkles className="size-3" />
            <span>{roleMeta.badge}</span>
          </span>
          <span className="text-xs text-muted font-medium">Lộ trình khởi tạo nhanh</span>
        </div>

        <div>
          <h3 className="font-heading text-lg sm:text-xl font-bold text-ink">
            {roleMeta.title}
          </h3>
          <p className="text-xs text-ink-2 mt-1">{roleMeta.subtitle}</p>
        </div>

        {/* 3 Step Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-1">
          {steps.map((st, idx) => (
            <div
              key={idx}
              className={`p-3.5 rounded-2xl border transition-all flex flex-col justify-between space-y-3 bg-surface/80 backdrop-blur-xs ${
                st.completed ? "border-success-line bg-success-soft/40" : "border-line/80 hover:border-action-line"
              }`}
            >
              <div className="space-y-1">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium text-muted tracking-wider">
                    BƯỚC {idx + 1}
                  </span>
                  {st.completed ? (
                    <span className="text-xs text-success-strong font-medium flex items-center gap-1">
                      <CheckCircle2 className="size-3" /> Đã xong
                    </span>
                  ) : null}
                </div>
                <h4 className="text-xs font-medium text-ink">{st.title}</h4>
                <p className="text-xs text-muted leading-snug">{st.description}</p>
              </div>

              <Link
                href={st.href}
                className={`w-full py-2 px-3 rounded-xl text-xs font-medium transition-all flex items-center justify-center gap-1.5 cursor-pointer ${
                  st.completed
                    ? "bg-success-soft hover:bg-success-line text-success-strong"
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

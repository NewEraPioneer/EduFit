"use client";

import React from "react";
import Link from "next/link";
import {
  CreditCard,
  DollarSign,
  Calendar,
  ArrowLeft,
  ShieldCheck,
  FileSpreadsheet,
  Clock,
  Sparkles,
} from "lucide-react";

export type FinanceRole = "STUDENT" | "PARENT" | "TUTOR";

export interface FinanceComingSoonNoticeProps {
  role: FinanceRole;
  title: string;
  subtitle: string;
  description: string;
  badge?: string;
}

export function FinanceComingSoonNotice({
  role,
  title,
  subtitle,
  description,
  badge = "Sắp ra mắt",
}: FinanceComingSoonNoticeProps) {
  const roleLower = role.toLowerCase();
  const dashboardHref = `/${roleLower}/dashboard`;
  const scheduleHref = `/${roleLower}/schedule`;

  const MainIcon = role === "TUTOR" ? DollarSign : CreditCard;

  const roleLabelMap: Record<FinanceRole, string> = {
    STUDENT: "CỔNG HỌC SINH",
    PARENT: "CỔNG PHỤ HUYNH",
    TUTOR: "CỔNG GIA SƯ",
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6 sm:space-y-8 animate-in fade-in duration-200 py-2 sm:py-4">
      {/* 1. Header & Navigation Back */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-stone-200/80 dark:border-stone-800">
        <div>
          <div className="flex items-center gap-2 mb-1.5">
            <span className="px-2 py-0.5 text-[10px] font-extrabold uppercase tracking-wider rounded-md bg-stone-100 dark:bg-stone-800 text-stone-600 dark:text-stone-300">
              {roleLabelMap[role]}
            </span>
            <span className="px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider rounded-md bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-300/40 dark:border-amber-700/40">
              {badge}
            </span>
          </div>
          <h1 className="font-heading text-2xl sm:text-3xl font-extrabold text-neutral-900 dark:text-stone-100 tracking-tight">
            {title}
          </h1>
          <p className="text-xs sm:text-sm text-neutral-500 dark:text-stone-400 mt-1 font-medium">
            {subtitle}
          </p>
        </div>

        <div className="flex items-center gap-2.5 shrink-0">
          <Link
            href={dashboardHref}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-white dark:bg-stone-900 hover:bg-stone-50 dark:hover:bg-stone-800 border border-stone-200 dark:border-stone-700 text-neutral-700 dark:text-stone-200 rounded-xl text-xs font-bold shadow-2xs transition-colors"
          >
            <ArrowLeft className="size-3.5" />
            <span>Quay lại Dashboard</span>
          </Link>
          <Link
            href={scheduleHref}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-amber-500 hover:bg-amber-600 text-neutral-950 rounded-xl text-xs font-bold shadow-xs transition-colors"
          >
            <Calendar className="size-3.5" />
            <span>{role === "TUTOR" ? "Xem lịch dạy" : "Xem lịch học"}</span>
          </Link>
        </div>
      </div>

      {/* 2. Main Hero Notice Card */}
      <div className="bg-white dark:bg-stone-900 rounded-3xl border border-stone-200/80 dark:border-stone-800 p-6 sm:p-8 shadow-xs relative overflow-hidden">
        <div className="flex flex-col sm:flex-row items-start gap-5 sm:gap-6">
          <div className="size-14 sm:size-16 rounded-2xl bg-amber-500/10 dark:bg-amber-500/20 text-amber-600 dark:text-amber-400 border border-amber-200/50 dark:border-amber-700/40 flex items-center justify-center shrink-0">
            <MainIcon className="size-7 sm:size-8" />
          </div>

          <div className="space-y-3 flex-1">
            <div className="flex items-center gap-2">
              <h2 className="text-lg sm:text-xl font-bold text-neutral-900 dark:text-stone-100">
                Tính năng tài chính đang được hoàn thiện
              </h2>
            </div>
            <p className="text-xs sm:text-sm text-neutral-600 dark:text-stone-300 leading-relaxed font-sans">
              {description}
            </p>

            {/* GitHub-style Important Note Box */}
            <div className="rounded-2xl bg-amber-50/70 dark:bg-amber-950/30 border border-amber-200/80 dark:border-amber-800/60 p-4 text-xs text-amber-950 dark:text-amber-200 space-y-1.5">
              <div className="flex items-center gap-2 font-bold text-amber-900 dark:text-amber-300">
                <ShieldCheck className="size-4 shrink-0 text-amber-600 dark:text-amber-400" />
                <span>Chính sách thanh toán học phí hiện hành</span>
              </div>
              <p className="leading-relaxed font-normal text-amber-900/90 dark:text-amber-200/90">
                Nền tảng EduFit hiện <strong>không thu phí trung gian, không giữ tiền ký quỹ và không tích hợp ví điện tử</strong>. Học phí được thỏa thuận trực tiếp và thanh toán bằng chuyển khoản ngân hàng hoặc tiền mặt giữa Gia sư và Học sinh/Phụ huynh theo từng buổi hoặc từng tháng.
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* 3. Upcoming Capabilities (Research & Future Roadmap) */}
      <div className="space-y-4">
        <h3 className="text-xs font-bold text-neutral-400 dark:text-stone-400 uppercase tracking-wider">
          Đề án quản lý học phí & đối soát đang nghiên cứu
        </h3>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="p-5 rounded-2xl bg-white dark:bg-stone-900 border border-stone-200/80 dark:border-stone-800 space-y-2">
            <div className="size-9 rounded-xl bg-sky-50 dark:bg-sky-950/50 text-sky-600 dark:text-sky-400 flex items-center justify-center">
              <Sparkles className="size-4.5" />
            </div>
            <h4 className="text-sm font-bold text-neutral-900 dark:text-stone-100">
              Thỏa thuận học phí rõ ràng
            </h4>
            <p className="text-xs text-neutral-500 dark:text-stone-400 leading-relaxed font-sans">
              Đơn giá theo buổi và hình thức thanh toán được hai bên chốt ngay khi gia sư chấp thuận yêu cầu kết nối.
            </p>
          </div>

          <div className="p-5 rounded-2xl bg-white dark:bg-stone-900 border border-stone-200/80 dark:border-stone-800 space-y-2">
            <div className="size-9 rounded-xl bg-emerald-50 dark:bg-emerald-950/50 text-emerald-600 dark:text-emerald-400 flex items-center justify-center">
              <FileSpreadsheet className="size-4.5" />
            </div>
            <h4 className="text-sm font-bold text-neutral-900 dark:text-stone-100">
              Sổ nhật ký & Bảng kê đối soát
            </h4>
            <p className="text-xs text-neutral-500 dark:text-stone-400 leading-relaxed font-sans">
              Tự động tổng hợp số buổi thực dạy, trạng thái điểm danh và xuất bảng kê học phí định kỳ để đối chiếu thanh toán.
            </p>
          </div>

          <div className="p-5 rounded-2xl bg-white dark:bg-stone-900 border border-stone-200/80 dark:border-stone-800 space-y-2">
            <div className="size-9 rounded-xl bg-amber-50 dark:bg-amber-950/50 text-amber-600 dark:text-amber-400 flex items-center justify-center">
              <Clock className="size-4.5" />
            </div>
            <h4 className="text-sm font-bold text-neutral-900 dark:text-stone-100">
              Cửa sổ bảo vệ quyền lợi 24h
            </h4>
            <p className="text-xs text-neutral-500 dark:text-stone-400 leading-relaxed font-sans">
              Cho phép học sinh/phụ huynh xác nhận buổi dạy hoặc phản hồi khiếu nại trong vòng 24 giờ sau khi kết thúc buổi học.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

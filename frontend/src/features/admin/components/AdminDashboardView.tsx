"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { BookOpen, ScrollText, ShieldCheck, HeartHandshake, RefreshCw } from "lucide-react";
import { AdminKpiGrid } from "./AdminKpiGrid";
import { AdminKycQueueCard } from "@/features/dashboard/components/admin/AdminKycQueueCard";
import { AdminSystemHealthCard } from "@/features/dashboard/components/admin/AdminSystemHealthCard";
import { adminApi } from "../api/adminApi";
import type { AdminDashboardStats } from "../types";

interface AdminDashboardViewProps {
  user: {
    fullName: string;
    email: string;
    role: string;
  };
}

export function AdminDashboardView({ user }: AdminDashboardViewProps) {
  const [stats, setStats] = useState<AdminDashboardStats | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchStats = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await adminApi.getDashboardStats();
      setStats(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Không thể tải dữ liệu điều hành.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchStats();
  }, []);

  const totalUsers = stats?.totalUsers ?? 0;
  const activeClasses = stats?.activeClasses ?? 0;
  const pendingCount = stats?.pendingVerifications ?? stats?.pendingKyc ?? 0;

  return (
    <div className="space-y-6 sm:space-y-8 animate-in fade-in duration-200">
      {/* 1. Header Greeting & Quick Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="font-heading text-2xl sm:text-3xl font-extrabold text-neutral-900 dark:text-stone-100 tracking-tight">
            Bảng điều hành Tổng thể Hệ thống EduFit 📊
          </h2>
          <p className="text-xs sm:text-sm text-neutral-500 dark:text-stone-400 mt-1 font-medium">
            {isLoading ? (
              <span className="inline-block h-4 w-72 bg-stone-200 dark:bg-stone-800 rounded animate-pulse" />
            ) : (
              `Toàn hệ thống hiện có ${totalUsers.toLocaleString()} tài khoản, ${activeClasses.toLocaleString()} lớp học đang diễn ra và ${pendingCount} hồ sơ gia sư chờ duyệt.`
            )}
          </p>
        </div>

        <div className="flex items-center gap-2.5 flex-wrap">
          <button
            type="button"
            onClick={fetchStats}
            disabled={isLoading}
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-white dark:bg-stone-900 hover:bg-stone-50 dark:hover:bg-stone-800 border border-stone-200 dark:border-stone-800 text-neutral-700 dark:text-stone-300 rounded-xl text-xs font-semibold shadow-2xs transition-colors shrink-0 disabled:opacity-50 cursor-pointer"
          >
            <RefreshCw className={`size-3.5 ${isLoading ? "animate-spin" : ""}`} />
            <span>Làm mới số liệu</span>
          </button>
          <Link
            href="/admin/catalogs"
            className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-purple-700 hover:bg-purple-800 text-white rounded-xl text-xs font-bold shadow-xs transition-colors shrink-0"
          >
            <BookOpen className="size-3.5" />
            <span>Quản lý môn học</span>
          </Link>
          <Link
            href="/admin/audit-logs"
            className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-stone-900 hover:bg-stone-800 dark:bg-stone-800 dark:hover:bg-stone-700 text-white rounded-xl text-xs font-bold shadow-xs transition-colors shrink-0"
          >
            <ScrollText className="size-3.5" />
            <span>Nhật ký kiểm toán</span>
          </Link>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-2xl bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-800/50 text-amber-800 dark:text-amber-300 text-xs">
          {error}
        </div>
      )}

      {/* 2. Admin Real Live KPI Row */}
      <AdminKpiGrid stats={stats} isLoading={isLoading} />

      {/* 3. 2-Column Responsive Dashboard Content */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 sm:gap-8">
        {/* Left Column: Quick Navigation & Governance Summary */}
        <div className="lg:col-span-8 space-y-6">
          <div className="bg-white dark:bg-stone-900 p-6 rounded-3xl border border-stone-200/80 dark:border-stone-800 shadow-xs">
            <h3 className="font-heading text-base sm:text-lg font-bold text-neutral-900 dark:text-stone-100 mb-2">
              Trung tâm Điều phối Quản trị Hệ thống
            </h3>
            <p className="text-xs text-neutral-500 dark:text-stone-400 leading-relaxed mb-5">
              Truy cập nhanh vào các phân hệ nghiệp vụ trọng yếu của EduFit dành riêng cho Quản trị viên.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <Link
                href="/admin/catalogs"
                className="p-4 rounded-2xl bg-stone-50 dark:bg-stone-800/50 border border-stone-200/60 dark:border-stone-700/60 hover:border-purple-300 dark:hover:border-purple-600 transition-all group block"
              >
                <div className="size-8 rounded-xl bg-purple-100 dark:bg-purple-950/60 flex items-center justify-center text-purple-700 dark:text-purple-400 mb-3 group-hover:scale-105 transition-transform">
                  <BookOpen className="size-4" />
                </div>
                <h4 className="text-xs font-bold text-neutral-900 dark:text-stone-100 group-hover:text-purple-700 dark:group-hover:text-purple-400">
                  Danh mục môn học
                </h4>
                <p className="text-[11px] text-neutral-500 dark:text-stone-400 mt-1">
                  Thêm mới & chuyển đổi hiển thị môn học với chính sách toàn vẹn khóa ngoại.
                </p>
              </Link>

              <Link
                href="/admin/audit-logs"
                className="p-4 rounded-2xl bg-stone-50 dark:bg-stone-800/50 border border-stone-200/60 dark:border-stone-700/60 hover:border-indigo-300 dark:hover:border-indigo-600 transition-all group block"
              >
                <div className="size-8 rounded-xl bg-indigo-100 dark:bg-indigo-950/60 flex items-center justify-center text-indigo-700 dark:text-indigo-400 mb-3 group-hover:scale-105 transition-transform">
                  <ScrollText className="size-4" />
                </div>
                <h4 className="text-xs font-bold text-neutral-900 dark:text-stone-100 group-hover:text-indigo-700 dark:group-hover:text-indigo-400">
                  Nhật ký kiểm toán
                </h4>
                <p className="text-[11px] text-neutral-500 dark:text-stone-400 mt-1">
                  Truy vết 100% thay đổi trạng thái, thu hồi bằng cấp và sự kiện quan trọng.
                </p>
              </Link>

              <Link
                href="/admin/links"
                className="p-4 rounded-2xl bg-stone-50 dark:bg-stone-800/50 border border-stone-200/60 dark:border-stone-700/60 hover:border-emerald-300 dark:hover:border-emerald-600 transition-all group block"
              >
                <div className="size-8 rounded-xl bg-emerald-100 dark:bg-emerald-950/60 flex items-center justify-center text-emerald-700 dark:text-emerald-400 mb-3 group-hover:scale-105 transition-transform">
                  <HeartHandshake className="size-4" />
                </div>
                <h4 className="text-xs font-bold text-neutral-900 dark:text-stone-100 group-hover:text-emerald-700 dark:group-hover:text-emerald-400">
                  Liên kết gia đình (UC3.3)
                </h4>
                <p className="text-[11px] text-neutral-500 dark:text-stone-400 mt-1">
                  Giám sát và thu hồi liên kết bảo vệ quyền riêng tư học sinh.
                </p>
              </Link>
            </div>
          </div>

          {/* Verification Queue Highlight */}
          <div className="bg-purple-50/60 dark:bg-purple-950/20 p-5 rounded-3xl border border-purple-200/70 dark:border-purple-800/40 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="flex items-start gap-3">
              <div className="size-9 rounded-2xl bg-purple-700 text-white flex items-center justify-center shrink-0">
                <ShieldCheck className="size-5" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-purple-950 dark:text-purple-200">
                  Quy trình Thẩm định & Thu hồi Bằng cấp KYC
                </h4>
                <p className="text-xs text-purple-900/80 dark:text-purple-300/80 mt-0.5">
                  Đã hỗ trợ hàng đợi 3 Tabs (Chờ duyệt, Đã duyệt, Đã thu hồi) và Modal Thu hồi bằng cấp với lý do kiểm toán.
                </p>
              </div>
            </div>
            <Link
              href="/admin/verification-queue"
              className="inline-flex items-center justify-center px-4 py-2 bg-purple-700 hover:bg-purple-800 text-white rounded-xl text-xs font-bold shrink-0 transition-colors"
            >
              Xem hàng đợi
            </Link>
          </div>
        </div>

        {/* Right Column: KYC Queue Card & System Health Card */}
        <div className="lg:col-span-4 space-y-6">
          <AdminKycQueueCard pendingCount={pendingCount} />
          <AdminSystemHealthCard />
        </div>
      </div>
    </div>
  );
}

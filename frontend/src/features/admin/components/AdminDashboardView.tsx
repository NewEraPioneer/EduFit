"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { BookOpen, ShieldCheck, HeartHandshake, RefreshCw } from "lucide-react";
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
          <h2 className="font-heading text-2xl sm:text-3xl font-bold text-ink tracking-tight">
            Bảng điều hành hệ thống EduFit
          </h2>
          <p className="text-xs sm:text-sm text-muted mt-1 font-medium">
            {isLoading ? (
              <span className="inline-block h-4 w-72 bg-line rounded animate-pulse" />
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
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-surface hover:bg-paper border border-line text-ink-2 rounded-xl text-xs font-medium shadow-2xs transition-colors shrink-0 disabled:opacity-50 cursor-pointer"
          >
            <RefreshCw className={`size-3.5 ${isLoading ? "animate-spin" : ""}`} />
            <span>Làm mới số liệu</span>
          </button>
          <Link
            href="/admin/catalogs"
            className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-brand hover:bg-brand-deep text-white rounded-xl text-xs font-medium shadow-xs transition-colors shrink-0"
          >
            <BookOpen className="size-3.5" />
            <span>Quản lý môn học</span>
          </Link>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-2xl bg-action-soft border border-action-line text-action-strong text-xs">
          {error}
        </div>
      )}

      {/* 2. Admin Real Live KPI Row */}
      <AdminKpiGrid stats={stats} isLoading={isLoading} />

      {/* 3. 2-Column Responsive Dashboard Content */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 sm:gap-8">
        {/* Left Column: Quick Navigation & Governance Summary */}
        <div className="lg:col-span-8 space-y-6">
          <div className="bg-surface p-6 rounded-3xl border border-line/80 shadow-xs">
            <h3 className="font-heading text-base sm:text-lg font-bold text-ink mb-2">
              Trung tâm Điều phối Quản trị Hệ thống
            </h3>
            <p className="text-xs text-muted leading-relaxed mb-5">
              Truy cập nhanh vào các phân hệ nghiệp vụ trọng yếu của EduFit dành riêng cho Quản trị viên.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <Link
                href="/admin/catalogs"
                className="p-4 rounded-2xl bg-paper border border-line/60 hover:border-brand-line transition-all group block"
              >
                <div className="size-8 rounded-xl bg-brand-soft flex items-center justify-center text-brand-strong mb-3 transition-transform">
                  <BookOpen className="size-4" />
                </div>
                <h4 className="text-xs font-medium text-ink group-hover:text-brand-strong">
                  Danh mục môn học
                </h4>
                <p className="text-xs text-muted mt-1">
                  Thêm mới & chuyển đổi hiển thị môn học với chính sách toàn vẹn khóa ngoại.
                </p>
              </Link>

              <Link
                href="/admin/links"
                className="p-4 rounded-2xl bg-paper border border-line/60 hover:border-success-line transition-all group block"
              >
                <div className="size-8 rounded-xl bg-success-soft flex items-center justify-center text-success-strong mb-3 transition-transform">
                  <HeartHandshake className="size-4" />
                </div>
                <h4 className="text-xs font-medium text-ink group-hover:text-success-strong">
                  Liên kết gia đình (UC3.3)
                </h4>
                <p className="text-xs text-muted mt-1">
                  Giám sát và thu hồi liên kết bảo vệ quyền riêng tư học sinh.
                </p>
              </Link>
            </div>
          </div>

          {/* Verification Queue Highlight */}
          <div className="bg-brand-soft/60 p-5 rounded-3xl border border-brand-line/70 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="flex items-start gap-3">
              <div className="size-9 rounded-2xl bg-brand text-white flex items-center justify-center shrink-0">
                <ShieldCheck className="size-5" />
              </div>
              <div>
                <h4 className="text-sm font-medium text-brand-strong">
                  Quy trình thẩm định và thu hồi bằng cấp KYC
                </h4>
                <p className="text-xs text-brand-strong/80 mt-0.5">
                  Đã hỗ trợ hàng đợi 3 Tabs (Chờ duyệt, Đã duyệt, Đã thu hồi) và Modal Thu hồi bằng cấp với lý do kiểm toán.
                </p>
              </div>
            </div>
            <Link
              href="/admin/verification-queue"
              className="inline-flex items-center justify-center px-4 py-2 bg-brand hover:bg-brand-deep text-white rounded-xl text-xs font-medium shrink-0 transition-colors"
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

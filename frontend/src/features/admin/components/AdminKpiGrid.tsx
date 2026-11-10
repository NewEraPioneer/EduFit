"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { Users, BookOpen, CheckCircle2, ShieldAlert, ShieldCheck } from "lucide-react";
import { adminApi } from "../api/adminApi";
import type { AdminDashboardStats } from "../types";

interface AdminKpiGridProps {
  stats?: AdminDashboardStats | null;
  isLoading?: boolean;
}

export function AdminKpiGrid({ stats: initialStats, isLoading: initialLoading }: AdminKpiGridProps) {
  const [stats, setStats] = useState<AdminDashboardStats | null>(initialStats ?? null);
  const [loading, setLoading] = useState<boolean>(initialLoading ?? (initialStats === undefined));
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (initialStats !== undefined) {
      setStats(initialStats);
      setLoading(initialLoading ?? false);
      return;
    }

    let isMounted = true;
    setLoading(true);
    setError(null);

    adminApi
      .getDashboardStats()
      .then((data) => {
        if (isMounted) {
          setStats(data);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (isMounted) {
          setError(err instanceof Error ? err.message : "Không thể tải số liệu thống kê.");
          setLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [initialStats, initialLoading]);

  if (loading) {
    return (
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4" data-testid="admin-kpi-loading">
        {[1, 2, 3, 4].map((i) => (
          <div
            key={i}
            className="bg-surface p-5 rounded-2xl border border-line/80 shadow-xs animate-pulse space-y-3"
          >
            <div className="flex justify-between items-center">
              <div className="h-3 w-24 bg-line rounded" />
              <div className="size-4 bg-line rounded-full" />
            </div>
            <div className="h-7 w-20 bg-line rounded mt-2" />
            <div className="h-3 w-32 bg-sunken rounded" />
          </div>
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-4 rounded-2xl bg-danger-soft border border-danger-line text-danger-strong text-xs flex items-center justify-between">
        <span>{error}</span>
        <button
          type="button"
          onClick={() => {
            setLoading(true);
            setError(null);
            adminApi
              .getDashboardStats()
              .then((d) => {
                setStats(d);
                setLoading(false);
              })
              .catch((e) => {
                setError(e instanceof Error ? e.message : "Thử lại thất bại.");
                setLoading(false);
              });
          }}
          className="font-medium underline ml-2 cursor-pointer hover:text-danger-strong"
        >
          Tải lại
        </button>
      </div>
    );
  }

  const currentStats: AdminDashboardStats = stats ?? {
    totalUsers: 0,
    totalTutors: 0,
    totalStudents: 0,
    totalParents: 0,
    activeClasses: 0,
    completedSessions: 0,
    pendingVerifications: 0,
    pendingKyc: 0,
  };

  const pendingCount = currentStats.pendingVerifications ?? currentStats.pendingKyc ?? 0;

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4" data-testid="admin-kpi-grid">
      {/* 1. Tổng người dùng */}
      <div className="card-lift bg-surface p-5 rounded-2xl border border-line/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-muted">
          <span className="text-xs font-medium">Tổng người dùng</span>
          <Users className="size-4 text-brand-strong" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-bold font-heading text-ink">
            {currentStats.totalUsers.toLocaleString()}
          </div>
          <div className="text-xs text-muted font-medium mt-1">
            <span className="font-medium text-brand-strong">{currentStats.totalTutors}</span> gia sư ·{" "}
            <span className="font-medium text-brand-strong">{currentStats.totalStudents}</span> học sinh ·{" "}
            <span className="font-medium text-ink-2">{currentStats.totalParents}</span> phụ huynh
          </div>
        </div>
      </div>

      {/* 2. Lớp học hoạt động */}
      <div className="card-lift bg-surface p-5 rounded-2xl border border-line/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-muted">
          <span className="text-xs font-medium">Lớp học hoạt động</span>
          <BookOpen className="size-4 text-brand-strong" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-bold font-heading text-ink">
            {currentStats.activeClasses.toLocaleString()} <span className="text-sm font-normal text-muted">lớp</span>
          </div>
          <div className="text-xs text-brand-strong font-medium mt-1">
            Đang kết nối & giảng dạy thực tế
          </div>
        </div>
      </div>

      {/* 3. Buổi học hoàn tất */}
      <div className="card-lift bg-surface p-5 rounded-2xl border border-line/80 shadow-xs flex flex-col justify-between">
        <div className="flex items-center justify-between text-muted">
          <span className="text-xs font-medium">Buổi học hoàn tất</span>
          <CheckCircle2 className="size-4 text-success-strong" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-bold font-heading text-ink">
            {currentStats.completedSessions.toLocaleString()} <span className="text-sm font-normal text-muted">buổi</span>
          </div>
          <div className="text-xs text-success-strong font-medium mt-1">
            Đã hoàn thành trên hệ thống
          </div>
        </div>
      </div>

      {/* 4. Hồ sơ KYC chờ thẩm định */}
      <Link
        href="/admin/verification-queue"
        className={`p-5 rounded-2xl border shadow-xs flex flex-col justify-between transition-all group ${
          pendingCount > 0
            ? "bg-action-soft/50 border-action-line/80 hover:border-action"
            : "bg-surface border-line/80 hover:border-line-strong"
        }`}
      >
        <div className="flex items-center justify-between text-muted">
          <span
            className={`text-xs font-medium ${
              pendingCount > 0 ? "text-action-strong" : "text-muted"
            }`}
          >
            Hồ sơ KYC chờ duyệt
          </span>
          {pendingCount > 0 ? (
            <ShieldAlert className="size-4 text-action-strong transition-transform" />
          ) : (
            <ShieldCheck className="size-4 text-success-strong" />
          )}
        </div>
        <div className="mt-3">
          <div
            className={`text-2xl font-bold font-heading ${
              pendingCount > 0
                ? "text-action-strong"
                : "text-ink"
            }`}
          >
            {pendingCount} <span className="text-sm font-normal text-muted">hồ sơ</span>
          </div>
          <div
            className={`text-xs font-medium mt-1 ${
              pendingCount > 0
                ? "text-action-strong group-hover:underline"
                : "text-success-strong"
            }`}
          >
            {pendingCount > 0 ? "Nhấn để thẩm định ngay →" : "Hàng đợi đã giải tỏa 100%"}
          </div>
        </div>
      </Link>
    </div>
  );
}

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
            className="bg-white dark:bg-stone-900 p-5 rounded-2xl border border-stone-200/80 dark:border-stone-800 shadow-xs animate-pulse space-y-3"
          >
            <div className="flex justify-between items-center">
              <div className="h-3 w-24 bg-stone-200 dark:bg-stone-800 rounded" />
              <div className="size-4 bg-stone-200 dark:bg-stone-800 rounded-full" />
            </div>
            <div className="h-7 w-20 bg-stone-200 dark:bg-stone-800 rounded mt-2" />
            <div className="h-3 w-32 bg-stone-100 dark:bg-stone-800/60 rounded" />
          </div>
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-4 rounded-2xl bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-800/50 text-red-700 dark:text-red-400 text-xs flex items-center justify-between">
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
          className="font-bold underline ml-2 cursor-pointer hover:text-red-800 dark:hover:text-red-300"
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
      <div className="bg-white dark:bg-stone-900 p-5 rounded-2xl border border-stone-200/80 dark:border-stone-800 shadow-xs flex flex-col justify-between transition-colors">
        <div className="flex items-center justify-between text-neutral-400 dark:text-stone-500">
          <span className="text-xs font-bold uppercase tracking-wider">Tổng người dùng</span>
          <Users className="size-4 text-purple-700 dark:text-purple-400" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-extrabold font-heading text-neutral-900 dark:text-stone-100">
            {currentStats.totalUsers.toLocaleString()}
          </div>
          <div className="text-[11px] text-neutral-500 dark:text-stone-400 font-medium mt-1">
            <span className="font-semibold text-purple-700 dark:text-purple-400">{currentStats.totalTutors}</span> gia sư ·{" "}
            <span className="font-semibold text-indigo-700 dark:text-indigo-400">{currentStats.totalStudents}</span> học sinh ·{" "}
            <span className="font-semibold text-stone-600 dark:text-stone-300">{currentStats.totalParents}</span> phụ huynh
          </div>
        </div>
      </div>

      {/* 2. Lớp học hoạt động */}
      <div className="bg-white dark:bg-stone-900 p-5 rounded-2xl border border-stone-200/80 dark:border-stone-800 shadow-xs flex flex-col justify-between transition-colors">
        <div className="flex items-center justify-between text-neutral-400 dark:text-stone-500">
          <span className="text-xs font-bold uppercase tracking-wider">Lớp học hoạt động</span>
          <BookOpen className="size-4 text-indigo-600 dark:text-indigo-400" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-extrabold font-heading text-neutral-900 dark:text-stone-100">
            {currentStats.activeClasses.toLocaleString()} <span className="text-sm font-normal text-neutral-500 dark:text-stone-400">lớp</span>
          </div>
          <div className="text-[11px] text-indigo-700 dark:text-indigo-400 font-semibold mt-1">
            Đang kết nối & giảng dạy thực tế
          </div>
        </div>
      </div>

      {/* 3. Buổi học hoàn tất */}
      <div className="bg-white dark:bg-stone-900 p-5 rounded-2xl border border-stone-200/80 dark:border-stone-800 shadow-xs flex flex-col justify-between transition-colors">
        <div className="flex items-center justify-between text-neutral-400 dark:text-stone-500">
          <span className="text-xs font-bold uppercase tracking-wider">Buổi học hoàn tất</span>
          <CheckCircle2 className="size-4 text-emerald-600 dark:text-emerald-400" />
        </div>
        <div className="mt-3">
          <div className="text-2xl font-extrabold font-heading text-neutral-900 dark:text-stone-100">
            {currentStats.completedSessions.toLocaleString()} <span className="text-sm font-normal text-neutral-500 dark:text-stone-400">buổi</span>
          </div>
          <div className="text-[11px] text-emerald-600 dark:text-emerald-400 font-semibold mt-1">
            Đã hoàn thành trên hệ thống
          </div>
        </div>
      </div>

      {/* 4. Hồ sơ KYC chờ thẩm định */}
      <Link
        href="/admin/verification-queue"
        className={`p-5 rounded-2xl border shadow-xs flex flex-col justify-between transition-all group ${
          pendingCount > 0
            ? "bg-amber-50/50 dark:bg-amber-950/20 border-amber-200/80 dark:border-amber-800/60 hover:border-amber-400 dark:hover:border-amber-600"
            : "bg-white dark:bg-stone-900 border-stone-200/80 dark:border-stone-800 hover:border-stone-300 dark:hover:border-stone-700"
        }`}
      >
        <div className="flex items-center justify-between text-neutral-400 dark:text-stone-500">
          <span
            className={`text-xs font-bold uppercase tracking-wider ${
              pendingCount > 0 ? "text-amber-800 dark:text-amber-400" : "text-neutral-500 dark:text-stone-400"
            }`}
          >
            Hồ sơ KYC chờ duyệt
          </span>
          {pendingCount > 0 ? (
            <ShieldAlert className="size-4 text-amber-600 dark:text-amber-400 group-hover:scale-110 transition-transform" />
          ) : (
            <ShieldCheck className="size-4 text-emerald-600 dark:text-emerald-400" />
          )}
        </div>
        <div className="mt-3">
          <div
            className={`text-2xl font-extrabold font-heading ${
              pendingCount > 0
                ? "text-amber-800 dark:text-amber-300"
                : "text-neutral-900 dark:text-stone-100"
            }`}
          >
            {pendingCount} <span className="text-sm font-normal text-neutral-500 dark:text-stone-400">hồ sơ</span>
          </div>
          <div
            className={`text-[11px] font-semibold mt-1 ${
              pendingCount > 0
                ? "text-amber-700 dark:text-amber-400 group-hover:underline"
                : "text-emerald-600 dark:text-emerald-400"
            }`}
          >
            {pendingCount > 0 ? "Nhấn để thẩm định ngay →" : "Hàng đợi đã giải tỏa 100%"}
          </div>
        </div>
      </Link>
    </div>
  );
}

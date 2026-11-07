"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  Activity,
  CheckCircle2,
  AlertTriangle,
  RefreshCw,
  Server,
  Clock,
} from "lucide-react";
import { adminApi } from "@/features/admin/api/adminApi";

type HealthStatus = "CHECKING" | "UP" | "DOWN";

/**
 * Chỉ hiển thị những gì đo được thật từ trình duyệt: gọi API thống kê admin (truy vấn PostgreSQL)
 * để biết API + CSDL có phản hồi hay không và độ trễ khứ hồi. Không có endpoint health riêng nên
 * không hiển thị uptime hay trạng thái từng thành phần hạ tầng.
 */
export function AdminSystemHealthCard() {
  const [status, setStatus] = useState<HealthStatus>("CHECKING");
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [apiLatency, setApiLatency] = useState<number | null>(null);
  const [lastChecked, setLastChecked] = useState<Date | null>(null);

  const checkHealth = useCallback(async (isManualRefresh = false) => {
    if (isManualRefresh) {
      setRefreshing(true);
    }
    setError(null);

    const startTime = performance.now();
    try {
      await adminApi.getDashboardStats();
      setApiLatency(Math.round(performance.now() - startTime));
      setStatus("UP");
    } catch (err: unknown) {
      setApiLatency(null);
      setStatus("DOWN");
      setError(
        err instanceof Error
          ? err.message
          : "Không thể kết nối đến máy chủ quản trị"
      );
    } finally {
      setLastChecked(new Date());
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void checkHealth();
  }, [checkHealth]);

  const isUp = status === "UP";
  const isChecking = status === "CHECKING";

  return (
    <div className="bg-white dark:bg-stone-900 p-5 rounded-2xl border border-stone-200/80 dark:border-stone-800 shadow-xs space-y-4 transition-colors">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Activity
            className={`size-4 ${
              isUp
                ? "text-emerald-600 dark:text-emerald-400"
                : "text-amber-500"
            }`}
          />
          <h4 className="text-xs font-bold text-neutral-400 dark:text-stone-500 uppercase tracking-wider">
            Trạng thái hệ thống
          </h4>
        </div>
        <button
          type="button"
          onClick={() => checkHealth(true)}
          disabled={isChecking || refreshing}
          aria-label="Kiểm tra lại trạng thái hệ thống"
          className="p-1 rounded-lg text-neutral-400 hover:text-neutral-700 dark:text-stone-500 dark:hover:text-stone-300 hover:bg-stone-100 dark:hover:bg-stone-800 transition-colors disabled:opacity-50"
        >
          <RefreshCw
            className={`size-3.5 ${refreshing ? "animate-spin" : ""}`}
          />
        </button>
      </div>

      {/* Overview Badges */}
      <div className="grid grid-cols-2 gap-2 pt-1">
        <div className="p-2.5 rounded-xl bg-stone-50 dark:bg-stone-800/60 border border-stone-100 dark:border-stone-800">
          <div className="flex items-center gap-1.5 text-[11px] text-neutral-500 dark:text-stone-400">
            <Activity className="size-3 text-emerald-600 dark:text-emerald-400" />
            <span>Độ trễ API</span>
          </div>
          <p className="text-sm font-bold text-emerald-600 dark:text-emerald-400 mt-0.5">
            {apiLatency !== null ? `${apiLatency} ms` : "—"}
          </p>
        </div>

        <div className="p-2.5 rounded-xl bg-stone-50 dark:bg-stone-800/60 border border-stone-100 dark:border-stone-800">
          <div className="flex items-center gap-1.5 text-[11px] text-neutral-500 dark:text-stone-400">
            <Clock className="size-3 text-sky-600 dark:text-sky-400" />
            <span>Kiểm tra lúc</span>
          </div>
          <p className="text-sm font-bold text-neutral-900 dark:text-stone-100 mt-0.5">
            {lastChecked ? lastChecked.toLocaleTimeString("vi-VN") : "—"}
          </p>
        </div>
      </div>

      {/* Error state alert */}
      {error && (
        <div className="p-3 bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900 rounded-xl text-rose-800 dark:text-rose-300 text-xs flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <AlertTriangle className="size-4 text-rose-600 shrink-0" />
            <span className="truncate">{error}</span>
          </div>
          <button
            type="button"
            onClick={() => checkHealth(true)}
            className="text-[11px] font-bold text-rose-700 dark:text-rose-400 underline hover:no-underline shrink-0"
          >
            Thử lại
          </button>
        </div>
      )}

      {/* Service row */}
      <div className="flex items-center justify-between py-1 text-xs">
        <div className="flex items-center gap-2">
          <Server className="size-3.5 text-neutral-400 dark:text-stone-500 shrink-0" />
          <div>
            <p className="font-medium text-neutral-700 dark:text-stone-300">
              API & Cơ sở dữ liệu
            </p>
            <p className="text-[10px] text-neutral-400 dark:text-stone-500">
              Đo bằng truy vấn thống kê quản trị
            </p>
          </div>
        </div>

        <div className="text-right shrink-0">
          {isChecking ? (
            <span className="text-neutral-400 font-bold text-[11px]">Đang kiểm tra…</span>
          ) : isUp ? (
            <span className="inline-flex items-center gap-1 text-emerald-700 dark:text-emerald-400 font-bold text-[11px]">
              <CheckCircle2 className="size-3 text-emerald-600 dark:text-emerald-400" />
              Hoạt động
            </span>
          ) : (
            <span className="inline-flex items-center gap-1 text-rose-700 dark:text-rose-400 font-bold text-[11px]">
              <AlertTriangle className="size-3 text-rose-600 dark:text-rose-400" />
              Mất kết nối
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

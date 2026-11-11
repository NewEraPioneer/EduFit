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
    <div className="bg-surface p-5 rounded-2xl border border-line/80 shadow-xs space-y-4 transition-colors">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Activity
            className={`size-4 ${
              isUp
                ? "text-success-strong"
                : "text-action-strong"
            }`}
          />
          <h4 className="text-xs font-medium text-muted">
            Trạng thái hệ thống
          </h4>
        </div>
        <button
          type="button"
          onClick={() => checkHealth(true)}
          disabled={isChecking || refreshing}
          aria-label="Kiểm tra lại trạng thái hệ thống"
          className="p-1 rounded-lg text-muted hover:text-ink-2 hover:bg-sunken transition-colors disabled:opacity-50"
        >
          <RefreshCw
            className={`size-3.5 ${refreshing ? "animate-spin" : ""}`}
          />
        </button>
      </div>

      {/* Overview Badges */}
      <div className="grid grid-cols-2 gap-2 pt-1">
        <div className="p-2.5 rounded-xl bg-paper border border-line">
          <div className="flex items-center gap-1.5 text-xs text-muted">
            <Activity className="size-3 text-success-strong" />
            <span>Độ trễ API</span>
          </div>
          <p className="text-sm font-medium text-success-strong mt-0.5">
            {apiLatency !== null ? `${apiLatency} ms` : "—"}
          </p>
        </div>

        <div className="p-2.5 rounded-xl bg-paper border border-line">
          <div className="flex items-center gap-1.5 text-xs text-muted">
            <Clock className="size-3 text-brand-strong" />
            <span>Kiểm tra lúc</span>
          </div>
          <p className="text-sm font-medium text-ink mt-0.5">
            {lastChecked ? lastChecked.toLocaleTimeString("vi-VN") : "—"}
          </p>
        </div>
      </div>

      {/* Error state alert */}
      {error && (
        <div className="p-3 bg-danger-soft border border-danger-line rounded-xl text-danger-strong text-xs flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <AlertTriangle className="size-4 text-danger-strong shrink-0" />
            <span className="truncate">{error}</span>
          </div>
          <button
            type="button"
            onClick={() => checkHealth(true)}
            className="text-xs font-medium text-danger-strong underline hover:no-underline shrink-0"
          >
            Thử lại
          </button>
        </div>
      )}

      {/* Service row */}
      <div className="flex items-center justify-between py-1 text-xs">
        <div className="flex items-center gap-2">
          <Server className="size-3.5 text-muted shrink-0" />
          <div>
            <p className="font-medium text-ink-2">
              API & Cơ sở dữ liệu
            </p>
            <p className="text-xs text-muted">
              Đo bằng truy vấn thống kê quản trị
            </p>
          </div>
        </div>

        <div className="text-right shrink-0">
          {isChecking ? (
            <span className="text-muted font-medium text-xs">Đang kiểm tra…</span>
          ) : isUp ? (
            <span className="inline-flex items-center gap-1 text-success-strong font-medium text-xs">
              <CheckCircle2 className="size-3 text-success-strong" />
              Hoạt động
            </span>
          ) : (
            <span className="inline-flex items-center gap-1 text-danger-strong font-medium text-xs">
              <AlertTriangle className="size-3 text-danger-strong" />
              Mất kết nối
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

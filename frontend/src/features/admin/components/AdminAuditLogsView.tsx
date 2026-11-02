"use client";

import React, { useEffect, useState, useCallback } from "react";
import {
  ScrollText,
  Search,
  Filter,
  ChevronLeft,
  ChevronRight,
  ShieldAlert,
  ShieldCheck,
  RotateCcw,
  BookOpen,
  User,
  Clock,
  Loader2,
  RefreshCw,
  Eye,
  X,
} from "lucide-react";
import { adminApi } from "../api/adminApi";
import type { AuditLogItem, PagedAuditLogs } from "../types";

export function AdminAuditLogsView() {
  const [logsData, setLogsData] = useState<PagedAuditLogs>({
    items: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });
  const [currentPage, setCurrentPage] = useState(0);
  const [actionFilter, setActionFilter] = useState<string>("");
  const [customAction, setCustomAction] = useState<string>("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedLog, setSelectedLog] = useState<AuditLogItem | null>(null);

  const fetchLogs = useCallback(async (page: number, action?: string) => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await adminApi.getAuditLogs({
        page,
        size: 20,
        action: action?.trim() || undefined,
      });
      setLogsData(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Không thể tải nhật ký kiểm toán.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchLogs(currentPage, actionFilter);
  }, [currentPage, actionFilter, fetchLogs]);

  const handleFilterSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setCurrentPage(0);
    setActionFilter(customAction);
  };

  const handlePresetSelect = (actionVal: string) => {
    setCustomAction(actionVal);
    setActionFilter(actionVal);
    setCurrentPage(0);
  };

  const getActionBadge = (action: string) => {
    const act = action.toUpperCase();

    if (act.includes("REVOKE") || act.includes("DELETE") || act.includes("REJECT")) {
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-rose-100 dark:bg-rose-950/60 text-rose-800 dark:text-rose-300 border border-rose-200 dark:border-rose-800/60">
          <ShieldAlert className="size-3" />
          <span>{action}</span>
        </span>
      );
    }

    if (act.includes("APPROVE") || act.includes("CONFIRM") || act.includes("CREATE")) {
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800/60">
          <ShieldCheck className="size-3" />
          <span>{action}</span>
        </span>
      );
    }

    if (act.includes("UPDATE") || act.includes("TOGGLE") || act.includes("EDIT")) {
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-indigo-100 dark:bg-indigo-950/60 text-indigo-800 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-800/60">
          <RotateCcw className="size-3" />
          <span>{action}</span>
        </span>
      );
    }

    return (
      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-stone-100 dark:bg-stone-800 text-neutral-700 dark:text-stone-300 border border-stone-200 dark:border-stone-700">
        <BookOpen className="size-3" />
        <span>{action}</span>
      </span>
    );
  };

  return (
    <div className="space-y-6 sm:space-y-8 animate-in fade-in duration-200">
      {/* 1. Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <div className="size-8 rounded-xl bg-stone-900 text-white dark:bg-stone-800 dark:text-purple-400 flex items-center justify-center">
              <ScrollText className="size-4" />
            </div>
            <h2 className="font-heading text-2xl sm:text-3xl font-extrabold text-neutral-900 dark:text-stone-100 tracking-tight">
              Nhật ký Kiểm toán Hệ thống
            </h2>
          </div>
          <p className="text-xs sm:text-sm text-neutral-500 dark:text-stone-400 mt-1 font-medium">
            Truy vết độc lập và minh bạch 100% các hành động phê duyệt, thu hồi và thay đổi cấu hình quản trị.
          </p>
        </div>

        <button
          type="button"
          onClick={() => fetchLogs(currentPage, actionFilter)}
          disabled={isLoading}
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-white dark:bg-stone-900 hover:bg-stone-50 dark:hover:bg-stone-800 border border-stone-200 dark:border-stone-800 text-neutral-700 dark:text-stone-300 rounded-xl text-xs font-semibold shadow-2xs transition-colors shrink-0 disabled:opacity-50 cursor-pointer self-start sm:self-auto"
        >
          <RefreshCw className={`size-3.5 ${isLoading ? "animate-spin" : ""}`} />
          <span>Làm mới nhật ký</span>
        </button>
      </div>

      {/* 2. Preset Filter Buttons & Search Box */}
      <div className="bg-white dark:bg-stone-900 p-5 rounded-2xl border border-stone-200/80 dark:border-stone-800 shadow-xs space-y-4">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-bold text-neutral-500 dark:text-stone-400 mr-1 flex items-center gap-1">
            <Filter className="size-3.5" /> Bộ lọc nhanh:
          </span>
          <button
            type="button"
            onClick={() => handlePresetSelect("")}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-colors cursor-pointer ${
              actionFilter === ""
                ? "bg-purple-700 text-white"
                : "bg-stone-100 dark:bg-stone-800 text-neutral-700 dark:text-stone-300 hover:bg-stone-200 dark:hover:bg-stone-700"
            }`}
          >
            Tất cả hành động
          </button>
          <button
            type="button"
            onClick={() => handlePresetSelect("TUTOR_VERIFICATION_REVOKED")}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-colors cursor-pointer ${
              actionFilter === "TUTOR_VERIFICATION_REVOKED"
                ? "bg-rose-600 text-white"
                : "bg-rose-50 dark:bg-rose-950/40 text-rose-800 dark:text-rose-300 border border-rose-200 dark:border-rose-900/50 hover:bg-rose-100"
            }`}
          >
            Thu hồi KYC (REVOKED)
          </button>
          <button
            type="button"
            onClick={() => handlePresetSelect("TUTOR_VERIFICATION_APPROVED")}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-colors cursor-pointer ${
              actionFilter === "TUTOR_VERIFICATION_APPROVED"
                ? "bg-emerald-600 text-white"
                : "bg-emerald-50 dark:bg-emerald-950/40 text-emerald-800 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-900/50 hover:bg-emerald-100"
            }`}
          >
            Duyệt KYC (APPROVED)
          </button>
          <button
            type="button"
            onClick={() => handlePresetSelect("SUBJECT_STATUS_UPDATED")}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-colors cursor-pointer ${
              actionFilter === "SUBJECT_STATUS_UPDATED"
                ? "bg-indigo-600 text-white"
                : "bg-indigo-50 dark:bg-indigo-950/40 text-indigo-800 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-900/50 hover:bg-indigo-100"
            }`}
          >
            Đổi trạng thái môn học
          </button>
        </div>

        <form onSubmit={handleFilterSubmit} className="flex items-center gap-2 max-w-md">
          <div className="relative flex-1">
            <Search className="size-4 absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400 dark:text-stone-500" />
            <input
              type="text"
              value={customAction}
              onChange={(e) => setCustomAction(e.target.value)}
              placeholder="Nhập mã sự kiện (VD: USER_LOGIN, REVOKE...)"
              className="w-full pl-9 pr-3 py-2 rounded-xl border border-stone-200 dark:border-stone-700 text-xs text-neutral-900 dark:text-stone-100 bg-stone-50/50 dark:bg-stone-800/50 outline-none focus:border-purple-600 focus:bg-white dark:focus:bg-stone-900 transition-all"
            />
          </div>
          <button
            type="submit"
            className="px-4 py-2 bg-stone-900 dark:bg-stone-800 text-white rounded-xl text-xs font-bold hover:bg-stone-800 dark:hover:bg-stone-700 transition-colors cursor-pointer shrink-0"
          >
            Lọc
          </button>
          {actionFilter && (
            <button
              type="button"
              onClick={() => handlePresetSelect("")}
              className="px-2.5 py-2 text-xs text-neutral-500 dark:text-stone-400 hover:text-neutral-900 dark:hover:text-stone-100 cursor-pointer"
            >
              Xóa lọc
            </button>
          )}
        </form>
      </div>

      {error && (
        <div className="p-4 rounded-2xl bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-400 text-xs flex items-center justify-between">
          <span>{error}</span>
          <button
            type="button"
            onClick={() => fetchLogs(currentPage, actionFilter)}
            className="underline font-bold ml-2 hover:text-red-900 dark:hover:text-red-300"
          >
            Thử lại
          </button>
        </div>
      )}

      {/* 3. Audit Logs Table */}
      <div className="bg-white dark:bg-stone-900 rounded-3xl border border-stone-200/80 dark:border-stone-800 shadow-xs overflow-hidden transition-colors">
        {isLoading ? (
          <div className="p-12 flex flex-col items-center justify-center gap-3">
            <Loader2 className="size-6 text-purple-700 dark:text-purple-400 animate-spin" />
            <span className="text-xs text-neutral-500 dark:text-stone-400 font-medium">
              Đang tải nhật ký kiểm toán...
            </span>
          </div>
        ) : logsData.items.length === 0 ? (
          <div className="p-12 text-center space-y-3">
            <div className="size-12 rounded-2xl bg-stone-100 dark:bg-stone-800 text-neutral-400 dark:text-stone-500 flex items-center justify-center mx-auto">
              <ScrollText className="size-6" />
            </div>
            <h4 className="text-sm font-bold text-neutral-900 dark:text-stone-100">
              Không tìm thấy nhật ký kiểm toán nào
            </h4>
            <p className="text-xs text-neutral-500 dark:text-stone-400 max-w-sm mx-auto">
              {actionFilter
                ? `Không có sự kiện nào khớp với mã hành động "${actionFilter}".`
                : "Hệ thống chưa ghi nhận bản ghi kiểm toán nào."}
            </p>
            {actionFilter && (
              <button
                type="button"
                onClick={() => handlePresetSelect("")}
                className="text-xs font-bold text-purple-700 dark:text-purple-400 hover:underline cursor-pointer"
              >
                Xem toàn bộ sự kiện
              </button>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-stone-200 dark:border-stone-800 bg-stone-50/70 dark:bg-stone-800/40 text-[11px] font-bold text-neutral-500 dark:text-stone-400 uppercase tracking-wider">
                  <th className="py-3.5 px-4 sm:px-6">Thời gian</th>
                  <th className="py-3.5 px-4">Hành động (Action)</th>
                  <th className="py-3.5 px-4">Người thực hiện (Actor)</th>
                  <th className="py-3.5 px-4">Chi tiết / Lý do</th>
                  <th className="py-3.5 px-4 sm:px-6 text-right">Chi tiết</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100 dark:divide-stone-800 text-xs">
                {logsData.items.map((log) => {
                  const actorDisplay = log.actor || log.actorUserId || "Hệ thống (System)";
                  const reasonOrDetails = log.reason || log.details || "Thực hiện thành công";
                  const createdAtFormatted = log.createdAt
                    ? new Date(log.createdAt).toLocaleString("vi-VN", {
                        year: "numeric",
                        month: "2-digit",
                        day: "2-digit",
                        hour: "2-digit",
                        minute: "2-digit",
                        second: "2-digit",
                      })
                    : "—";

                  return (
                    <tr
                      key={log.id}
                      className="hover:bg-stone-50/60 dark:hover:bg-stone-800/40 transition-colors"
                    >
                      <td className="py-3.5 px-4 sm:px-6 whitespace-nowrap">
                        <div className="flex items-center gap-1.5 text-neutral-800 dark:text-stone-200 font-mono text-[11px]">
                          <Clock className="size-3 text-neutral-400 dark:text-stone-500 shrink-0" />
                          <span>{createdAtFormatted}</span>
                        </div>
                      </td>

                      <td className="py-3.5 px-4">
                        {getActionBadge(log.action)}
                        {log.targetType && (
                          <div className="text-[10px] text-neutral-400 dark:text-stone-500 font-mono mt-0.5">
                            Target: {log.targetType} {log.targetId ? `(#${log.targetId.slice(0, 8)})` : ""}
                          </div>
                        )}
                      </td>

                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-1.5 font-medium text-neutral-900 dark:text-stone-100">
                          <User className="size-3.5 text-neutral-400 dark:text-stone-500 shrink-0" />
                          <span className="font-mono text-[11px] truncate max-w-[180px]">
                            {actorDisplay}
                          </span>
                        </div>
                      </td>

                      <td className="py-3.5 px-4 max-w-xs sm:max-w-md">
                        <p className="text-neutral-700 dark:text-stone-300 line-clamp-2 leading-relaxed">
                          {reasonOrDetails}
                        </p>
                      </td>

                      <td className="py-3.5 px-4 sm:px-6 text-right">
                        <button
                          type="button"
                          onClick={() => setSelectedLog(log)}
                          className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-bold text-neutral-600 dark:text-stone-400 hover:text-purple-700 dark:hover:text-purple-400 hover:bg-stone-100 dark:hover:bg-stone-800 transition-colors cursor-pointer"
                        >
                          <Eye className="size-3.5" />
                          <span>Xem</span>
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination Footer */}
        {logsData.totalPages > 1 && (
          <div className="p-4 border-t border-stone-200 dark:border-stone-800 flex items-center justify-between text-xs text-neutral-500 dark:text-stone-400">
            <div>
              Trang <span className="font-bold text-neutral-900 dark:text-stone-100">{logsData.page + 1}</span> / {logsData.totalPages}{" "}
              <span className="hidden sm:inline">
                (Tổng số <span className="font-bold">{logsData.totalElements}</span> bản ghi)
              </span>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                disabled={logsData.page <= 0 || isLoading}
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl border border-stone-200 dark:border-stone-700 font-semibold hover:bg-stone-50 dark:hover:bg-stone-800 disabled:opacity-40 cursor-pointer"
              >
                <ChevronLeft className="size-3.5" />
                <span>Trước</span>
              </button>
              <button
                type="button"
                onClick={() => setCurrentPage((p) => Math.min(logsData.totalPages - 1, p + 1))}
                disabled={logsData.page >= logsData.totalPages - 1 || isLoading}
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl border border-stone-200 dark:border-stone-700 font-semibold hover:bg-stone-50 dark:hover:bg-stone-800 disabled:opacity-40 cursor-pointer"
              >
                <span>Sau</span>
                <ChevronRight className="size-3.5" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* 4. Audit Log Detail Modal */}
      {selectedLog && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs animate-in fade-in duration-150 overflow-y-auto"
        >
          <div className="bg-white dark:bg-stone-900 w-full max-w-lg rounded-3xl border border-stone-200 dark:border-stone-800 shadow-xl overflow-hidden transition-colors my-auto">
            <div className="px-6 py-4 border-b border-stone-100 dark:border-stone-800 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ScrollText className="size-4 text-purple-700 dark:text-purple-400" />
                <h3 className="font-heading text-base font-bold text-neutral-900 dark:text-stone-100">
                  Chi tiết bản ghi kiểm toán
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setSelectedLog(null)}
                className="size-8 rounded-full flex items-center justify-center text-neutral-400 hover:text-neutral-700 dark:hover:text-stone-200 hover:bg-stone-100 dark:hover:bg-stone-800"
              >
                <X className="size-4" />
              </button>
            </div>

            <div className="p-6 space-y-3.5 text-xs">
              <div>
                <span className="text-neutral-400 dark:text-stone-500 font-medium">Mã sự kiện (ID):</span>
                <div className="font-mono text-neutral-900 dark:text-stone-100 mt-0.5">{selectedLog.id}</div>
              </div>

              <div>
                <span className="text-neutral-400 dark:text-stone-500 font-medium">Hành động:</span>
                <div className="mt-1">{getActionBadge(selectedLog.action)}</div>
              </div>

              <div>
                <span className="text-neutral-400 dark:text-stone-500 font-medium">Người thực hiện:</span>
                <div className="font-mono text-neutral-900 dark:text-stone-100 mt-0.5">
                  {selectedLog.actor || selectedLog.actorUserId || "Hệ thống"}
                </div>
              </div>

              {selectedLog.targetType && (
                <div>
                  <span className="text-neutral-400 dark:text-stone-500 font-medium">Đối tượng tác động:</span>
                  <div className="font-mono text-neutral-900 dark:text-stone-100 mt-0.5">
                    {selectedLog.targetType} (ID: {selectedLog.targetId || "N/A"})
                  </div>
                </div>
              )}

              <div>
                <span className="text-neutral-400 dark:text-stone-500 font-medium">Lý do / Diễn giải:</span>
                <div className="p-3 rounded-xl bg-stone-50 dark:bg-stone-800/60 border border-stone-200/60 dark:border-stone-700/60 text-neutral-800 dark:text-stone-200 mt-1">
                  {selectedLog.reason || selectedLog.details || "Không có ghi chú cụ thể."}
                </div>
              </div>

              {selectedLog.metadata && Object.keys(selectedLog.metadata).length > 0 && (
                <div>
                  <span className="text-neutral-400 dark:text-stone-500 font-medium">Metadata:</span>
                  <pre className="p-3 rounded-xl bg-stone-900 text-stone-100 dark:bg-stone-950 text-[11px] font-mono mt-1 overflow-x-auto">
                    {JSON.stringify(selectedLog.metadata, null, 2)}
                  </pre>
                </div>
              )}

              <div>
                <span className="text-neutral-400 dark:text-stone-500 font-medium">Thời gian tạo:</span>
                <div className="text-neutral-700 dark:text-stone-300 mt-0.5">
                  {selectedLog.createdAt ? new Date(selectedLog.createdAt).toLocaleString("vi-VN") : "—"}
                </div>
              </div>
            </div>

            <div className="p-4 bg-stone-50 dark:bg-stone-800/50 border-t border-stone-100 dark:border-stone-800 flex justify-end">
              <button
                type="button"
                onClick={() => setSelectedLog(null)}
                className="px-4 py-2 bg-stone-900 dark:bg-stone-800 text-white rounded-xl text-xs font-bold hover:bg-stone-800 cursor-pointer"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

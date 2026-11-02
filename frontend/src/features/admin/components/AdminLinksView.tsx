"use client";

import React, { useEffect, useState } from "react";
import {
  HeartHandshake,
  Search,
  ShieldAlert,
  CheckCircle2,
  AlertTriangle,
  Loader2,
  RefreshCw,
  Trash2,
  Info,
} from "lucide-react";
import { adminApi } from "../api/adminApi";
import type { ParentStudentAdminLink } from "../types";

export function AdminLinksView() {
  const [links, setLinks] = useState<ParentStudentAdminLink[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [emailSearch, setEmailSearch] = useState("");
  const [activeSearch, setActiveSearch] = useState("");
  const [revokingId, setRevokingId] = useState<string | null>(null);
  const [confirmModalLink, setConfirmModalLink] = useState<ParentStudentAdminLink | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const fetchLinks = async (email?: string) => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await adminApi.getParentStudentLinks(email);
      setLinks(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Không thể tải danh sách liên kết gia đình.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchLinks(activeSearch);
  }, [activeSearch]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setActiveSearch(emailSearch);
  };

  const handleRevokeConfirm = async () => {
    if (!confirmModalLink) return;
    const targetId = confirmModalLink.linkId || confirmModalLink.id;
    if (!targetId) return;

    setRevokingId(targetId);
    try {
      await adminApi.revokeParentStudentLink(targetId);
      setLinks((prev) => prev.filter((l) => (l.linkId || l.id) !== targetId));
      setSuccessMessage("Đã thu hồi quyền liên kết Phụ huynh - Học sinh (UC3.3) thành công.");
      setConfirmModalLink(null);
      setTimeout(() => setSuccessMessage(null), 3500);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Thu hồi liên kết thất bại.");
    } finally {
      setRevokingId(null);
    }
  };

  return (
    <div className="space-y-6 sm:space-y-8 animate-in fade-in duration-200">
      {/* 1. Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <div className="size-8 rounded-xl bg-purple-100 dark:bg-purple-950/60 text-purple-700 dark:text-purple-400 flex items-center justify-center">
              <HeartHandshake className="size-4" />
            </div>
            <h2 className="font-heading text-2xl sm:text-3xl font-extrabold text-neutral-900 dark:text-stone-100 tracking-tight">
              Giám sát & Thu hồi Liên kết Gia đình (UC3.3)
            </h2>
          </div>
          <p className="text-xs sm:text-sm text-neutral-500 dark:text-stone-400 mt-1 font-medium">
            Quản trị quyền giám sát học tập giữa Phụ huynh và Học sinh nhằm bảo vệ tính toàn vẹn dữ liệu cá nhân.
          </p>
        </div>

        <button
          type="button"
          onClick={() => fetchLinks(activeSearch)}
          disabled={isLoading}
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-white dark:bg-stone-900 hover:bg-stone-50 dark:hover:bg-stone-800 border border-stone-200 dark:border-stone-800 text-neutral-700 dark:text-stone-300 rounded-xl text-xs font-semibold shadow-2xs transition-colors shrink-0 disabled:opacity-50 cursor-pointer self-start sm:self-auto"
        >
          <RefreshCw className={`size-3.5 ${isLoading ? "animate-spin" : ""}`} />
          <span>Tải lại</span>
        </button>
      </div>

      {/* 2. SRS Alignment Notice */}
      <div className="p-4 rounded-2xl bg-stone-100 dark:bg-stone-800/60 border border-stone-200/80 dark:border-stone-700 text-xs text-neutral-700 dark:text-stone-300 flex items-start gap-3">
        <Info className="size-4 shrink-0 text-purple-700 dark:text-purple-400 mt-0.5" />
        <div className="leading-relaxed">
          <span className="font-bold">Quy chuẩn nghiệp vụ UC3.3 & BR-38:</span> Khi phụ huynh hoặc học sinh
          yêu cầu hủy bỏ ủy quyền giám sát, hoặc khi có tranh chấp tài khoản gia đình, Quản trị viên có thẩm
          quyền thu hồi liên kết. Phụ huynh sẽ lập tức mất quyền xem tiến độ và lịch học của học sinh.
        </div>
      </div>

      {/* 3. Feedback */}
      {successMessage && (
        <div className="p-3.5 rounded-2xl bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-300 text-xs font-semibold flex items-center gap-2">
          <CheckCircle2 className="size-4 text-emerald-600 dark:text-emerald-400" />
          <span>{successMessage}</span>
        </div>
      )}

      {error && (
        <div className="p-4 rounded-2xl bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-400 text-xs flex items-center justify-between">
          <span>{error}</span>
          <button
            type="button"
            onClick={() => fetchLinks(activeSearch)}
            className="underline font-bold ml-2 hover:text-red-900"
          >
            Thử lại
          </button>
        </div>
      )}

      {/* 4. Search Filter */}
      <form
        onSubmit={handleSearchSubmit}
        className="bg-white dark:bg-stone-900 p-4 rounded-2xl border border-stone-200/80 dark:border-stone-800 shadow-xs flex items-center gap-2 max-w-md"
      >
        <div className="relative flex-1">
          <Search className="size-4 absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400 dark:text-stone-500" />
          <input
            type="email"
            value={emailSearch}
            onChange={(e) => setEmailSearch(e.target.value)}
            placeholder="Tra cứu theo email phụ huynh / học sinh..."
            className="w-full pl-9 pr-3 py-2 rounded-xl border border-stone-200 dark:border-stone-700 text-xs text-neutral-900 dark:text-stone-100 bg-stone-50/50 dark:bg-stone-800/50 outline-none focus:border-purple-600 transition-all"
          />
        </div>
        <button
          type="submit"
          className="px-4 py-2 bg-purple-700 hover:bg-purple-800 text-white rounded-xl text-xs font-bold transition-colors cursor-pointer"
        >
          Tìm kiếm
        </button>
        {activeSearch && (
          <button
            type="button"
            onClick={() => {
              setEmailSearch("");
              setActiveSearch("");
            }}
            className="text-xs text-neutral-500 dark:text-stone-400 hover:text-neutral-900 dark:hover:text-stone-100"
          >
            Đặt lại
          </button>
        )}
      </form>

      {/* 5. Links Table */}
      <div className="bg-white dark:bg-stone-900 rounded-3xl border border-stone-200/80 dark:border-stone-800 shadow-xs overflow-hidden transition-colors">
        {isLoading ? (
          <div className="p-12 flex flex-col items-center justify-center gap-3">
            <Loader2 className="size-6 text-purple-700 dark:text-purple-400 animate-spin" />
            <span className="text-xs text-neutral-500 dark:text-stone-400 font-medium">
              Đang tải danh sách liên kết...
            </span>
          </div>
        ) : links.length === 0 ? (
          <div className="p-12 text-center space-y-3">
            <div className="size-12 rounded-2xl bg-stone-100 dark:bg-stone-800 text-neutral-400 dark:text-stone-500 flex items-center justify-center mx-auto">
              <HeartHandshake className="size-6" />
            </div>
            <h4 className="text-sm font-bold text-neutral-900 dark:text-stone-100">
              Chưa có liên kết gia đình nào
            </h4>
            <p className="text-xs text-neutral-500 dark:text-stone-400 max-w-sm mx-auto">
              {activeSearch
                ? `Không tìm thấy liên kết nào với email "${activeSearch}".`
                : "Hệ thống hiện tại chưa có mối quan hệ Phụ huynh - Học sinh được xác lập."}
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-stone-200 dark:border-stone-800 bg-stone-50/70 dark:bg-stone-800/40 text-[11px] font-bold text-neutral-500 dark:text-stone-400 uppercase tracking-wider">
                  <th className="py-3.5 px-4 sm:px-6">Mã liên kết</th>
                  <th className="py-3.5 px-4">Phụ huynh (Parent)</th>
                  <th className="py-3.5 px-4">Học sinh (Student)</th>
                  <th className="py-3.5 px-4">Trạng thái</th>
                  <th className="py-3.5 px-4 sm:px-6 text-right">Thao tác UC3.3</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100 dark:divide-stone-800 text-xs">
                {links.map((link, idx) => {
                  const linkId = link.linkId || link.id || `LINK-${idx + 1}`;
                  const isRevoking = revokingId === linkId;

                  return (
                    <tr
                      key={linkId}
                      className="hover:bg-stone-50/60 dark:hover:bg-stone-800/40 transition-colors"
                    >
                      <td className="py-3.5 px-4 sm:px-6 font-mono text-[11px] text-neutral-600 dark:text-stone-400">
                        {linkId.slice(0, 8)}...
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="font-bold text-neutral-900 dark:text-stone-100">
                          {link.parentName || link.parentEmail || "Phụ huynh"}
                        </div>
                        {link.parentId && (
                          <div className="text-[10px] text-neutral-400 dark:text-stone-500 font-mono">
                            ID: {link.parentId.slice(0, 8)}
                          </div>
                        )}
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="font-bold text-neutral-900 dark:text-stone-100">
                          {link.studentName || link.studentEmail || "Học sinh"}
                        </div>
                        {link.studentId && (
                          <div className="text-[10px] text-neutral-400 dark:text-stone-500 font-mono">
                            ID: {link.studentId.slice(0, 8)}
                          </div>
                        )}
                      </td>
                      <td className="py-3.5 px-4">
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800/60">
                          <CheckCircle2 className="size-3" />
                          <span>{link.status || "CONFIRMED"}</span>
                        </span>
                      </td>
                      <td className="py-3.5 px-4 sm:px-6 text-right">
                        <button
                          type="button"
                          onClick={() => setConfirmModalLink(link)}
                          disabled={isRevoking}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold bg-rose-50 dark:bg-rose-950/40 hover:bg-rose-100 dark:hover:bg-rose-900/50 text-rose-700 dark:text-rose-300 border border-rose-200 dark:border-rose-800/60 transition-colors cursor-pointer disabled:opacity-50"
                        >
                          {isRevoking ? (
                            <Loader2 className="size-3.5 animate-spin" />
                          ) : (
                            <Trash2 className="size-3.5" />
                          )}
                          <span>Thu hồi</span>
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Confirmation Modal */}
      {confirmModalLink && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs animate-in fade-in duration-150 overflow-y-auto"
        >
          <div className="bg-white dark:bg-stone-900 w-full max-w-md rounded-3xl border border-stone-200 dark:border-stone-800 shadow-xl p-6 space-y-4 my-auto">
            <div className="flex items-center gap-3">
              <div className="size-10 rounded-2xl bg-rose-100 dark:bg-rose-950/60 text-rose-700 dark:text-rose-400 flex items-center justify-center shrink-0">
                <AlertTriangle className="size-5" />
              </div>
              <div>
                <h3 className="font-heading text-base font-bold text-neutral-900 dark:text-stone-100">
                  Xác nhận thu hồi liên kết gia đình?
                </h3>
                <p className="text-xs text-neutral-500 dark:text-stone-400">
                  Thao tác này thực hiện theo quy chuẩn UC3.3
                </p>
              </div>
            </div>

            <p className="text-xs text-neutral-600 dark:text-stone-300 leading-relaxed">
              Phụ huynh sẽ bị hủy quyền giám sát tiến độ học tập và thông tin lịch học của học sinh ngay lập tức. Hành động này sẽ được ghi nhận vào nhật ký kiểm toán hệ thống.
            </p>

            <div className="pt-2 flex items-center justify-end gap-2.5">
              <button
                type="button"
                onClick={() => setConfirmModalLink(null)}
                className="px-4 py-2 rounded-xl border border-stone-200 dark:border-stone-700 text-xs font-semibold text-neutral-700 dark:text-stone-300 hover:bg-stone-100 dark:hover:bg-stone-800 cursor-pointer"
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                onClick={handleRevokeConfirm}
                disabled={revokingId !== null}
                className="px-4 py-2 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-xs font-bold transition-colors cursor-pointer disabled:opacity-50 flex items-center gap-1.5"
              >
                {revokingId ? <Loader2 className="size-3.5 animate-spin" /> : null}
                <span>Xác nhận thu hồi</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

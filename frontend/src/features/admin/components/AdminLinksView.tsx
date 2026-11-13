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
            <div className="size-8 rounded-xl bg-brand-soft text-brand-strong flex items-center justify-center">
              <HeartHandshake className="size-4" />
            </div>
            <h2 className="font-heading text-2xl sm:text-3xl font-bold text-ink tracking-tight">
              Giám sát và thu hồi liên kết gia đình
            </h2>
          </div>
          <p className="text-xs sm:text-sm text-muted mt-1 font-medium">
            Quản trị quyền giám sát học tập giữa Phụ huynh và Học sinh nhằm bảo vệ tính toàn vẹn dữ liệu cá nhân.
          </p>
        </div>

        <button
          type="button"
          onClick={() => fetchLinks(activeSearch)}
          disabled={isLoading}
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-surface hover:bg-paper border border-line text-ink-2 rounded-xl text-xs font-medium shadow-2xs transition-colors shrink-0 disabled:opacity-50 cursor-pointer self-start sm:self-auto"
        >
          <RefreshCw className={`size-3.5 ${isLoading ? "animate-spin" : ""}`} />
          <span>Tải lại</span>
        </button>
      </div>

      {/* 2. SRS Alignment Notice */}
      <div className="p-4 rounded-2xl bg-sunken border border-line/80 text-xs text-ink-2 flex items-start gap-3">
        <Info className="size-4 shrink-0 text-brand-strong mt-0.5" />
        <div className="leading-relaxed">
          <span className="font-medium">Lưu ý:</span> Khi phụ huynh hoặc học sinh
          yêu cầu hủy bỏ ủy quyền giám sát, hoặc khi có tranh chấp tài khoản gia đình, Quản trị viên có thẩm
          quyền thu hồi liên kết. Phụ huynh sẽ lập tức mất quyền xem tiến độ và lịch học của học sinh.
        </div>
      </div>

      {/* 3. Feedback */}
      {successMessage && (
        <div className="p-3.5 rounded-2xl bg-success-soft border border-success-line text-success-strong text-xs font-medium flex items-center gap-2">
          <CheckCircle2 className="size-4 text-success-strong" />
          <span>{successMessage}</span>
        </div>
      )}

      {error && (
        <div className="p-4 rounded-2xl bg-danger-soft border border-danger-line text-danger-strong text-xs flex items-center justify-between">
          <span>{error}</span>
          <button
            type="button"
            onClick={() => fetchLinks(activeSearch)}
            className="underline font-medium ml-2 hover:text-danger-strong"
          >
            Thử lại
          </button>
        </div>
      )}

      {/* 4. Search Filter */}
      <form
        onSubmit={handleSearchSubmit}
        className="bg-surface p-4 rounded-2xl border border-line/80 shadow-xs flex items-center gap-2 max-w-md"
      >
        <div className="relative flex-1">
          <Search className="size-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
          <input
            type="email"
            value={emailSearch}
            onChange={(e) => setEmailSearch(e.target.value)}
            placeholder="Tra cứu theo email phụ huynh / học sinh..."
            className="w-full pl-9 pr-3 py-2 rounded-xl border border-line text-xs text-ink bg-paper/50 outline-none focus:border-brand transition-all"
          />
        </div>
        <button
          type="submit"
          className="px-4 py-2 bg-brand hover:bg-brand-deep text-white rounded-xl text-xs font-medium transition-colors cursor-pointer"
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
            className="text-xs text-muted hover:text-ink"
          >
            Đặt lại
          </button>
        )}
      </form>

      {/* 5. Links Table */}
      <div className="bg-surface rounded-3xl border border-line/80 shadow-xs overflow-hidden transition-colors">
        {isLoading ? (
          <div className="p-12 flex flex-col items-center justify-center gap-3">
            <Loader2 className="size-6 text-brand-strong animate-spin" />
            <span className="text-xs text-muted font-medium">
              Đang tải danh sách liên kết...
            </span>
          </div>
        ) : links.length === 0 ? (
          <div className="p-12 text-center space-y-3">
            <div className="size-12 rounded-2xl bg-sunken text-muted flex items-center justify-center mx-auto">
              <HeartHandshake className="size-6" />
            </div>
            <h4 className="text-sm font-medium text-ink">
              Chưa có liên kết gia đình nào
            </h4>
            <p className="text-xs text-muted max-w-sm mx-auto">
              {activeSearch
                ? `Không tìm thấy liên kết nào với email "${activeSearch}".`
                : "Hệ thống hiện tại chưa có mối quan hệ Phụ huynh - Học sinh được xác lập."}
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-line bg-paper/70 text-xs font-medium text-muted">
                  <th className="py-3.5 px-4 sm:px-6">Mã liên kết</th>
                  <th className="py-3.5 px-4">Phụ huynh (Parent)</th>
                  <th className="py-3.5 px-4">Học sinh (Student)</th>
                  <th className="py-3.5 px-4">Trạng thái</th>
                  <th className="py-3.5 px-4 sm:px-6 text-right">Thao tác UC3.3</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line text-xs">
                {links.map((link, idx) => {
                  const linkId = link.linkId || link.id || `LINK-${idx + 1}`;
                  const isRevoking = revokingId === linkId;

                  return (
                    <tr
                      key={linkId}
                      className="hover:bg-paper/60 transition-colors"
                    >
                      <td className="py-3.5 px-4 sm:px-6 font-mono text-xs text-ink-2">
                        {linkId.slice(0, 8)}...
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="font-medium text-ink">
                          {link.parentName || link.parentEmail || "Phụ huynh"}
                        </div>
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="font-medium text-ink">
                          {link.studentName || link.studentEmail || "Học sinh"}
                        </div>
                      </td>
                      <td className="py-3.5 px-4">
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-success-soft text-success-strong border border-success-line">
                          <CheckCircle2 className="size-3" />
                          <span>{link.status || "CONFIRMED"}</span>
                        </span>
                      </td>
                      <td className="py-3.5 px-4 sm:px-6 text-right">
                        <button
                          type="button"
                          onClick={() => setConfirmModalLink(link)}
                          disabled={isRevoking}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium bg-danger-soft hover:bg-danger-soft text-danger-strong border border-danger-line transition-colors cursor-pointer disabled:opacity-50"
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
          className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-black/50 backdrop-blur-xs animate-in fade-in duration-150 overflow-y-auto"
        >
          <div className="bg-surface w-full max-w-dialog-confirm rounded-3xl border border-line shadow-xl p-6 space-y-4 my-auto">
            <div className="flex items-center gap-3">
              <div className="size-10 rounded-2xl bg-danger-soft text-danger-strong flex items-center justify-center shrink-0">
                <AlertTriangle className="size-5" />
              </div>
              <div>
                <h3 className="font-heading text-base font-bold text-ink">
                  Xác nhận thu hồi liên kết gia đình?
                </h3>
                <p className="text-xs text-muted">
                  Thao tác này thực hiện theo quy chuẩn UC3.3
                </p>
              </div>
            </div>

            <p className="text-xs text-ink-2 leading-relaxed">
              Phụ huynh sẽ bị hủy quyền giám sát tiến độ học tập và thông tin lịch học của học sinh ngay lập tức. Hành động này sẽ được ghi nhận vào nhật ký kiểm toán hệ thống.
            </p>

            <div className="pt-2 flex items-center justify-end gap-2.5">
              <button
                type="button"
                onClick={() => setConfirmModalLink(null)}
                className="px-4 py-2 rounded-xl border border-line text-xs font-medium text-ink-2 hover:bg-sunken cursor-pointer"
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                onClick={handleRevokeConfirm}
                disabled={revokingId !== null}
                className="px-4 py-2 bg-danger hover:bg-danger-deep text-white rounded-xl text-xs font-medium transition-colors cursor-pointer disabled:opacity-50 flex items-center gap-1.5"
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

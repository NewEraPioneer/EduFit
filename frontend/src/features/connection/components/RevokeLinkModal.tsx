"use client";

import React, { useState } from "react";
import { AlertTriangle, X, Loader2, UserX } from "lucide-react";
import type { StudentLink } from "../types";

export interface RevokeLinkModalProps {
  link: StudentLink | null;
  studentName?: string;
  isOpen: boolean;
  onClose: () => void;
  onConfirm: (link: StudentLink, reason?: string) => Promise<void>;
  targetRole?: "student" | "parent";
}

export function RevokeLinkModal({
  link,
  studentName,
  isOpen,
  onClose,
  onConfirm,
  targetRole = "student",
}: RevokeLinkModalProps) {
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen || !link) return null;

  const isTargetParent = targetRole === "parent";
  const displayName = isTargetParent
    ? "Tài khoản phụ huynh"
    : studentName || "Học sinh đã liên kết";

  const handleConfirm = async () => {
    try {
      setSubmitting(true);
      setError(null);
      await onConfirm(link, reason.trim() || undefined);
      setReason("");
      onClose();
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Đã xảy ra lỗi khi hủy liên kết."
      );
    } finally {
      setSubmitting(false);
    }
  };

  const handleClose = () => {
    if (submitting) return;
    setError(null);
    setReason("");
    onClose();
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="revoke-link-modal-title"
      aria-describedby="revoke-link-modal-desc"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-900/60 backdrop-blur-xs animate-in fade-in duration-200 overflow-y-auto"
    >
      <div className="relative w-full max-w-md overflow-hidden rounded-3xl bg-white shadow-2xl border border-stone-200 p-6 space-y-4 animate-in zoom-in-95 duration-200 my-auto">
        {/* Header */}
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="size-11 rounded-2xl bg-rose-100 text-rose-700 flex items-center justify-center shrink-0">
              <UserX className="size-5" />
            </div>
            <div>
              <h3
                id="revoke-link-modal-title"
                className="text-base font-bold text-neutral-900"
              >
                {isTargetParent ? "Hủy liên kết phụ huynh" : "Hủy liên kết học sinh"}
              </h3>
              <p
                id="revoke-link-modal-desc"
                className="text-xs text-neutral-500"
              >
                {isTargetParent
                  ? "Xác nhận ngừng liên kết tài khoản học sinh với phụ huynh"
                  : "Xác nhận ngừng liên kết tài khoản phụ huynh với học sinh"}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={handleClose}
            disabled={submitting}
            className="p-1.5 text-neutral-400 hover:text-neutral-700 rounded-lg hover:bg-stone-100 transition-colors cursor-pointer disabled:opacity-50"
            aria-label="Đóng"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* Account preview */}
        <div className="p-3.5 bg-stone-50 rounded-2xl border border-stone-200/80 flex items-center gap-3">
          <div
            className={`size-10 rounded-xl font-bold flex items-center justify-center shrink-0 text-sm ${
              isTargetParent
                ? "bg-amber-100 text-amber-800"
                : "bg-emerald-100 text-emerald-800"
            }`}
          >
            {isTargetParent ? "PH" : displayName.charAt(0).toUpperCase()}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-bold text-neutral-900 truncate">
              {displayName}
            </p>
            <p className="text-[11px] text-neutral-500">Liên kết đã được xác nhận</p>
          </div>
        </div>

        {/* Warning callout */}
        <div className="p-3.5 rounded-2xl bg-amber-50 border border-amber-200 text-amber-900 text-xs flex items-start gap-2.5">
          <AlertTriangle className="size-4 shrink-0 text-amber-600 mt-0.5" />
          <div className="space-y-1">
            <p className="font-bold">Lưu ý quan trọng:</p>
            <p className="text-amber-800 leading-relaxed text-[11px]">
              {isTargetParent
                ? "Khi hủy liên kết, phụ huynh sẽ không thể tiếp tục xem lịch học, tiến độ và kết quả học tập của bạn trên cổng phụ huynh. Để kết nối lại sau này, phụ huynh cần gửi lại lời mời mới."
                : "Khi hủy liên kết, phụ huynh sẽ không thể tiếp tục xem lịch học, tiến độ và kết quả học tập của con em trên cổng phụ huynh. Để kết nối lại trong tương lai, cần gửi lại lời mời liên kết mới."}
            </p>
          </div>
        </div>

        {/* Optional reason */}
        <div className="space-y-1.5">
          <label
            htmlFor="revoke-reason-input"
            className="block text-xs font-bold text-neutral-700"
          >
            Lý do hủy liên kết (tùy chọn)
          </label>
          <textarea
            id="revoke-reason-input"
            rows={2}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder={
              isTargetParent
                ? "Ví dụ: Tạm dừng học tập, đổi tài khoản phụ huynh..."
                : "Ví dụ: Thay đổi tài khoản phụ huynh, tạm ngừng học..."
            }
            maxLength={300}
            disabled={submitting}
            className="w-full text-xs p-2.5 rounded-xl border border-stone-200 bg-stone-50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500 resize-none text-neutral-900"
          />
        </div>

        {error && (
          <p role="alert" className="text-xs text-rose-600 font-semibold">
            {error}
          </p>
        )}

        {/* Action buttons */}
        <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-stone-100">
          <button
            type="button"
            onClick={handleClose}
            disabled={submitting}
            className="px-4 py-2.5 rounded-xl border border-stone-200 bg-white text-xs font-bold text-neutral-700 hover:bg-stone-50 transition-colors cursor-pointer disabled:opacity-50"
          >
            Hủy bỏ
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={submitting}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold text-white bg-rose-600 hover:bg-rose-700 transition-colors cursor-pointer shadow-xs disabled:opacity-50"
          >
            {submitting ? (
              <>
                <Loader2 className="size-3.5 animate-spin" />
                <span>Đang xử lý...</span>
              </>
            ) : (
              <>
                <UserX className="size-3.5" />
                <span>Xác nhận hủy liên kết</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

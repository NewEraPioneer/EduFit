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
    : studentName || "Học sinh";

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
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-night/60 backdrop-blur-xs animate-in fade-in duration-200 overflow-y-auto"
    >
      <div className="relative w-full max-w-dialog-confirm overflow-hidden rounded-3xl bg-surface shadow-2xl border border-line p-6 space-y-4 animate-in zoom-in-95 duration-200 my-auto">
        {/* Header */}
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="size-11 rounded-2xl bg-danger-soft text-danger-strong flex items-center justify-center shrink-0">
              <UserX className="size-5" />
            </div>
            <div>
              <h3
                id="revoke-link-modal-title"
                className="text-base font-medium text-ink"
              >
                {isTargetParent ? "Hủy liên kết phụ huynh" : "Hủy liên kết học sinh"}
              </h3>
              <p
                id="revoke-link-modal-desc"
                className="text-xs text-muted"
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
            className="p-1.5 text-muted hover:text-ink-2 rounded-lg hover:bg-sunken transition-colors cursor-pointer disabled:opacity-50"
            aria-label="Đóng"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* Account preview */}
        <div className="p-3.5 bg-paper rounded-2xl border border-line/80 flex items-center gap-3">
          <div
            className={`size-10 rounded-xl font-medium flex items-center justify-center shrink-0 text-sm ${
              isTargetParent
                ? "bg-action-soft text-action-strong"
                : "bg-success-soft text-success-strong"
            }`}
          >
            {isTargetParent ? "PH" : displayName.charAt(0).toUpperCase()}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-medium text-ink truncate">
              {displayName}
            </p>
            <p className="text-xs text-muted font-mono truncate">
              {isTargetParent
                ? `Mã tài khoản: ${link.parentUserId}`
                : `Mã học sinh: ${link.studentId}`}
            </p>
          </div>
        </div>

        {/* Warning callout */}
        <div className="p-3.5 rounded-2xl bg-action-soft border border-action-line text-action-strong text-xs flex items-start gap-2.5">
          <AlertTriangle className="size-4 shrink-0 text-action-strong mt-0.5" />
          <div className="space-y-1">
            <p className="font-medium">Lưu ý quan trọng:</p>
            <p className="text-action-strong leading-relaxed text-xs">
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
            className="block text-xs font-medium text-ink-2"
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
            className="w-full text-xs p-2.5 rounded-xl border border-line bg-paper focus:bg-surface focus:outline-none focus:ring-2 focus:ring-danger/20 focus:border-danger resize-none text-ink"
          />
        </div>

        {error && (
          <p role="alert" className="text-xs text-danger-strong font-medium">
            {error}
          </p>
        )}

        {/* Action buttons */}
        <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-line">
          <button
            type="button"
            onClick={handleClose}
            disabled={submitting}
            className="px-4 py-2.5 rounded-xl border border-line bg-surface text-xs font-medium text-ink-2 hover:bg-paper transition-colors cursor-pointer disabled:opacity-50"
          >
            Hủy bỏ
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={submitting}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-medium text-white bg-danger hover:bg-danger-deep transition-colors cursor-pointer shadow-xs disabled:opacity-50"
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

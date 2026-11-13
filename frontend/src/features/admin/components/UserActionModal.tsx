"use client";

import React, { useState } from "react";
import { Lock, Unlock, AlertTriangle, X, Loader2 } from "lucide-react";
import type { AdminUser } from "../types";

interface UserActionModalProps {
  user: AdminUser | null;
  action: "lock" | "unlock" | null;
  isOpen: boolean;
  onClose: () => void;
  onConfirm: (user: AdminUser, reason?: string) => Promise<void>;
}

export function UserActionModal({
  user,
  action,
  isOpen,
  onClose,
  onConfirm,
}: UserActionModalProps) {
  const [reason, setReason] = useState("Khóa tài khoản bởi Quản trị viên");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen || !user || !action) return null;

  const isLock = action === "lock";

  const handleConfirm = async () => {
    try {
      setSubmitting(true);
      setError(null);
      await onConfirm(user, isLock ? reason.trim() : undefined);
      onClose();
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError("Đã xảy ra lỗi khi thực hiện thao tác");
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-action-title"
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-night/60 backdrop-blur-xs animate-in fade-in duration-200 overflow-y-auto"
    >
      <div className="w-full max-w-dialog-confirm bg-surface rounded-3xl border border-line shadow-2xl overflow-hidden p-6 space-y-5 animate-in zoom-in-95 duration-200 my-auto">
        {/* Header */}
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3">
            <div
              className={`size-11 rounded-2xl flex items-center justify-center shrink-0 ${
                isLock
                  ? "bg-danger-soft text-danger-strong"
                  : "bg-success-soft text-success-strong"
              }`}
            >
              {isLock ? <Lock className="size-5" /> : <Unlock className="size-5" />}
            </div>
            <div>
              <h3
                id="modal-action-title"
                className="text-base font-medium text-ink"
              >
                {isLock ? "Khóa tài khoản người dùng" : "Mở khóa tài khoản"}
              </h3>
              <p className="text-xs text-muted">
                {isLock
                  ? "Tài khoản sẽ bị vô hiệu hóa truy cập hệ thống"
                  : "Khôi phục quyền truy cập bình thường cho tài khoản"}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="p-1.5 text-muted hover:text-ink-2 rounded-lg hover:bg-sunken transition-colors"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* User preview */}
        <div className="p-3.5 bg-paper rounded-2xl border border-line/80 flex items-center gap-3">
          <div className="size-10 rounded-xl overflow-hidden bg-line border border-line-strong flex items-center justify-center shrink-0">
            {user.avatarUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={user.avatarUrl}
                alt={user.fullName}
                className="size-full object-cover"
              />
            ) : (
              <span className="font-medium text-sm text-ink-2">
                {user.fullName ? user.fullName.charAt(0).toUpperCase() : "U"}
              </span>
            )}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-medium text-ink truncate">
              {user.fullName}
            </p>
            <p className="text-xs text-muted truncate">{user.email}</p>
          </div>
        </div>

        {/* Reason field (for Lock only) */}
        {isLock && (
          <div className="space-y-1.5">
            <label
              htmlFor="lock-reason"
              className="block text-xs font-medium text-ink-2"
            >
              Lý do khóa tài khoản <span className="text-danger-strong">*</span>
            </label>
            <textarea
              id="lock-reason"
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Nhập lý do cụ thể khóa tài khoản..."
              className="w-full text-xs p-3 rounded-xl border border-line-strong focus:outline-none focus:ring-2 focus:ring-danger focus:border-transparent resize-none text-ink"
            />
          </div>
        )}

        {/* Warning hint */}
        <div
          className={`p-3 rounded-xl text-xs flex items-start gap-2.5 ${
            isLock
              ? "bg-action-soft text-action-strong border border-action-line"
              : "bg-success-soft text-success-strong border border-success-line"
          }`}
        >
          <AlertTriangle
            className={`size-4 shrink-0 mt-0.5 ${
              isLock ? "text-action-strong" : "text-success-strong"
            }`}
          />
          <span>
            {isLock
              ? "Khi bị khóa, người dùng sẽ không thể đăng nhập hoặc thực hiện bất kỳ hoạt động nào trên nền tảng."
              : "Sau khi mở khóa, người dùng có thể tiếp tục sử dụng dịch vụ và đăng nhập bình thường."}
          </span>
        </div>

        {error && (
          <p className="text-xs text-danger-strong font-medium">{error}</p>
        )}

        {/* Actions */}
        <div className="flex items-center justify-end gap-2.5 pt-2">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="px-4 py-2.5 text-xs font-medium text-ink-2 hover:bg-sunken rounded-xl transition-colors"
          >
            Hủy bỏ
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={submitting || (isLock && !reason.trim())}
            className={`inline-flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-medium text-white transition-colors ${
              isLock
                ? "bg-danger hover:bg-danger-deep disabled:bg-danger-line"
                : "bg-success hover:bg-success-deep disabled:bg-success-line"
            }`}
          >
            {submitting ? (
              <Loader2 className="size-4 animate-spin" />
            ) : isLock ? (
              <Lock className="size-4" />
            ) : (
              <Unlock className="size-4" />
            )}
            <span>{isLock ? "Xác nhận khóa" : "Xác nhận mở khóa"}</span>
          </button>
        </div>
      </div>
    </div>
  );
}

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
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-900/60 backdrop-blur-xs animate-in fade-in duration-200 overflow-y-auto"
    >
      <div className="w-full max-w-md bg-white rounded-3xl border border-stone-200 shadow-2xl overflow-hidden p-6 space-y-5 animate-in zoom-in-95 duration-200 my-auto">
        {/* Header */}
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3">
            <div
              className={`size-11 rounded-2xl flex items-center justify-center shrink-0 ${
                isLock
                  ? "bg-rose-100 text-rose-700"
                  : "bg-emerald-100 text-emerald-700"
              }`}
            >
              {isLock ? <Lock className="size-5" /> : <Unlock className="size-5" />}
            </div>
            <div>
              <h3
                id="modal-action-title"
                className="text-base font-bold text-neutral-900"
              >
                {isLock ? "Khóa tài khoản người dùng" : "Mở khóa tài khoản"}
              </h3>
              <p className="text-xs text-neutral-500">
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
            className="p-1.5 text-neutral-400 hover:text-neutral-700 rounded-lg hover:bg-stone-100 transition-colors"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* User preview */}
        <div className="p-3.5 bg-stone-50 rounded-2xl border border-stone-200/80 flex items-center gap-3">
          <div className="size-10 rounded-xl overflow-hidden bg-stone-200 border border-stone-300 flex items-center justify-center shrink-0">
            {user.avatarUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={user.avatarUrl}
                alt={user.fullName}
                className="size-full object-cover"
              />
            ) : (
              <span className="font-bold text-sm text-neutral-600">
                {user.fullName ? user.fullName.charAt(0).toUpperCase() : "U"}
              </span>
            )}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-bold text-neutral-900 truncate">
              {user.fullName}
            </p>
            <p className="text-[11px] text-neutral-500 truncate">{user.email}</p>
          </div>
        </div>

        {/* Reason field (for Lock only) */}
        {isLock && (
          <div className="space-y-1.5">
            <label
              htmlFor="lock-reason"
              className="block text-xs font-bold text-neutral-700"
            >
              Lý do khóa tài khoản <span className="text-rose-500">*</span>
            </label>
            <textarea
              id="lock-reason"
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Nhập lý do cụ thể khóa tài khoản..."
              className="w-full text-xs p-3 rounded-xl border border-stone-300 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-transparent resize-none text-neutral-800"
            />
          </div>
        )}

        {/* Warning hint */}
        <div
          className={`p-3 rounded-xl text-xs flex items-start gap-2.5 ${
            isLock
              ? "bg-amber-50 text-amber-800 border border-amber-200"
              : "bg-emerald-50 text-emerald-800 border border-emerald-200"
          }`}
        >
          <AlertTriangle
            className={`size-4 shrink-0 mt-0.5 ${
              isLock ? "text-amber-600" : "text-emerald-600"
            }`}
          />
          <span>
            {isLock
              ? "Khi bị khóa, người dùng sẽ không thể đăng nhập hoặc thực hiện bất kỳ hoạt động nào trên nền tảng."
              : "Sau khi mở khóa, người dùng có thể tiếp tục sử dụng dịch vụ và đăng nhập bình thường."}
          </span>
        </div>

        {error && (
          <p className="text-xs text-rose-600 font-semibold">{error}</p>
        )}

        {/* Actions */}
        <div className="flex items-center justify-end gap-2.5 pt-2">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="px-4 py-2.5 text-xs font-bold text-neutral-700 hover:bg-stone-100 rounded-xl transition-colors"
          >
            Hủy bỏ
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={submitting || (isLock && !reason.trim())}
            className={`inline-flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold text-white transition-colors ${
              isLock
                ? "bg-rose-600 hover:bg-rose-700 disabled:bg-rose-300"
                : "bg-emerald-600 hover:bg-emerald-700 disabled:bg-emerald-300"
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

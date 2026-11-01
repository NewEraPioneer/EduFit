"use client";

import React, { useEffect, useCallback } from "react";
import Link from "next/link";
import { X, Lock, CheckCircle2, ArrowRight, UserPlus } from "lucide-react";

export interface LoginPromptModalProps {
  isOpen: boolean;
  onClose: () => void;
  tutorName?: string;
  returnUrl?: string;
  actionText?: string;
}

export function LoginPromptModal({
  isOpen,
  onClose,
  tutorName,
  returnUrl = "/tutors",
  actionText = "gửi yêu cầu học",
}: LoginPromptModalProps) {
  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
      }
    },
    [onClose]
  );

  useEffect(() => {
    if (isOpen) {
      document.addEventListener("keydown", handleKeyDown);
    }
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen, handleKeyDown]);

  if (!isOpen) return null;

  const loginHref = `/login?returnUrl=${encodeURIComponent(returnUrl)}`;
  const registerHref = `/register`;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="login-prompt-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs animate-in fade-in duration-150 overflow-y-auto"
      onClick={onClose}
    >
      <div
        className="relative w-full max-w-md bg-white rounded-3xl shadow-2xl border border-stone-200 overflow-hidden p-6 sm:p-7 space-y-5 animate-in zoom-in-95 duration-200 my-auto"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Close Button */}
        <button
          type="button"
          onClick={onClose}
          aria-label="Đóng"
          className="absolute top-4 right-4 p-1.5 rounded-full text-neutral-400 hover:text-neutral-700 hover:bg-stone-100 transition-colors cursor-pointer"
        >
          <X className="size-5" />
        </button>

        {/* Icon & Title */}
        <div className="flex flex-col items-center text-center space-y-3 pt-2">
          <div className="size-14 rounded-2xl bg-amber-100 text-amber-700 flex items-center justify-center shadow-xs">
            <Lock className="size-7 text-amber-600" />
          </div>
          <div>
            <h3 id="login-prompt-title" className="font-heading font-extrabold text-lg text-neutral-900">
              Đăng nhập để {actionText}
            </h3>
            <p className="text-xs text-neutral-500 mt-1 leading-relaxed max-w-xs">
              Vui lòng đăng nhập tài khoản Học sinh hoặc Phụ huynh để {actionText}{" "}
              {tutorName ? <strong className="text-neutral-800">{tutorName}</strong> : "gia sư"}.
            </p>
          </div>
        </div>

        {/* Value Proposition Pills */}
        <div className="p-3.5 bg-stone-50 rounded-2xl border border-stone-100 space-y-2 text-xs text-neutral-600">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="size-4 text-emerald-600 shrink-0" />
            <span>Kết nối trực tiếp gia sư THCS đã xác minh</span>
          </div>
          <div className="flex items-center gap-2">
            <CheckCircle2 className="size-4 text-emerald-600 shrink-0" />
            <span>Thiết lập lịch học và trao đổi mục tiêu 1-1</span>
          </div>
          <div className="flex items-center gap-2">
            <CheckCircle2 className="size-4 text-emerald-600 shrink-0" />
            <span>Xem tiến độ học tập và đánh giá minh bạch</span>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="space-y-2.5 pt-1">
          <Link
            href={loginHref}
            className="w-full inline-flex items-center justify-center gap-2 px-5 py-3 rounded-2xl font-bold text-xs text-white bg-amber-600 hover:bg-amber-700 shadow-md shadow-amber-600/20 active:scale-[0.99] transition-all cursor-pointer"
          >
            <span>Đăng nhập ngay</span>
            <ArrowRight className="size-4" />
          </Link>

          <Link
            href={registerHref}
            className="w-full inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-2xl font-semibold text-xs text-neutral-700 bg-stone-100 hover:bg-stone-200 active:scale-[0.99] transition-all cursor-pointer"
          >
            <UserPlus className="size-4 text-neutral-500" />
            <span>Tạo tài khoản mới miễn phí</span>
          </Link>

          <button
            type="button"
            onClick={onClose}
            className="w-full py-1 text-center text-xs font-medium text-neutral-400 hover:text-neutral-600 transition-colors cursor-pointer"
          >
            Để sau
          </button>
        </div>
      </div>
    </div>
  );
}

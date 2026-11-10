"use client";

import React, { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { CheckCircle2, Loader2, Sparkles, UserCheck } from "lucide-react";
import { RoleSelector, type RoleType } from "./RoleSelector";
import { useAuth } from "@/context/AuthContext";
import { buildOnboardingPath } from "../utils/authRedirect";
import { useToast } from "@/shared/ui/Toast";

interface OAuthRoleSelectionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onboardingData: {
    onboardingToken: string;
    email?: string;
    fullName?: string;
    avatarUrl?: string;
  } | null;
}

export function OAuthRoleSelectionModal({
  isOpen,
  onClose,
  onboardingData,
}: OAuthRoleSelectionModalProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { completeOAuthRegistration } = useAuth();
  const { showToast } = useToast();

  const [selectedRole, setSelectedRole] = useState<RoleType>("STUDENT");
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  if (!isOpen || !onboardingData) {
    return null;
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSubmitting(true);

    try {
      if (!completeOAuthRegistration) {
        throw new Error("Dịch vụ đăng ký OAuth chưa sẵn sàng");
      }
      const user = await completeOAuthRegistration({
        onboardingToken: onboardingData.onboardingToken,
        role: selectedRole,
      });

      showToast(`Chào mừng ${user.fullName} gia nhập EduFit!`, "success");
      onClose();

      // Điều hướng người dùng vào màn hình Onboarding theo vai trò vừa đăng ký, giữ returnUrl an toàn
      const role = selectedRole === "TUTOR" || selectedRole === "PARENT" ? selectedRole : "STUDENT";
      router.push(buildOnboardingPath(role, searchParams.get("returnUrl")));
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Đã có lỗi xảy ra. Vui lòng thử lại.";
      setErrorMsg(msg);
      showToast(msg, "error");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-night/60 backdrop-blur-xs animate-in fade-in duration-200 overflow-y-auto">
      <div className="relative w-full max-w-dialog-confirm bg-surface rounded-3xl p-6 sm:p-7 shadow-2xl border border-line space-y-5 animate-in zoom-in-95 duration-200 my-auto">
        {/* Header */}
        <div className="text-center space-y-2">
          <div className="inline-flex items-center justify-center size-12 rounded-2xl bg-action-soft border border-action-line text-action-strong mb-1">
            <Sparkles className="size-6" />
          </div>
          <h3 className="font-heading text-xl font-bold text-ink">
            Chọn vai trò tài khoản
          </h3>
          <p className="text-xs text-muted">
            Chào mừng bạn đến với EduFit. Chọn vai trò của bạn để bắt đầu.
          </p>
        </div>

        {/* Thông tin tài khoản Google đã liên kết */}
        <div className="flex items-center gap-3 p-3 bg-paper rounded-2xl border border-line/80">
          {onboardingData.avatarUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={onboardingData.avatarUrl}
              alt={onboardingData.fullName || "Google User"}
              className="size-10 rounded-full object-cover border border-line shadow-2xs"
            />
          ) : (
            <div className="size-10 rounded-full bg-action text-on-action font-medium text-sm flex items-center justify-center">
              {onboardingData.fullName?.charAt(0).toUpperCase() || "G"}
            </div>
          )}
          <div className="min-w-0 flex-1">
            <p className="text-xs font-medium text-ink truncate">
              {onboardingData.fullName || "Người dùng"}
            </p>
            <p className="text-xs text-muted truncate">{onboardingData.email}</p>
          </div>
          <span className="text-xs font-medium text-success-strong bg-success-soft px-2 py-0.5 rounded-full border border-success-line">
            Google
          </span>
        </div>

        {errorMsg && (
          <div className="p-3 bg-danger-soft border border-danger-line text-danger-strong text-xs rounded-xl">
            {errorMsg}
          </div>
        )}

        {/* Form chọn vai trò */}
        <form onSubmit={handleSubmit} className="space-y-5">
          <RoleSelector selectedRole={selectedRole} onChange={setSelectedRole} />

          <div className="pt-2 flex items-center justify-end gap-2.5">
            <button
              type="button"
              disabled={submitting}
              onClick={onClose}
              className="px-4 py-2.5 text-xs font-medium text-ink-2 hover:text-ink hover:bg-sunken rounded-xl transition-colors cursor-pointer"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-action hover:bg-action-deep active:scale-[0.99] text-on-action font-medium text-xs rounded-xl shadow-xs transition-all disabled:opacity-50 cursor-pointer"
            >
              {submitting ? (
                <Loader2 className="size-3.5 animate-spin" />
              ) : (
                <UserCheck className="size-3.5" />
              )}
              <span>{submitting ? "Đang xử lý..." : "Hoàn tất đăng ký"}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

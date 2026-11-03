"use client";

import React, { useState } from "react";
import { useRouter } from "next/navigation";
import { CheckCircle2, Loader2, Sparkles, UserCheck } from "lucide-react";
import { RoleSelector, type RoleType } from "./RoleSelector";
import { useAuth } from "@/context/AuthContext";
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

      // Điều hướng người dùng vào màn hình Onboarding theo vai trò vừa đăng ký
      if (selectedRole === "TUTOR") {
        router.push("/onboarding/tutor");
      } else if (selectedRole === "PARENT") {
        router.push("/onboarding/parent");
      } else {
        router.push("/onboarding/student");
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Đã có lỗi xảy ra. Vui lòng thử lại.";
      setErrorMsg(msg);
      showToast(msg, "error");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-neutral-900/60 backdrop-blur-xs animate-in fade-in duration-200 overflow-y-auto">
      <div className="relative w-full max-w-md bg-white rounded-3xl p-6 sm:p-7 shadow-2xl border border-stone-100 space-y-5 animate-in zoom-in-95 duration-200 my-auto">
        {/* Header */}
        <div className="text-center space-y-2">
          <div className="inline-flex items-center justify-center size-12 rounded-2xl bg-amber-50 border border-amber-100 text-amber-600 mb-1">
            <Sparkles className="size-6" />
          </div>
          <h3 className="font-heading text-xl font-bold text-neutral-900">
            Chọn vai trò tài khoản
          </h3>
          <p className="text-xs text-neutral-500">
            Chào mừng bạn đến với EduFit! Vui lòng chọn vai trò bạn muốn tham gia để hệ thống tối ưu giao diện phù hợp.
          </p>
        </div>

        {/* Thông tin tài khoản Google đã liên kết */}
        <div className="flex items-center gap-3 p-3 bg-stone-50 rounded-2xl border border-stone-200/80">
          {onboardingData.avatarUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={onboardingData.avatarUrl}
              alt={onboardingData.fullName || "Google User"}
              className="size-10 rounded-full object-cover border border-stone-200 shadow-2xs"
            />
          ) : (
            <div className="size-10 rounded-full bg-amber-500 text-white font-bold text-sm flex items-center justify-center">
              {onboardingData.fullName?.charAt(0).toUpperCase() || "G"}
            </div>
          )}
          <div className="min-w-0 flex-1">
            <p className="text-xs font-bold text-neutral-900 truncate">
              {onboardingData.fullName || "Người dùng"}
            </p>
            <p className="text-[11px] text-neutral-500 truncate">{onboardingData.email}</p>
          </div>
          <span className="text-[10px] font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-200">
            Google
          </span>
        </div>

        {errorMsg && (
          <div className="p-3 bg-rose-50 border border-rose-200 text-rose-700 text-xs rounded-xl">
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
              className="px-4 py-2.5 text-xs font-semibold text-neutral-600 hover:text-neutral-800 hover:bg-stone-100 rounded-xl transition-colors cursor-pointer"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-amber-500 hover:bg-amber-600 active:scale-[0.99] text-white font-bold text-xs rounded-xl shadow-xs transition-all disabled:opacity-50 cursor-pointer"
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
